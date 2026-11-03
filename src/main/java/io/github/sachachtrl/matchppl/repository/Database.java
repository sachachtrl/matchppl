package io.github.sachachtrl.matchppl.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database implements AutoCloseable {
    private final Connection connection;

    public Database(String url) throws SQLException {
        this.connection = DriverManager.getConnection(url);
        createSchema();
    }

    private void createSchema() throws SQLException {
        try (Statement statement = this.connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS message_activity (
                        guild_id INTEGER NOT NULL,
                        user_id  INTEGER NOT NULL,
                        day      TEXT    NOT NULL,
                        count    INTEGER NOT NULL,
                        PRIMARY KEY (guild_id, user_id, day)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS profile (
                        guild_id  INTEGER NOT NULL,
                        user_id   INTEGER NOT NULL,
                        searching INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (guild_id, user_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS guild_admin_role (
                        guild_id INTEGER NOT NULL,
                        role_id  INTEGER NOT NULL,
                        PRIMARY KEY (guild_id, role_id)
                    )
                    """);
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    @Override
    public void close() throws SQLException {
        this.connection.close();
    }
}
