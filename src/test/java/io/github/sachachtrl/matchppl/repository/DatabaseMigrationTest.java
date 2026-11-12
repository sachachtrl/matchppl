package io.github.sachachtrl.matchppl.repository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseMigrationTest {
    @TempDir
    Path folder;

    @Test
    void existingDailyCountsBecomeTheFirstTotals() throws SQLException {
        String url = "jdbc:sqlite:" + this.folder.resolve("old.db");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE message_activity (
                        guild_id INTEGER NOT NULL,
                        user_id  INTEGER NOT NULL,
                        day      TEXT    NOT NULL,
                        count    INTEGER NOT NULL,
                        PRIMARY KEY (guild_id, user_id, day)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO message_activity VALUES
                        (1, 10, '2026-09-01', 4), (1, 10, '2026-11-01', 3), (1, 20, '2026-11-01', 5)
                    """);
        }

        try (Database database = new Database(url)) {
            SqliteActivityRepository repository = new SqliteActivityRepository(database);

            assertEquals(7, repository.countAllMessages(1, 10));
            assertEquals(5, repository.countAllMessages(1, 20));
        }
        try (Database reopened = new Database(url)) {
            assertEquals(7, new SqliteActivityRepository(reopened).countAllMessages(1, 10));
        }
    }
}
