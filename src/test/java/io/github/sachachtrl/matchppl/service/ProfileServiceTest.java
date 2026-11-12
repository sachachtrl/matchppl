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
import java.util.List;
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
    private static final MemberSnapshot MEMBER = new MemberSnapshot(GUILD, USER, NOW, NOW, Set.of(), true);

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
                new SqliteProfileRepository(this.database, new TextCipher(new byte[32])), this.tagService,
                Clock.fixed(NOW, ZoneOffset.UTC));
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
    void aMenuOnlyReplacesTheTagsItShowed() {
        this.service.setTags(MEMBER, Set.of(this.finals.id(), this.medium.id()));

        Profile profile = this.service.setTags(MEMBER, Set.of(this.medium.id(), this.light.id()),
                Set.of(this.light.id()));

        assertEquals(Set.of(this.finals.id(), this.light.id()), profile.tagIds());
    }

    @Test
    void excludedAndRequiredMenusAlsoKeepTheTagsOfOtherPages() {
        this.service.setExcludedTags(GUILD, USER, Set.of(this.finals.id()));
        this.service.setRequiredTags(GUILD, USER, Set.of(this.medium.id()));

        this.service.setExcludedTags(GUILD, USER, Set.of(this.light.id()), Set.of(this.light.id()));
        Profile profile = this.service.setRequiredTags(GUILD, USER, Set.of(this.light.id()), Set.of());

        assertEquals(Set.of(this.finals.id(), this.light.id()), profile.excludedTagIds());
        assertEquals(Set.of(this.medium.id()), profile.preferences().requiredTagIds());
    }

    @Test
    void aMenuCannotAddATagItDidNotShow() {
        Profile profile = this.service.setTags(MEMBER, Set.of(this.finals.id()),
                Set.of(this.finals.id(), this.medium.id()));

        assertEquals(Set.of(this.finals.id()), profile.tagIds());
    }

    @Test
    void anAdminCanStopTheSearchOfEveryMemberOfOneServer() {
        long otherGuild = 2L;
        for (MemberSnapshot member : List.of(MEMBER, new MemberSnapshot(GUILD, USER + 1, NOW, NOW, Set.of(), true))) {
            this.service.setTags(member, Set.of(this.finals.id()));
            this.service.setSearching(GUILD, member.userId(), true);
        }
        Tag otherTag = ((TagService.Created) this.tagService.createTag(otherGuild, "Lille", "🏙️")).tag();
        this.service.setTags(new MemberSnapshot(otherGuild, USER, NOW, NOW, Set.of(), true), Set.of(otherTag.id()));
        this.service.setSearching(otherGuild, USER, true);
        assertEquals(2, this.service.countSearching(GUILD));

        assertEquals(2, this.service.stopAllSearches(GUILD));

        assertEquals(0, this.service.countSearching(GUILD));
        assertNull(this.service.getProfile(GUILD, USER).waitingSince());
        assertEquals(1, this.service.countSearching(otherGuild));
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
        assertTrue(updated.preferences().requiredTagIds().isEmpty());
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
        assertEquals(50, this.service.getProfile(GUILD, USER).preferences().minCompatibility());

        assertTrue(this.service.setMinCompatibility(GUILD, USER, 0));
        assertNull(this.service.getProfile(GUILD, USER).preferences().minCompatibility());
    }

    @Test
    void partnerConditionsFollowTheTagRules() {
        Profile updated = this.service.setPartnerConditions(GUILD, USER, 30, 0, 20, null);

        assertEquals(30, updated.preferences().conditions().minAccountAgeDays());
        assertNull(updated.preferences().conditions().minMemberDays());
        assertEquals(20, updated.preferences().conditions().minMessages());
        assertEquals(ActivityPeriod.THIRTY_DAYS, updated.preferences().conditions().activityPeriod());
    }

    @Test
    void enablingTheSearchRecordsWhenTheWaitStarted() {
        this.service.setTags(MEMBER, Set.of(this.finals.id()));

        this.service.setSearching(GUILD, USER, true);

        assertEquals(NOW, this.service.getProfile(GUILD, USER).waitingSince());
    }

    @Test
    void disablingTheSearchClearsTheWaitStart() {
        this.service.setTags(MEMBER, Set.of(this.finals.id()));
        this.service.setSearching(GUILD, USER, true);

        this.service.setSearching(GUILD, USER, false);

        assertNull(this.service.getProfile(GUILD, USER).waitingSince());
    }

    @Test
    void removingEveryTagAlsoClearsTheWaitStart() {
        this.service.setTags(MEMBER, Set.of(this.finals.id()));
        this.service.setSearching(GUILD, USER, true);

        assertNull(this.service.setTags(MEMBER, Set.of()).waitingSince());
    }

    @Test
    void minimumCommonTagsDefaultsToOneAndStaysWithinRange() {
        assertEquals(1, this.service.getProfile(GUILD, USER).preferences().minCommonTags());

        assertFalse(this.service.setMinCommonTags(GUILD, USER, ProfileService.MAX_MIN_COMMON_TAGS + 1));
        assertFalse(this.service.setMinCommonTags(GUILD, USER, -1));
        assertTrue(this.service.setMinCommonTags(GUILD, USER, 0));

        assertEquals(0, this.service.getProfile(GUILD, USER).preferences().minCommonTags());
    }

    @Test
    void anonymousAndDefaultAvatarExclusionsAreSavedSeparately() {
        this.service.setExcludeAnonymous(GUILD, USER, true);

        Profile updated = this.service.setExcludeDefaultAvatars(GUILD, USER, true);

        assertTrue(updated.preferences().excludeAnonymous());
        assertTrue(updated.preferences().excludeDefaultAvatars());
        assertFalse(this.service.setExcludeAnonymous(GUILD, USER, false).preferences().excludeAnonymous());
    }

    @Test
    void unreachableMemberStopsSearchingUntilEnablingItAgain() {
        this.service.setTags(MEMBER, Set.of(this.finals.id()));
        this.service.setSearching(GUILD, USER, true);

        this.service.markUnreachable(GUILD, USER);
        Profile unreachable = this.service.getProfile(GUILD, USER);
        assertFalse(unreachable.searching());
        assertTrue(unreachable.dmUnreachable());

        this.service.setSearching(GUILD, USER, true);
        assertFalse(this.service.getProfile(GUILD, USER).dmUnreachable());
    }
}
