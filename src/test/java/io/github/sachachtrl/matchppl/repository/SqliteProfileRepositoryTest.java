package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Tag;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        this.repository = new SqliteProfileRepository(this.database, TextCipherTest.CIPHER);
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    @Test
    void memberWithoutProfileGetsAnEmptyProfile() {
        Profile profile = this.repository.find(GUILD, USER);

        assertFalse(profile.searching());
        assertNull(profile.presentation());
        assertTrue(profile.tagIds().isEmpty());
        assertTrue(profile.partnerConditions().isEmpty());
    }

    @Test
    void savedProfileIsReadBackIdentically() {
        Profile profile = new Profile(GUILD, USER, true, "Chill player", true, Set.of(1L, 2L), Set.of(3L), Set.of(1L),
                50, true, new MemberConditions(30, 7, 20, ActivityPeriod.TWO_WEEKS));

        this.repository.save(profile);

        assertEquals(profile, this.repository.find(GUILD, USER));
    }

    @Test
    void savingAgainReplacesThePreviousValues() {
        this.repository.save(Profile.empty(GUILD, USER).withTagSets(Set.of(1L, 2L), Set.of(3L), Set.of()));

        this.repository.save(Profile.empty(GUILD, USER).withTagSets(Set.of(4L), Set.of(), Set.of()));

        Profile found = this.repository.find(GUILD, USER);
        assertEquals(Set.of(4L), found.tagIds());
        assertTrue(found.excludedTagIds().isEmpty());
    }

    @Test
    void presentationIsStoredEncrypted() throws SQLException {
        this.repository.save(Profile.empty(GUILD, USER).withPresentation("secret introduction"));

        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement("SELECT presentation FROM profile WHERE guild_id = ? AND user_id = ?")) {
            statement.setLong(1, GUILD);
            statement.setLong(2, USER);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertFalse(result.getString(1).contains("secret"));
            }
        }
        assertEquals("secret introduction", this.repository.find(GUILD, USER).presentation());
    }

    @Test
    void profilesAreSeparatedByGuild() {
        this.repository.save(Profile.empty(GUILD, USER).withSearching(true).withPresentation("Hello"));

        Profile other = this.repository.find(OTHER_GUILD, USER);
        assertFalse(other.searching());
        assertNull(other.presentation());
    }

    @Test
    void deletingATagRemovesItFromProfiles() {
        SqliteTagRepository tagRepository = new SqliteTagRepository(this.database);
        Tag fortnite = tagRepository.create(GUILD, "Fortnite", "🎮");
        Tag lille = tagRepository.create(GUILD, "Lille", "🏙️");
        this.repository.save(Profile.empty(GUILD, USER)
                .withTagSets(Set.of(fortnite.id(), lille.id()), Set.of(), Set.of(fortnite.id())));

        tagRepository.delete(GUILD, fortnite.id());

        Profile found = this.repository.find(GUILD, USER);
        assertEquals(Set.of(lille.id()), found.tagIds());
        assertTrue(found.requiredTagIds().isEmpty());
    }
}
