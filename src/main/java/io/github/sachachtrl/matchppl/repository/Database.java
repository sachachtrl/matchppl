package io.github.sachachtrl.matchppl.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public class Database implements AutoCloseable {
    private final Connection connection;

    public Database(String url) throws SQLException {
        this.connection = DriverManager.getConnection(url);
        createSchema();
        migrateSchema();
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
                    CREATE TABLE IF NOT EXISTS profile_tag (
                        guild_id INTEGER NOT NULL,
                        user_id  INTEGER NOT NULL,
                        tag_id   INTEGER NOT NULL,
                        kind     TEXT    NOT NULL,
                        PRIMARY KEY (guild_id, user_id, tag_id, kind)
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
                    CREATE TABLE IF NOT EXISTS proposal (
                        id                INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id          INTEGER NOT NULL,
                        first_user_id     INTEGER NOT NULL,
                        second_user_id    INTEGER NOT NULL,
                        created_at        INTEGER NOT NULL,
                        deadline          INTEGER NOT NULL,
                        first_answer      TEXT    NOT NULL,
                        second_answer     TEXT    NOT NULL,
                        status            TEXT    NOT NULL,
                        first_message_id  INTEGER,
                        second_message_id INTEGER
                    )
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS proposal_by_status ON proposal (guild_id, status)
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

    private void migrateSchema() throws SQLException {
        this.addColumnIfMissing("profile", "presentation", "TEXT");
        this.addColumnIfMissing("profile", "anonymous", "INTEGER NOT NULL DEFAULT 0");
        this.addColumnIfMissing("profile", "min_compatibility", "INTEGER");
        this.renameColumnIfPresent("profile", "require_photo", "exclude_anonymous");
        this.addColumnIfMissing("profile", "exclude_anonymous", "INTEGER NOT NULL DEFAULT 0");
        this.addColumnIfMissing("profile", "min_account_age_days", "INTEGER");
        this.addColumnIfMissing("profile", "min_member_days", "INTEGER");
        this.addColumnIfMissing("profile", "min_messages", "INTEGER");
        this.addColumnIfMissing("profile", "activity_period", "TEXT");
        this.addColumnIfMissing("profile", "exclude_default_avatars", "INTEGER NOT NULL DEFAULT 0");
        this.addColumnIfMissing("profile", "min_common_tags", "INTEGER NOT NULL DEFAULT 1");
        this.addColumnIfMissing("profile", "waiting_since", "TEXT");
        this.addColumnIfMissing("profile", "dm_unreachable", "INTEGER NOT NULL DEFAULT 0");
        this.addColumnIfMissing("proposal", "unblocked", "INTEGER NOT NULL DEFAULT 0");
    }

    private void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        if (!this.columnsOf(table).contains(column)) {
            try (Statement statement = this.connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    private void renameColumnIfPresent(String table, String oldName, String newName) throws SQLException {
        Set<String> columns = this.columnsOf(table);
        if (columns.contains(oldName) && !columns.contains(newName)) {
            try (Statement statement = this.connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " RENAME COLUMN " + oldName + " TO " + newName);
            }
        }
    }

    private Set<String> columnsOf(String table) throws SQLException {
        try (Statement statement = this.connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            Set<String> columns = new HashSet<>();
            while (result.next()) {
                columns.add(result.getString("name"));
            }
            return columns;
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
