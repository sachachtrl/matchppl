package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.SqliteGuildConfigRepository;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildConfigServiceTest {
    private static final long GUILD = 1L;
    private static final long OTHER_GUILD = 2L;
    private static final long ADMIN_ROLE = 100L;
    private static final long OTHER_ROLE = 200L;

    private Database database;
    private GuildConfigService service;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.service = new GuildConfigService(new SqliteGuildConfigRepository(this.database));
        this.service.addAdminRole(GUILD, ADMIN_ROLE);
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    @Test
    void memberWithAnAdminRoleIsAdmin() {
        assertTrue(this.service.isAdmin(GUILD, List.of(OTHER_ROLE, ADMIN_ROLE), false));
    }

    @Test
    void memberWhoCanManageTheServerIsAlwaysAdmin() {
        assertTrue(this.service.isAdmin(GUILD, List.of(), true));
    }

    @Test
    void memberWithoutAdminRoleIsNotAdmin() {
        assertFalse(this.service.isAdmin(GUILD, List.of(OTHER_ROLE), false));
    }

    @Test
    void adminRolesOfAnotherGuildDoNotCount() {
        assertFalse(this.service.isAdmin(OTHER_GUILD, List.of(ADMIN_ROLE), false));
    }
}
