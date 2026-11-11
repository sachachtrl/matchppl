package io.github.sachachtrl.matchppl.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private final Connection connection;

    public SqliteGuildConfigRepository(Database database) {
        this.connection = database.getConnection();
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
