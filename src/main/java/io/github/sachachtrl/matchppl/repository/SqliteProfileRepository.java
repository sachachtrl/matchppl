package io.github.sachachtrl.matchppl.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SqliteProfileRepository implements ProfileRepository {
    private static final String IS_SEARCHING_SQL = """
            SELECT searching FROM profile WHERE guild_id = ? AND user_id = ?
            """;
    private static final String SET_SEARCHING_SQL = """
            INSERT INTO profile (guild_id, user_id, searching)
            VALUES (?, ?, ?)
            ON CONFLICT (guild_id, user_id) DO UPDATE SET searching = excluded.searching
            """;

    private final Connection connection;

    public SqliteProfileRepository(Database database) {
        this.connection = database.getConnection();
    }

    @Override
    public boolean isSearching(long guildId, long userId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(IS_SEARCHING_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, userId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() && result.getBoolean(1);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to read searching state", e);
            }
        }
    }

    @Override
    public void setSearching(long guildId, long userId, boolean searching) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(SET_SEARCHING_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, userId);
                statement.setBoolean(3, searching);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to update searching state", e);
            }
        }
    }
}
