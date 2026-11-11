package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.Tag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
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
    private static final String FIND_SQL = """
            SELECT name, emoji, min_account_age_days, min_member_days, min_messages, activity_period
            FROM tag
            WHERE id = ? AND guild_id = ?
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
    private static final String DELETE_ROLES_SQL = """
            DELETE FROM tag_role WHERE tag_id = ?
            """;
    private static final String INSERT_ROLE_SQL = """
            INSERT INTO tag_role (tag_id, role_id, kind) VALUES (?, ?, ?)
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
                    return new Tag(result.getLong(1), guildId, name, emoji, null, null, null, null, Set.of(), Set.of());
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
                    if (!result.next()) {
                        return Optional.empty();
                    }
                    String period = result.getString(6);
                    Set<Long> required = new HashSet<>();
                    Set<Long> forbidden = new HashSet<>();
                    this.loadRoles(tagId, required, forbidden);
                    return Optional.of(new Tag(
                            tagId,
                            guildId,
                            result.getString(1),
                            result.getString(2),
                            getNullableInt(result, 3),
                            getNullableInt(result, 4),
                            getNullableInt(result, 5),
                            period == null ? null : ActivityPeriod.valueOf(period),
                            required,
                            forbidden));
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find tag", e);
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
            setNullableInt(statement, 1, tag.minAccountAgeDays());
            setNullableInt(statement, 2, tag.minMemberDays());
            setNullableInt(statement, 3, tag.minMessages());
            statement.setString(4, tag.activityPeriod() == null ? null : tag.activityPeriod().name());
            statement.setLong(5, tag.id());
            statement.setLong(6, tag.guildId());
            statement.executeUpdate();
        }
    }

    private void replaceRoles(Tag tag) throws SQLException {
        try (PreparedStatement delete = this.connection.prepareStatement(DELETE_ROLES_SQL)) {
            delete.setLong(1, tag.id());
            delete.executeUpdate();
        }
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
