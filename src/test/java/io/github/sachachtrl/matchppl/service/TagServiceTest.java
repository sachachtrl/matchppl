package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.InMemoryActivityRepository;
import io.github.sachachtrl.matchppl.repository.SqliteTagRepository;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagServiceTest {
    private static final long GUILD = 1L;
    private static final long USER = 10L;
    private static final long VERIFIED_ROLE = 100L;
    private static final long MUTED_ROLE = 200L;
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    private Database database;
    private InMemoryActivityRepository activityRepo;
    private TagService service;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.activityRepo = new InMemoryActivityRepository();
        ConditionChecker checker = new ConditionChecker(new ActivityService(this.activityRepo),
                Clock.fixed(NOW, ZoneOffset.UTC));
        this.service = new TagService(new SqliteTagRepository(this.database), checker);
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    private Tag createTag(String name) {
        return ((TagService.Created) this.service.createTag(GUILD, name, "🎮")).tag();
    }

    private static MemberSnapshot member(int accountAgeDays, int memberDays, Set<Long> roles) {
        return new MemberSnapshot(GUILD, USER, NOW.minus(Duration.ofDays(accountAgeDays)),
                NOW.minus(Duration.ofDays(memberDays)), roles, true);
    }

    @Test
    void createdTagNameIsTrimmed() {
        Tag tag = this.createTag("  Fortnite  ");

        assertEquals("Fortnite", tag.name());
    }

    @Test
    void blankNameIsRejected() {
        assertInstanceOf(TagService.InvalidName.class, this.service.createTag(GUILD, "   ", "🎮"));
    }

    @Test
    void tooLongNameIsRejected() {
        String name = "a".repeat(TagService.MAX_NAME_LENGTH + 1);

        assertInstanceOf(TagService.InvalidName.class, this.service.createTag(GUILD, name, "🎮"));
    }

    @Test
    void nameAtMaximumLengthIsAccepted() {
        String name = "a".repeat(TagService.MAX_NAME_LENGTH);

        assertInstanceOf(TagService.Created.class, this.service.createTag(GUILD, name, "🎮"));
    }

    @Test
    void duplicateNameIsRejectedWhateverTheCase() {
        this.createTag("Fortnite");

        assertInstanceOf(TagService.NameTaken.class, this.service.createTag(GUILD, "FORTNITE", "🎮"));
    }

    @Test
    void zeroMeansNoMinimum() {
        Tag tag = this.createTag("Fortnite");
        this.service.setMinAccountAge(GUILD, tag.id(), 30);

        Tag updated = this.service.setMinAccountAge(GUILD, tag.id(), 0).orElseThrow();

        assertNull(updated.conditions().minAccountAgeDays());
    }

    @Test
    void activityWithoutPeriodUsesTheDefaultPeriod() {
        Tag tag = this.createTag("Fortnite");

        Tag updated = this.service.setActivity(GUILD, tag.id(), 20, null).orElseThrow();

        assertEquals(20, updated.conditions().minMessages());
        assertEquals(ConditionRules.DEFAULT_ACTIVITY_PERIOD, updated.conditions().activityPeriod());
    }

    @Test
    void removingTheMessageMinimumAlsoClearsThePeriod() {
        Tag tag = this.createTag("Fortnite");
        this.service.setActivity(GUILD, tag.id(), 20, ActivityPeriod.TWO_WEEKS);

        Tag updated = this.service.setActivity(GUILD, tag.id(), null, ActivityPeriod.TWO_WEEKS).orElseThrow();

        assertNull(updated.conditions().minMessages());
        assertNull(updated.conditions().activityPeriod());
    }

    @Test
    void aRoleCannotBeBothRequiredAndForbidden() {
        Tag tag = this.createTag("Fortnite");
        this.service.setForbiddenRoles(GUILD, tag.id(), Set.of(100L, 200L));

        Tag updated = this.service.setRequiredRoles(GUILD, tag.id(), Set.of(100L)).orElseThrow();

        assertEquals(Set.of(100L), updated.requiredRoleIds());
        assertEquals(Set.of(200L), updated.forbiddenRoleIds());
    }

    @Test
    void deletedTagNameCanBeReused() {
        Tag tag = this.createTag("Fortnite");

        assertTrue(this.service.deleteTag(GUILD, tag.id()));

        assertTrue(this.service.listTags(GUILD).isEmpty());
        assertInstanceOf(TagService.Created.class, this.service.createTag(GUILD, "Fortnite", "🎮"));
    }

    @Test
    void updatingAnUnknownTagReturnsNothing() {
        assertTrue(this.service.setMinMemberDays(GUILD, 999L, 7).isEmpty());
    }

    @Test
    void tagWithoutConditionCanBeUsedByAnyone() {
        Tag tag = this.createTag("Fortnite");

        assertTrue(this.service.canUse(tag, member(0, 0, Set.of())));
    }

    @Test
    void requiredRolesNeedAtLeastOneOfThem() {
        Tag tag = this.service.setRequiredRoles(GUILD, this.createTag("Fortnite").id(), Set.of(VERIFIED_ROLE, 300L))
                .orElseThrow();

        assertTrue(this.service.canUse(tag, member(0, 0, Set.of(VERIFIED_ROLE))));
        assertFalse(this.service.canUse(tag, member(0, 0, Set.of())));
    }

    @Test
    void oneForbiddenRoleIsEnoughToRefuse() {
        Tag tag = this.service.setForbiddenRoles(GUILD, this.createTag("Fortnite").id(), Set.of(MUTED_ROLE))
                .orElseThrow();

        assertFalse(this.service.canUse(tag, member(0, 0, Set.of(VERIFIED_ROLE, MUTED_ROLE))));
    }

    @Test
    void accountAgeAndTimeOnTheServerAreChecked() {
        Tag tag = this.createTag("Fortnite");
        this.service.setMinAccountAge(GUILD, tag.id(), 30);
        tag = this.service.setMinMemberDays(GUILD, tag.id(), 7).orElseThrow();

        assertTrue(this.service.canUse(tag, member(30, 7, Set.of())));
        assertFalse(this.service.canUse(tag, member(29, 7, Set.of())));
        assertFalse(this.service.canUse(tag, member(30, 6, Set.of())));
    }

    @Test
    void activityIsCountedOnThePeriodOnly() {
        Tag tag = this.service.setActivity(GUILD, this.createTag("Fortnite").id(), 2, ActivityPeriod.TWO_WEEKS)
                .orElseThrow();
        LocalDate today = LocalDate.ofInstant(NOW, ZoneOffset.UTC);
        this.activityRepo.incrementMessageCount(GUILD, USER, today.minusDays(20));
        this.activityRepo.incrementMessageCount(GUILD, USER, today.minusDays(13));
        assertFalse(this.service.canUse(tag, member(0, 0, Set.of())));

        this.activityRepo.incrementMessageCount(GUILD, USER, today);

        assertTrue(this.service.canUse(tag, member(0, 0, Set.of())));
    }

    @Test
    void usableTagsOnlyListsAllowedTags() {
        Tag open = this.createTag("Lille");
        Tag restricted = this.createTag("Fortnite");
        this.service.setRequiredRoles(GUILD, restricted.id(), Set.of(VERIFIED_ROLE));

        assertEquals(List.of(open.id()),
                this.service.usableTags(member(0, 0, Set.of())).stream().map(Tag::id).toList());
    }
}
