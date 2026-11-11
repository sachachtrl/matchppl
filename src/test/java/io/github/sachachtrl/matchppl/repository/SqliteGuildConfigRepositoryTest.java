package io.github.sachachtrl.matchppl.repository;

import java.sql.SQLException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteGuildConfigRepositoryTest {
    private static final long GUILD = 1L;
    private static final long OTHER_GUILD = 2L;
    private static final long ROLE = 100L;

    private Database database;
    private SqliteGuildConfigRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.repository = new SqliteGuildConfigRepository(this.database);
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    @Test
    void addingANewAdminRoleReportsAChange() {
        assertTrue(this.repository.addAdminRole(GUILD, ROLE));
    }

    @Test
    void addingAnExistingAdminRoleReportsNoChange() {
        this.repository.addAdminRole(GUILD, ROLE);

        assertFalse(this.repository.addAdminRole(GUILD, ROLE));
    }

    @Test
    void removingAnAdminRoleReportsAChange() {
        this.repository.addAdminRole(GUILD, ROLE);

        assertTrue(this.repository.removeAdminRole(GUILD, ROLE));
    }

    @Test
    void removingAnUnknownAdminRoleReportsNoChange() {
        assertFalse(this.repository.removeAdminRole(GUILD, ROLE));
    }

    @Test
    void adminRolesAreSeparatedByGuild() {
        this.repository.addAdminRole(GUILD, ROLE);

        assertTrue(this.repository.addAdminRole(OTHER_GUILD, ROLE));
        assertFalse(this.repository.removeAdminRole(OTHER_GUILD, ROLE + 1));
    }

    @Test
    void adminRolesOfAGuildAreListed() {
        this.repository.addAdminRole(GUILD, ROLE);
        this.repository.addAdminRole(GUILD, ROLE + 1);
        this.repository.addAdminRole(OTHER_GUILD, ROLE + 2);
        this.repository.removeAdminRole(GUILD, ROLE + 1);

        assertEquals(Set.of(ROLE), this.repository.findAdminRoles(GUILD));
    }
}
