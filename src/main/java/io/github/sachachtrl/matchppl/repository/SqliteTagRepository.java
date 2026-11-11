package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.Tag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class SqliteTagRepository implements TagRepository {
    private static final String REQUIRED_KIND = "REQUIRED";
    private static final String FORBIDDEN_KIND = "FORBIDDEN";

    private static final String CREATE_SQL = """
            INSERT INTO tag (guild_id, name, name_key, emoji)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """;
    private static final String TAG_COLUMNS = """
            id, name, emoji, min_account_age_days, min_member_days, min_messages, activity_period
            """;
    private static final String FIND_SQL = "SELECT " + TAG_COLUMNS + """
            FROM tag
            WHERE id = ? AND guild_id = ?
            """;
    private static final String FIND_BY_GUILD_SQL = "SELECT " + TAG_COLUMNS + """
            FROM tag
            WHERE guild_id = ?
            ORDER BY name_key
            """;
    private static final String FIND_ROLES_SQL = """
            SELECT role_id, kind FROM tag_role WHERE tag_id = ?
            """;
    private static final String EXISTS_SQL = """
            SELECT 1 FROM tag WHERE guild_id = ? AND name_key = ?
            """;
    private static final String UPDATE_SQL = """
            UPDATE tag
            SET min_account_age_days = ?, min_member_days = ?, min_messages = ?, activity_period = ?
            WHERE id = ? AND guild_id = ?
            """;
    private static final String DELETE_SQL = """
            DELETE FROM tag WHERE id = ? AND guild_id = ?
            """;
    private static final String DELETE_ROLES_SQL = """
            DELETE FROM tag_role WHERE tag_id = ?
            """;
    private static final String INSERT_ROLE_SQL = """
            INSERT INTO tag_role (tag_id, role_id, kind) VALUES (?, ?, ?)
            """;
    private static final String REMOVE_FROM_PROFILES_SQL = """
            DELETE FROM profile_tag WHERE guild_id = ? AND tag_id = ?
            """;

    private final Connection connection;

    public SqliteTagRepository(Database database) {
        this.connection = database.getConnection();
    }

    @Override
    public Tag create(long guildId, String name, String emoji) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(CREATE_SQL)) {
                statement.setLong(1, guildId);
                statement.setString(2, name);
                statement.setString(3, nameKey(name));
                statement.setString(4, emoji);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return new Tag(result.getLong(1), guildId, name, emoji, MemberConditions.NONE, Set.of(), Set.of());
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to create tag", e);
            }
        }
    }

    @Override
    public Optional<Tag> findById(long guildId, long tagId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_SQL)) {
                statement.setLong(1, tagId);
                statement.setLong(2, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(this.readTag(result, guildId)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find tag", e);
            }
        }
    }

    @Override
    public List<Tag> findByGuild(long guildId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_BY_GUILD_SQL)) {
                statement.setLong(1, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    List<Tag> tags = new ArrayList<>();
                    while (result.next()) {
                        tags.add(this.readTag(result, guildId));
                    }
                    return tags;
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to list tags", e);
            }
        }
    }

    @Override
    public boolean existsByName(long guildId, String name) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(EXISTS_SQL)) {
                statement.setLong(1, guildId);
                statement.setString(2, nameKey(name));
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to check tag name", e);
            }
        }
    }

    @Override
    public void update(Tag tag) {
        synchronized (this.connection) {
            try {
                this.connection.setAutoCommit(false);
                try {
                    this.updateConditions(tag);
                    this.replaceRoles(tag);
                    this.connection.commit();
                } catch (SQLException e) {
                    this.connection.rollback();
                    throw e;
                } finally {
                    this.connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to update tag", e);
            }
        }
    }

    @Override
    public boolean delete(long guildId, long tagId) {
        synchronized (this.connection) {
            try {
                this.connection.setAutoCommit(false);
                try {
                    boolean deleted = this.deleteTagRow(guildId, tagId);
                    if (deleted) {
                        this.deleteRoles(tagId);
                        this.removeFromProfiles(guildId, tagId);
                    }
                    this.connection.commit();
                    return deleted;
                } catch (SQLException e) {
                    this.connection.rollback();
                    throw e;
                } finally {
                    this.connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to delete tag", e);
            }
        }
    }

    private Tag readTag(ResultSet result, long guildId) throws SQLException {
        long tagId = result.getLong(1);
        String period = result.getString(7);
        Set<Long> required = new HashSet<>();
        Set<Long> forbidden = new HashSet<>();
        this.loadRoles(tagId, required, forbidden);
        MemberConditions conditions = new MemberConditions(
                getNullableInt(result, 4),
                getNullableInt(result, 5),
                getNullableInt(result, 6),
                period == null ? null : ActivityPeriod.valueOf(period));
        return new Tag(tagId, guildId, result.getString(2), result.getString(3), conditions, required, forbidden);
    }

    private void loadRoles(long tagId, Set<Long> required, Set<Long> forbidden) throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(FIND_ROLES_SQL)) {
            statement.setLong(1, tagId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long roleId = result.getLong(1);
                    if (REQUIRED_KIND.equals(result.getString(2))) {
                        required.add(roleId);
                    } else {
                        forbidden.add(roleId);
                    }
                }
            }
        }
    }

    private void updateConditions(Tag tag) throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(UPDATE_SQL)) {
            MemberConditions conditions = tag.conditions();
            setNullableInt(statement, 1, conditions.minAccountAgeDays());
            setNullableInt(statement, 2, conditions.minMemberDays());
            setNullableInt(statement, 3, conditions.minMessages());
            statement.setString(4, conditions.activityPeriod() == null ? null : conditions.activityPeriod().name());
            statement.setLong(5, tag.id());
            statement.setLong(6, tag.guildId());
            statement.executeUpdate();
        }
    }

    private void replaceRoles(Tag tag) throws SQLException {
        this.deleteRoles(tag.id());
        try (PreparedStatement insert = this.connection.prepareStatement(INSERT_ROLE_SQL)) {
            for (long roleId : tag.requiredRoleIds()) {
                addRole(insert, tag.id(), roleId, REQUIRED_KIND);
            }
            for (long roleId : tag.forbiddenRoleIds()) {
                addRole(insert, tag.id(), roleId, FORBIDDEN_KIND);
            }
            insert.executeBatch();
        }
    }

    private boolean deleteTagRow(long guildId, long tagId) throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(DELETE_SQL)) {
            statement.setLong(1, tagId);
            statement.setLong(2, guildId);
            return statement.executeUpdate() > 0;
        }
    }

    private void deleteRoles(long tagId) throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(DELETE_ROLES_SQL)) {
            statement.setLong(1, tagId);
            statement.executeUpdate();
        }
    }

    private void removeFromProfiles(long guildId, long tagId) throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(REMOVE_FROM_PROFILES_SQL)) {
            statement.setLong(1, guildId);
            statement.setLong(2, tagId);
            statement.executeUpdate();
        }
    }

    private static void addRole(PreparedStatement insert, long tagId, long roleId, String kind) throws SQLException {
        insert.setLong(1, tagId);
        insert.setLong(2, roleId);
        insert.setString(3, kind);
        insert.addBatch();
    }

    private static String nameKey(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
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
