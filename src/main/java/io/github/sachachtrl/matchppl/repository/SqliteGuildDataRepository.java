package io.github.sachachtrl.matchppl.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class SqliteGuildDataRepository implements GuildDataRepository {
    private static final List<String> DELETE_SQL = List.of(
            "DELETE FROM message_activity WHERE guild_id = ?",
            "DELETE FROM message_total WHERE guild_id = ?",
            "DELETE FROM profile_tag WHERE guild_id = ?",
            "DELETE FROM profile WHERE guild_id = ?",
            "DELETE FROM guild_admin_role WHERE guild_id = ?",
            "DELETE FROM guild_settings WHERE guild_id = ?",
            "DELETE FROM tag_role WHERE tag_id IN (SELECT id FROM tag WHERE guild_id = ?)",
            "DELETE FROM tag WHERE guild_id = ?",
            "DELETE FROM room WHERE guild_id = ?",
            "DELETE FROM proposal WHERE guild_id = ?",
            "DELETE FROM member_block WHERE guild_id = ?");

    private final Connection connection;

    public SqliteGuildDataRepository(Database database) {
        this.connection = database.getConnection();
    }

    @Override
    public void deleteGuild(long guildId) {
        synchronized (this.connection) {
            try {
                this.connection.setAutoCommit(false);
                try {
                    for (String sql : DELETE_SQL) {
                        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
                            statement.setLong(1, guildId);
                            statement.executeUpdate();
                        }
                    }
                    this.connection.commit();
                } catch (SQLException e) {
                    this.connection.rollback();
                    throw e;
                } finally {
                    this.connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to delete guild data", e);
            }
        }
    }
}
