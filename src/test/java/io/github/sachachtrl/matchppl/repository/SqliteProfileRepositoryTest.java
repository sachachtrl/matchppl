package io.github.sachachtrl.matchppl.repository;

import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteProfileRepositoryTest {
    private static final long GUILD = 1L;
    private static final long OTHER_GUILD = 2L;
    private static final long USER = 10L;

    private Database database;
    private SqliteProfileRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.repository = new SqliteProfileRepository(this.database);
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    @Test
    void memberWithoutProfileIsNotSearching() {
        assertFalse(this.repository.isSearching(GUILD, USER));
    }

    @Test
    void searchCanBeEnabledThenDisabled() {
        this.repository.setSearching(GUILD, USER, true);
        assertTrue(this.repository.isSearching(GUILD, USER));

        this.repository.setSearching(GUILD, USER, false);
        assertFalse(this.repository.isSearching(GUILD, USER));
    }

    @Test
    void searchStateIsSeparatedByGuild() {
        this.repository.setSearching(GUILD, USER, true);

        assertFalse(this.repository.isSearching(OTHER_GUILD, USER));
    }
}
