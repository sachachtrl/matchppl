package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.Tag;

import java.sql.SQLException;
import java.util.List;
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
        assertNull(found.conditions().minAccountAgeDays());
        assertNull(found.conditions().minMemberDays());
        assertNull(found.conditions().minMessages());
        assertNull(found.conditions().activityPeriod());
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
                .withConditions(new MemberConditions(30, 7, 20, ActivityPeriod.TWO_WEEKS))
                .withRoles(Set.of(100L, 101L), Set.of(200L)));

        Tag found = this.repository.findById(GUILD, created.id()).orElseThrow();
        assertEquals(30, found.conditions().minAccountAgeDays());
        assertEquals(7, found.conditions().minMemberDays());
        assertEquals(20, found.conditions().minMessages());
        assertEquals(ActivityPeriod.TWO_WEEKS, found.conditions().activityPeriod());
        assertEquals(Set.of(100L, 101L), found.requiredRoleIds());
        assertEquals(Set.of(200L), found.forbiddenRoleIds());
    }

    @Test
    void tagsOfAGuildAreListedByName() {
        this.repository.create(GUILD, "lille", "🏙️");
        this.repository.create(GUILD, "Fortnite", "🎮");
        this.repository.create(GUILD, "Board games", "🎲");
        this.repository.create(OTHER_GUILD, "Cinema", "🎬");

        List<String> names = this.repository.findByGuild(GUILD).stream().map(Tag::name).toList();

        assertEquals(List.of("Board games", "Fortnite", "lille"), names);
    }

    @Test
    void deletingATagRemovesItAndItsRoles() {
        Tag created = this.repository.create(GUILD, "Fortnite", "🎮");
        this.repository.update(created.withRoles(Set.of(100L), Set.of(200L)));

        assertTrue(this.repository.delete(GUILD, created.id()));

        assertTrue(this.repository.findById(GUILD, created.id()).isEmpty());
        assertFalse(this.repository.existsByName(GUILD, "Fortnite"));
        Tag recreated = this.repository.create(GUILD, "Fortnite", "🎮");
        assertTrue(this.repository.findById(GUILD, recreated.id()).orElseThrow().requiredRoleIds().isEmpty());
    }

    @Test
    void aTagCannotBeDeletedFromAnotherGuild() {
        Tag created = this.repository.create(GUILD, "Fortnite", "🎮");

        assertFalse(this.repository.delete(OTHER_GUILD, created.id()));
        assertTrue(this.repository.findById(GUILD, created.id()).isPresent());
    }

    @Test
    void deletingAnUnknownTagReportsNoChange() {
        assertFalse(this.repository.delete(GUILD, 999L));
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
