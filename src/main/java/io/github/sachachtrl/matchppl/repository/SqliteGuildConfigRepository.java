package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.GuildSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public class SqliteGuildConfigRepository implements GuildConfigRepository {
    private static final String ADD_ADMIN_ROLE_SQL = """
            INSERT OR IGNORE INTO guild_admin_role (guild_id, role_id)
            VALUES (?, ?)
            """;
    private static final String REMOVE_ADMIN_ROLE_SQL = """
            DELETE FROM guild_admin_role
            WHERE guild_id = ? AND role_id = ?
            """;
    private static final String FIND_ADMIN_ROLES_SQL = """
            SELECT role_id FROM guild_admin_role WHERE guild_id = ?
            """;
    private static final String FIND_SETTINGS_SQL = """
            SELECT response_delay_minutes, matching_interval_minutes, rooms_enabled, room_lifetime_minutes,
                   room_prefix, category_id, log_channel_id
            FROM guild_settings
            WHERE guild_id = ?
            """;
    private static final String SAVE_SETTINGS_SQL = """
            INSERT INTO guild_settings (guild_id, response_delay_minutes, matching_interval_minutes, rooms_enabled,
                                        room_lifetime_minutes, room_prefix, category_id, log_channel_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (guild_id) DO UPDATE SET
                response_delay_minutes = excluded.response_delay_minutes,
                matching_interval_minutes = excluded.matching_interval_minutes,
                rooms_enabled = excluded.rooms_enabled,
                room_lifetime_minutes = excluded.room_lifetime_minutes,
                room_prefix = excluded.room_prefix,
                category_id = excluded.category_id,
                log_channel_id = excluded.log_channel_id
            """;

    private final Connection connection;

    public SqliteGuildConfigRepository(Database database) {
        this.connection = database.getConnection();
    }

    @Override
    public GuildSettings findSettings(long guildId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_SETTINGS_SQL)) {
                statement.setLong(1, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return GuildSettings.defaults(guildId);
                    }
                    return new GuildSettings(
                            guildId,
                            Duration.ofMinutes(result.getLong(1)),
                            Duration.ofMinutes(result.getLong(2)),
                            result.getBoolean(3),
                            Duration.ofMinutes(result.getLong(4)),
                            result.getString(5),
                            getNullableLong(result, 6),
                            getNullableLong(result, 7));
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to read guild settings", e);
            }
        }
    }

    @Override
    public void saveSettings(GuildSettings settings) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(SAVE_SETTINGS_SQL)) {
                statement.setLong(1, settings.guildId());
                statement.setLong(2, settings.responseDelay().toMinutes());
                statement.setLong(3, settings.matchingInterval().toMinutes());
                statement.setBoolean(4, settings.roomsEnabled());
                statement.setLong(5, settings.roomLifetime().toMinutes());
                statement.setString(6, settings.roomPrefix());
                setNullableLong(statement, 7, settings.categoryId());
                setNullableLong(statement, 8, settings.logChannelId());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to save guild settings", e);
            }
        }
    }

    private static Long getNullableLong(ResultSet result, int column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value);
        }
    }

    @Override
    public boolean addAdminRole(long guildId, long roleId) {
        return this.executeRoleUpdate(ADD_ADMIN_ROLE_SQL, guildId, roleId, "Failed to add admin role");
    }

    @Override
    public boolean removeAdminRole(long guildId, long roleId) {
        return this.executeRoleUpdate(REMOVE_ADMIN_ROLE_SQL, guildId, roleId, "Failed to remove admin role");
    }

    @Override
    public Set<Long> findAdminRoles(long guildId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_ADMIN_ROLES_SQL)) {
                statement.setLong(1, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    Set<Long> roleIds = new HashSet<>();
                    while (result.next()) {
                        roleIds.add(result.getLong(1));
                    }
                    return roleIds;
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to read admin roles", e);
            }
        }
    }

    private boolean executeRoleUpdate(String sql, long guildId, long roleId, String errorMessage) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
                statement.setLong(1, guildId);
                statement.setLong(2, roleId);
                return statement.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RepositoryException(errorMessage, e);
            }
        }
    }
}
