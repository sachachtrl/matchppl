package io.github.sachachtrl.matchppl.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SqliteGuildConfigRepository implements GuildConfigRepository {
    private static final String ADD_ADMIN_ROLE_SQL = """
            INSERT OR IGNORE INTO guild_admin_role (guild_id, role_id)
            VALUES (?, ?)
            """;
    private static final String REMOVE_ADMIN_ROLE_SQL = """
            DELETE FROM guild_admin_role
            WHERE guild_id = ? AND role_id = ?
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
