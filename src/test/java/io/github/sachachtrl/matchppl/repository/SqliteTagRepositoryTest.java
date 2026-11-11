package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.Tag;

import java.sql.SQLException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteTagRepositoryTest {
    private static final long GUILD = 1L;
    private static final long OTHER_GUILD = 2L;

    private Database database;
    private SqliteTagRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.repository = new SqliteTagRepository(this.database);
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    @Test
    void createdTagHasNoCondition() {
        Tag created = this.repository.create(GUILD, "Fortnite", "🎮");

        Tag found = this.repository.findById(GUILD, created.id()).orElseThrow();
        assertEquals("Fortnite", found.name());
        assertEquals("🎮", found.emoji());
        assertNull(found.minAccountAgeDays());
        assertNull(found.minMemberDays());
        assertNull(found.minMessages());
        assertNull(found.activityPeriod());
        assertTrue(found.requiredRoleIds().isEmpty());
        assertTrue(found.forbiddenRoleIds().isEmpty());
    }

    @Test
    void nameLookupIgnoresCase() {
        this.repository.create(GUILD, "Fortnite", "🎮");

        assertTrue(this.repository.existsByName(GUILD, "fortnite"));
        assertTrue(this.repository.existsByName(GUILD, " FORTNITE "));
        assertFalse(this.repository.existsByName(GUILD, "Lille"));
    }

    @Test
    void tagsAreSeparatedByGuild() {
        Tag created = this.repository.create(GUILD, "Fortnite", "🎮");

        assertFalse(this.repository.existsByName(OTHER_GUILD, "Fortnite"));
        assertTrue(this.repository.findById(OTHER_GUILD, created.id()).isEmpty());
    }

    @Test
    void updatePersistsConditionsAndRoles() {
        Tag created = this.repository.create(GUILD, "Fortnite", "🎮");

        this.repository.update(created
                .withMinAccountAgeDays(30)
                .withMinMemberDays(7)
                .withActivity(20, ActivityPeriod.TWO_WEEKS)
                .withRoles(Set.of(100L, 101L), Set.of(200L)));

        Tag found = this.repository.findById(GUILD, created.id()).orElseThrow();
        assertEquals(30, found.minAccountAgeDays());
        assertEquals(7, found.minMemberDays());
        assertEquals(20, found.minMessages());
        assertEquals(ActivityPeriod.TWO_WEEKS, found.activityPeriod());
        assertEquals(Set.of(100L, 101L), found.requiredRoleIds());
        assertEquals(Set.of(200L), found.forbiddenRoleIds());
    }

    @Test
    void updateReplacesPreviousRoles() {
        Tag created = this.repository.create(GUILD, "Fortnite", "🎮");
        this.repository.update(created.withRoles(Set.of(100L, 101L), Set.of(200L)));

        this.repository.update(created.withRoles(Set.of(102L), Set.of()));

        Tag found = this.repository.findById(GUILD, created.id()).orElseThrow();
        assertEquals(Set.of(102L), found.requiredRoleIds());
        assertTrue(found.forbiddenRoleIds().isEmpty());
    }
}
