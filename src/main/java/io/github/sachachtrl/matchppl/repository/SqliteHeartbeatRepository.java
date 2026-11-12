package io.github.sachachtrl.matchppl.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class SqliteHeartbeatRepository implements HeartbeatRepository {
    private static final String FIND_SQL = """
            SELECT beat_at FROM heartbeat WHERE id = 1
            """;
    private static final String SAVE_SQL = """
            INSERT INTO heartbeat (id, beat_at) VALUES (1, ?)
            ON CONFLICT (id) DO UPDATE SET beat_at = excluded.beat_at
            """;

    private final Connection connection;

    public SqliteHeartbeatRepository(Database database) {
        this.connection = database.getConnection();
    }

    @Override
    public Optional<Instant> findLast() {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_SQL);
                    ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(Instant.ofEpochMilli(result.getLong(1))) : Optional.empty();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to read heartbeat", e);
            }
        }
    }

    @Override
    public void save(Instant beatAt) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(SAVE_SQL)) {
                statement.setLong(1, beatAt.toEpochMilli());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to save heartbeat", e);
            }
        }
    }
}
