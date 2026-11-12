package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.PartnerPreferences;
import io.github.sachachtrl.matchppl.model.Profile;

import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqliteProfileRepository implements ProfileRepository {
    private static final Logger log = LoggerFactory.getLogger(SqliteProfileRepository.class);

    private static final String CHOSEN_KIND = "CHOSEN";
    private static final String EXCLUDED_KIND = "EXCLUDED";
    private static final String REQUIRED_KIND = "REQUIRED";

    private static final String FIND_SQL = """
            SELECT searching, presentation, anonymous, min_compatibility, exclude_anonymous,
                   min_account_age_days, min_member_days, min_messages, activity_period,
                   exclude_default_avatars, min_common_tags, waiting_since, dm_unreachable, allow_rooms
            FROM profile
            WHERE guild_id = ? AND user_id = ?
            """;
    private static final String FIND_TAGS_SQL = """
            SELECT tag_id, kind FROM profile_tag WHERE guild_id = ? AND user_id = ?
            """;
    private static final String SAVE_SQL = """
            INSERT INTO profile (guild_id, user_id, searching, presentation, anonymous, min_compatibility,
                                 exclude_anonymous, min_account_age_days, min_member_days, min_messages,
                                 activity_period, exclude_default_avatars, min_common_tags, waiting_since,
                                 dm_unreachable, allow_rooms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (guild_id, user_id) DO UPDATE SET
                searching = excluded.searching,
                presentation = excluded.presentation,
                anonymous = excluded.anonymous,
                min_compatibility = excluded.min_compatibility,
                exclude_anonymous = excluded.exclude_anonymous,
                min_account_age_days = excluded.min_account_age_days,
                min_member_days = excluded.min_member_days,
                min_messages = excluded.min_messages,
                activity_period = excluded.activity_period,
                exclude_default_avatars = excluded.exclude_default_avatars,
                min_common_tags = excluded.min_common_tags,
                waiting_since = excluded.waiting_since,
                dm_unreachable = excluded.dm_unreachable,
                allow_rooms = excluded.allow_rooms
            """;
    private static final String DELETE_TAGS_SQL = """
            DELETE FROM profile_tag WHERE guild_id = ? AND user_id = ?
            """;
    private static final String FIND_SEARCHING_SQL = """
            SELECT user_id FROM profile WHERE guild_id = ? AND searching = 1
            """;
    private static final String COUNT_SEARCHING_SQL = """
            SELECT COUNT(*) FROM profile WHERE guild_id = ? AND searching = 1
            """;
    private static final String STOP_ALL_SQL = """
            UPDATE profile SET searching = 0, waiting_since = NULL WHERE guild_id = ? AND searching = 1
            """;
    private static final String DELETE_SQL = """
            DELETE FROM profile WHERE guild_id = ? AND user_id = ?
            """;
    private static final String INSERT_TAG_SQL = """
            INSERT INTO profile_tag (guild_id, user_id, tag_id, kind) VALUES (?, ?, ?, ?)
            """;

    private final Connection connection;
    private final TextCipher cipher;

    public SqliteProfileRepository(Database database, TextCipher cipher) {
        this.connection = database.getConnection();
        this.cipher = cipher;
    }

    @Override
    public Profile find(long guildId, long userId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, userId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Profile.empty(guildId, userId);
                    }
                    Set<Long> chosen = new HashSet<>();
                    Set<Long> excluded = new HashSet<>();
                    Set<Long> required = new HashSet<>();
                    this.loadTags(guildId, userId, chosen, excluded, required);
                    String period = result.getString(9);
                    String waitingSince = result.getString(12);
                    MemberConditions conditions = new MemberConditions(
                            getNullableInt(result, 6),
                            getNullableInt(result, 7),
                            getNullableInt(result, 8),
                            period == null ? null : ActivityPeriod.valueOf(period));
                    PartnerPreferences preferences = new PartnerPreferences(
                            required,
                            getNullableInt(result, 4),
                            result.getInt(11),
                            result.getBoolean(5),
                            result.getBoolean(10),
                            conditions);
                    return new Profile(
                            guildId,
                            userId,
                            result.getBoolean(1),
                            waitingSince == null ? null : Instant.parse(waitingSince),
                            result.getBoolean(13),
                            this.decryptPresentation(result.getString(2), guildId, userId),
                            result.getBoolean(3),
                            result.getBoolean(14),
                            chosen,
                            excluded,
                            preferences);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to read profile", e);
            }
        }
    }

    @Override
    public List<Profile> findSearching(long guildId) {
        synchronized (this.connection) {
            List<Long> userIds = new ArrayList<>();
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_SEARCHING_SQL)) {
                statement.setLong(1, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        userIds.add(result.getLong(1));
                    }
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to list searching profiles", e);
            }
            return userIds.stream().map(userId -> this.find(guildId, userId)).toList();
        }
    }

    @Override
    public int countSearching(long guildId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(COUNT_SEARCHING_SQL)) {
                statement.setLong(1, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to count searching profiles", e);
            }
        }
    }

    @Override
    public int stopAllSearches(long guildId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(STOP_ALL_SQL)) {
                statement.setLong(1, guildId);
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to stop all searches", e);
            }
        }
    }

    @Override
    public void delete(long guildId, long userId) {
        synchronized (this.connection) {
            try {
                this.connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = this.connection.prepareStatement(DELETE_TAGS_SQL)) {
                        statement.setLong(1, guildId);
                        statement.setLong(2, userId);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = this.connection.prepareStatement(DELETE_SQL)) {
                        statement.setLong(1, guildId);
                        statement.setLong(2, userId);
                        statement.executeUpdate();
                    }
                    this.connection.commit();
                } catch (SQLException e) {
                    this.connection.rollback();
                    throw e;
                } finally {
                    this.connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to delete profile", e);
            }
        }
    }

    @Override
    public void save(Profile profile) {
        synchronized (this.connection) {
            try {
                this.connection.setAutoCommit(false);
                try {
                    this.saveRow(profile);
                    this.replaceTags(profile);
                    this.connection.commit();
                } catch (SQLException e) {
                    this.connection.rollback();
                    throw e;
                } finally {
                    this.connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to save profile", e);
            }
        }
    }

    private void saveRow(Profile profile) throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(SAVE_SQL)) {
            PartnerPreferences preferences = profile.preferences();
            MemberConditions conditions = preferences.conditions();
            statement.setLong(1, profile.guildId());
            statement.setLong(2, profile.userId());
            statement.setBoolean(3, profile.searching());
            statement.setString(4, profile.presentation() == null
                    ? null
                    : this.cipher.encrypt(profile.presentation(), context(profile.guildId(), profile.userId())));
            statement.setBoolean(5, profile.anonymous());
            setNullableInt(statement, 6, preferences.minCompatibility());
            statement.setBoolean(7, preferences.excludeAnonymous());
            setNullableInt(statement, 8, conditions.minAccountAgeDays());
            setNullableInt(statement, 9, conditions.minMemberDays());
            setNullableInt(statement, 10, conditions.minMessages());
            statement.setString(11, conditions.activityPeriod() == null ? null : conditions.activityPeriod().name());
            statement.setBoolean(12, preferences.excludeDefaultAvatars());
            statement.setInt(13, preferences.minCommonTags());
            statement.setString(14, profile.waitingSince() == null ? null : profile.waitingSince().toString());
            statement.setBoolean(15, profile.dmUnreachable());
            statement.setBoolean(16, profile.allowRooms());
            statement.executeUpdate();
        }
    }

    private void replaceTags(Profile profile) throws SQLException {
        try (PreparedStatement delete = this.connection.prepareStatement(DELETE_TAGS_SQL)) {
            delete.setLong(1, profile.guildId());
            delete.setLong(2, profile.userId());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = this.connection.prepareStatement(INSERT_TAG_SQL)) {
            addTags(insert, profile, profile.tagIds(), CHOSEN_KIND);
            addTags(insert, profile, profile.excludedTagIds(), EXCLUDED_KIND);
            addTags(insert, profile, profile.preferences().requiredTagIds(), REQUIRED_KIND);
            insert.executeBatch();
        }
    }

    private void loadTags(long guildId, long userId, Set<Long> chosen, Set<Long> excluded, Set<Long> required)
            throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(FIND_TAGS_SQL)) {
            statement.setLong(1, guildId);
            statement.setLong(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long tagId = result.getLong(1);
                    switch (result.getString(2)) {
                        case CHOSEN_KIND -> chosen.add(tagId);
                        case EXCLUDED_KIND -> excluded.add(tagId);
                        case REQUIRED_KIND -> required.add(tagId);
                        default -> {
                        }
                    }
                }
            }
        }
    }

    private String decryptPresentation(String encrypted, long guildId, long userId) {
        if (encrypted == null) {
            return null;
        }
        try {
            return this.cipher.decrypt(encrypted, context(guildId, userId));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("Cannot decrypt presentation: guild={}, user={}", guildId, userId);
            return null;
        }
    }

    private static void addTags(PreparedStatement insert, Profile profile, Set<Long> tagIds, String kind)
            throws SQLException {
        for (long tagId : tagIds) {
            insert.setLong(1, profile.guildId());
            insert.setLong(2, profile.userId());
            insert.setLong(3, tagId);
            insert.setString(4, kind);
            insert.addBatch();
        }
    }

    static String context(long guildId, long userId) {
        return "profile:" + guildId + ":" + userId;
    }

    private static Integer getNullableInt(ResultSet result, int column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }
}
