package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.InMemoryActivityRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProfileRepository;
import io.github.sachachtrl.matchppl.repository.SqliteTagRepository;
import io.github.sachachtrl.matchppl.repository.TextCipher;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileServiceTest {
    private static final long GUILD = 1L;
    private static final long USER = 10L;
    private static final long VERIFIED_ROLE = 100L;
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final MemberSnapshot MEMBER = new MemberSnapshot(GUILD, USER, NOW, NOW, Set.of());

    private Database database;
    private TagService tagService;
    private ProfileService service;
    private Tag finals;
    private Tag medium;
    private Tag light;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        ConditionChecker checker = new ConditionChecker(new ActivityService(new InMemoryActivityRepository()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        this.tagService = new TagService(new SqliteTagRepository(this.database), checker);
        this.service = new ProfileService(
                new SqliteProfileRepository(this.database, new TextCipher(new byte[32])), this.tagService);
        this.finals = this.createTag("The Finals");
        this.medium = this.createTag("Medium");
        this.light = this.createTag("Light");
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    private Tag createTag(String name) {
        return ((TagService.Created) this.tagService.createTag(GUILD, name, "🎮")).tag();
    }

    @Test
    void searchCannotBeEnabledWithoutTags() {
        assertFalse(this.service.setSearching(GUILD, USER, true));

        assertFalse(this.service.getProfile(GUILD, USER).searching());
    }

    @Test
    void searchCanBeEnabledOnceATagIsChosen() {
        this.service.setTags(MEMBER, Set.of(this.finals.id()));

        assertTrue(this.service.setSearching(GUILD, USER, true));
        assertTrue(this.service.getProfile(GUILD, USER).searching());
    }

    @Test
    void removingEveryTagDisablesTheSearch() {
        this.service.setTags(MEMBER, Set.of(this.finals.id()));
        this.service.setSearching(GUILD, USER, true);

        Profile updated = this.service.setTags(MEMBER, Set.of());

        assertFalse(updated.searching());
    }

    @Test
    void tagsTheMemberCannotUseAreIgnored() {
        this.tagService.setRequiredRoles(GUILD, this.medium.id(), Set.of(VERIFIED_ROLE));

        Profile updated = this.service.setTags(MEMBER, Set.of(this.finals.id(), this.medium.id()));

        assertEquals(Set.of(this.finals.id()), updated.tagIds());
    }

    @Test
    void unknownTagsAreIgnored() {
        Profile updated = this.service.setExcludedTags(GUILD, USER, Set.of(this.light.id(), 999L));

        assertEquals(Set.of(this.light.id()), updated.excludedTagIds());
    }

    @Test
    void excludingATagRemovesItFromChosenAndRequiredTags() {
        this.service.setTags(MEMBER, Set.of(this.finals.id(), this.light.id()));
        this.service.setRequiredTags(GUILD, USER, Set.of(this.light.id()));

        Profile updated = this.service.setExcludedTags(GUILD, USER, Set.of(this.light.id()));

        assertEquals(Set.of(this.finals.id()), updated.tagIds());
        assertTrue(updated.requiredTagIds().isEmpty());
        assertEquals(Set.of(this.light.id()), updated.excludedTagIds());
    }

    @Test
    void choosingATagRemovesItFromExcludedTags() {
        this.service.setExcludedTags(GUILD, USER, Set.of(this.light.id(), this.medium.id()));

        Profile updated = this.service.setTags(MEMBER, Set.of(this.light.id()));

        assertEquals(Set.of(this.medium.id()), updated.excludedTagIds());
    }

    @Test
    void requiringATagRemovesItFromExcludedTags() {
        this.service.setExcludedTags(GUILD, USER, Set.of(this.light.id()));

        Profile updated = this.service.setRequiredTags(GUILD, USER, Set.of(this.light.id()));

        assertTrue(updated.excludedTagIds().isEmpty());
    }

    @Test
    void presentationIsTrimmedAndEmptyMeansNone() {
        this.service.setPresentation(GUILD, USER, "  Hello!  ");
        assertEquals("Hello!", this.service.getProfile(GUILD, USER).presentation());

        this.service.setPresentation(GUILD, USER, "   ");
        assertNull(this.service.getProfile(GUILD, USER).presentation());
    }

    @Test
    void tooLongPresentationIsRejected() {
        String text = "a".repeat(ProfileService.MAX_PRESENTATION_LENGTH + 1);

        assertFalse(this.service.setPresentation(GUILD, USER, text));
        assertNull(this.service.getProfile(GUILD, USER).presentation());
    }

    @Test
    void compatibilityMustBeBetweenZeroAndOneHundred() {
        assertFalse(this.service.setMinCompatibility(GUILD, USER, 101));
        assertTrue(this.service.setMinCompatibility(GUILD, USER, 50));
        assertEquals(50, this.service.getProfile(GUILD, USER).minCompatibility());

        assertTrue(this.service.setMinCompatibility(GUILD, USER, 0));
        assertNull(this.service.getProfile(GUILD, USER).minCompatibility());
    }

    @Test
    void partnerConditionsFollowTheTagRules() {
        Profile updated = this.service.setPartnerConditions(GUILD, USER, 30, 0, 20, null);

        assertEquals(30, updated.partnerConditions().minAccountAgeDays());
        assertNull(updated.partnerConditions().minMemberDays());
        assertEquals(20, updated.partnerConditions().minMessages());
        assertEquals(ActivityPeriod.THIRTY_DAYS, updated.partnerConditions().activityPeriod());
    }
}
