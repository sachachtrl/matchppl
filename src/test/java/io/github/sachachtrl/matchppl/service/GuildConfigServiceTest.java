package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.GuildSettings;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.SqliteGuildConfigRepository;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void roleAddedThenRemovedElsewhereIsNoLongerListed() {
        assertTrue(this.service.listAdminRoles(GUILD).contains(ADMIN_ROLE));

        assertTrue(this.service.removeAdminRole(GUILD, ADMIN_ROLE));
        assertFalse(this.service.removeAdminRole(GUILD, ADMIN_ROLE));

        assertTrue(this.service.listAdminRoles(GUILD).isEmpty());
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

    @Test
    void aGuildWithoutSettingsUsesTheDefaults() {
        assertEquals(GuildSettings.defaults(GUILD), this.service.settings(GUILD));
        assertEquals(Duration.ofHours(24), this.service.responseDelay(GUILD));
        assertEquals(Duration.ofMinutes(1), this.service.matchingInterval(GUILD));
    }

    @Test
    void changedSettingsAreSavedPerGuild() {
        assertTrue(this.service.setResponseDelay(GUILD, Duration.ofHours(2)));
        assertTrue(this.service.setRoomLifetime(GUILD, Duration.ofDays(3)));
        this.service.setRoomsEnabled(GUILD, false);
        this.service.setCategory(GUILD, 500L);
        this.service.setLogChannel(GUILD, 600L);

        GuildSettings settings = this.service.settings(GUILD);
        assertEquals(Duration.ofHours(2), settings.responseDelay());
        assertEquals(Duration.ofDays(3), settings.roomLifetime());
        assertFalse(settings.roomsEnabled());
        assertEquals(500L, settings.categoryId());
        assertEquals(600L, settings.logChannelId());
        assertEquals(GuildSettings.defaults(OTHER_GUILD), this.service.settings(OTHER_GUILD));
    }

    @Test
    void durationsOutsideTheirLimitsAreRefused() {
        assertFalse(this.service.setResponseDelay(GUILD, Duration.ofSeconds(30)));
        assertFalse(this.service.setResponseDelay(GUILD, Duration.ofDays(8)));
        assertFalse(this.service.setMatchingInterval(GUILD, Duration.ofDays(2)));
        assertFalse(this.service.setRoomLifetime(GUILD, Duration.ofMinutes(5)));

        assertEquals(GuildSettings.defaults(GUILD), this.service.settings(GUILD));
    }

    @Test
    void roomPrefixIsTrimmedAndEmptyMeansTheDefault() {
        assertTrue(this.service.setRoomPrefix(GUILD, "  💓| Match  "));
        assertEquals("💓| Match", this.service.settings(GUILD).roomPrefix());

        assertTrue(this.service.setRoomPrefix(GUILD, "   "));
        assertEquals(GuildSettings.DEFAULT_ROOM_PREFIX, this.service.settings(GUILD).roomPrefix());

        assertFalse(this.service.setRoomPrefix(GUILD, "a".repeat(GuildConfigService.MAX_ROOM_PREFIX_LENGTH + 1)));
    }

    @Test
    void categoryAndLogChannelCanBeCleared() {
        this.service.setCategory(GUILD, 500L);
        this.service.setLogChannel(GUILD, 600L);

        this.service.setCategory(GUILD, null);
        this.service.setLogChannel(GUILD, null);

        assertNull(this.service.settings(GUILD).categoryId());
        assertNull(this.service.settings(GUILD).logChannelId());
    }
}
