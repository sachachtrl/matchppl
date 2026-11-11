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
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tag (
                        id                   INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id             INTEGER NOT NULL,
                        name                 TEXT    NOT NULL,
                        name_key             TEXT    NOT NULL,
                        emoji                TEXT    NOT NULL,
                        min_account_age_days INTEGER,
                        min_member_days      INTEGER,
                        min_messages         INTEGER,
                        activity_period      TEXT,
                        UNIQUE (guild_id, name_key)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tag_role (
                        tag_id  INTEGER NOT NULL,
                        role_id INTEGER NOT NULL,
                        kind    TEXT    NOT NULL,
                        PRIMARY KEY (tag_id, role_id, kind)
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
