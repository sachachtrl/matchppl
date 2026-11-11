package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.SqliteTagRepository;

import java.sql.SQLException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagServiceTest {
    private static final long GUILD = 1L;

    private Database database;
    private TagService service;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.service = new TagService(new SqliteTagRepository(this.database));
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    private Tag createTag(String name) {
        return ((TagService.Created) this.service.createTag(GUILD, name, "🎮")).tag();
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

        assertNull(updated.minAccountAgeDays());
    }

    @Test
    void activityWithoutPeriodUsesTheDefaultPeriod() {
        Tag tag = this.createTag("Fortnite");

        Tag updated = this.service.setActivity(GUILD, tag.id(), 20, null).orElseThrow();

        assertEquals(20, updated.minMessages());
        assertEquals(TagService.DEFAULT_ACTIVITY_PERIOD, updated.activityPeriod());
    }

    @Test
    void removingTheMessageMinimumAlsoClearsThePeriod() {
        Tag tag = this.createTag("Fortnite");
        this.service.setActivity(GUILD, tag.id(), 20, ActivityPeriod.TWO_WEEKS);

        Tag updated = this.service.setActivity(GUILD, tag.id(), null, ActivityPeriod.TWO_WEEKS).orElseThrow();

        assertNull(updated.minMessages());
        assertNull(updated.activityPeriod());
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
    void updatingAnUnknownTagReturnsNothing() {
        assertTrue(this.service.setMinMemberDays(GUILD, 999L, 7).isEmpty());
    }
}
