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
    private static final String INCREMENT_TOTAL_SQL = """
            INSERT INTO message_total (guild_id, user_id, count)
            VALUES (?, ?, 1)
            ON CONFLICT (guild_id, user_id) DO UPDATE SET count = count + 1
            """;
    private static final String COUNT_SQL = """
            SELECT COALESCE(SUM(count), 0)
            FROM message_activity
            WHERE guild_id = ? AND user_id = ? AND day BETWEEN ? AND ?
            """;
    private static final String COUNT_TOTAL_SQL = """
            SELECT COALESCE(SUM(count), 0) FROM message_total WHERE guild_id = ? AND user_id = ?
            """;
    private static final String DELETE_BEFORE_SQL = """
            DELETE FROM message_activity WHERE day < ?
            """;

    private final Connection connection;

    public SqliteActivityRepository(Database database) {
        this.connection = database.getConnection();
    }

    @Override
    public void incrementMessageCount(long guildId, long userId, LocalDate day) {
        synchronized (this.connection) {
            try {
                this.connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = this.connection.prepareStatement(INCREMENT_SQL)) {
                        statement.setLong(1, guildId);
                        statement.setLong(2, userId);
                        statement.setString(3, day.toString());
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = this.connection.prepareStatement(INCREMENT_TOTAL_SQL)) {
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

    @Override
    public int countAllMessages(long guildId, long userId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(COUNT_TOTAL_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, userId);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to count all messages", e);
            }
        }
    }

    @Override
    public int deleteDaysBefore(LocalDate day) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(DELETE_BEFORE_SQL)) {
                statement.setString(1, day.toString());
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to delete old message counts", e);
            }
        }
    }
}
