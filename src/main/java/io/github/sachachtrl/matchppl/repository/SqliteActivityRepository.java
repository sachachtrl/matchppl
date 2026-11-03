package io.github.sachachtrl.matchppl.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class SqliteActivityRepository implements ActivityRepository {
    private static final String INCREMENT_SQL = """
            INSERT INTO message_activity (guild_id, user_id, day, count)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (guild_id, user_id, day) DO UPDATE SET count = count + 1
            """;
    private static final String COUNT_SQL = """
            SELECT COALESCE(SUM(count), 0)
            FROM message_activity
            WHERE guild_id = ? AND user_id = ? AND day BETWEEN ? AND ?
            """;

    private final Connection connection;

    public SqliteActivityRepository(Database database) {
        this.connection = database.getConnection();
    }

    @Override
    public void incrementMessageCount(long guildId, long userId, LocalDate day) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(INCREMENT_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, userId);
                statement.setString(3, day.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to increment message count", e);
            }
        }
    }

    @Override
    public int countMessages(long guildId, long userId, LocalDate from, LocalDate to) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(COUNT_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, userId);
                statement.setString(3, from.toString());
                statement.setString(4, to.toString());
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to count messages", e);
            }
        }
    }
}
