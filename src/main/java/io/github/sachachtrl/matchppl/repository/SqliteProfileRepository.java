package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.Profile;

import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqliteProfileRepository implements ProfileRepository {
    private static final Logger log = LoggerFactory.getLogger(SqliteProfileRepository.class);

    private static final String CHOSEN_KIND = "CHOSEN";
    private static final String EXCLUDED_KIND = "EXCLUDED";
    private static final String REQUIRED_KIND = "REQUIRED";

    private static final String FIND_SQL = """
            SELECT searching, presentation, anonymous, min_compatibility, require_photo,
                   min_account_age_days, min_member_days, min_messages, activity_period
            FROM profile
            WHERE guild_id = ? AND user_id = ?
            """;
    private static final String FIND_TAGS_SQL = """
            SELECT tag_id, kind FROM profile_tag WHERE guild_id = ? AND user_id = ?
            """;
    private static final String SAVE_SQL = """
            INSERT INTO profile (guild_id, user_id, searching, presentation, anonymous, min_compatibility,
                                 require_photo, min_account_age_days, min_member_days, min_messages, activity_period)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (guild_id, user_id) DO UPDATE SET
                searching = excluded.searching,
                presentation = excluded.presentation,
                anonymous = excluded.anonymous,
                min_compatibility = excluded.min_compatibility,
                require_photo = excluded.require_photo,
                min_account_age_days = excluded.min_account_age_days,
                min_member_days = excluded.min_member_days,
                min_messages = excluded.min_messages,
                activity_period = excluded.activity_period
            """;
    private static final String DELETE_TAGS_SQL = """
            DELETE FROM profile_tag WHERE guild_id = ? AND user_id = ?
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
                    MemberConditions partnerConditions = new MemberConditions(
                            getNullableInt(result, 6),
                            getNullableInt(result, 7),
                            getNullableInt(result, 8),
                            period == null ? null : ActivityPeriod.valueOf(period));
                    return new Profile(
                            guildId,
                            userId,
                            result.getBoolean(1),
                            this.decryptPresentation(result.getString(2), guildId, userId),
                            result.getBoolean(3),
                            chosen,
                            excluded,
                            required,
                            getNullableInt(result, 4),
                            result.getBoolean(5),
                            partnerConditions);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to read profile", e);
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
            MemberConditions conditions = profile.partnerConditions();
            statement.setLong(1, profile.guildId());
            statement.setLong(2, profile.userId());
            statement.setBoolean(3, profile.searching());
            statement.setString(4, profile.presentation() == null
                    ? null
                    : this.cipher.encrypt(profile.presentation(), context(profile.guildId(), profile.userId())));
            statement.setBoolean(5, profile.anonymous());
            setNullableInt(statement, 6, profile.minCompatibility());
            statement.setBoolean(7, profile.requirePhoto());
            setNullableInt(statement, 8, conditions.minAccountAgeDays());
            setNullableInt(statement, 9, conditions.minMemberDays());
            setNullableInt(statement, 10, conditions.minMessages());
            statement.setString(11, conditions.activityPeriod() == null ? null : conditions.activityPeriod().name());
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
            addTags(insert, profile, profile.requiredTagIds(), REQUIRED_KIND);
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

    private static String context(long guildId, long userId) {
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
