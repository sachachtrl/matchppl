package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.GuildSettings;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.model.Tag;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteGuildDataRepositoryTest {
    private static final long GUILD = 1L;
    private static final long OTHER_GUILD = 2L;
    private static final long ALICE = 10L;
    private static final long BOB = 20L;
    private static final long ROLE = 100L;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);
    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    private record Filled(Tag tag, Proposal proposal, Room room) {}

    private Database database;
    private SqliteActivityRepository activityRepo;
    private SqliteProfileRepository profileRepo;
    private SqliteTagRepository tagRepo;
    private SqliteGuildConfigRepository guildConfigRepo;
    private SqliteProposalRepository proposalRepo;
    private SqliteRoomRepository roomRepo;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.activityRepo = new SqliteActivityRepository(this.database);
        this.profileRepo = new SqliteProfileRepository(this.database, new TextCipher(new byte[32]));
        this.tagRepo = new SqliteTagRepository(this.database);
        this.guildConfigRepo = new SqliteGuildConfigRepository(this.database);
        this.proposalRepo = new SqliteProposalRepository(this.database);
        this.roomRepo = new SqliteRoomRepository(this.database);
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    private Filled fill(long guildId) {
        this.activityRepo.incrementMessageCount(guildId, ALICE, DAY);
        Tag tag = this.tagRepo.create(guildId, "Fortnite", "🎮").withRoles(Set.of(ROLE), Set.of());
        this.tagRepo.update(tag);
        this.profileRepo.save(Profile.empty(guildId, ALICE).withPresentation("Hello")
                .withTagSets(Set.of(tag.id()), Set.of(), Set.of()));
        this.guildConfigRepo.addAdminRole(guildId, ROLE);
        this.guildConfigRepo.saveSettings(GuildSettings.defaults(guildId).withRoomPrefix("Duo"));
        Proposal proposal = this.proposalRepo.create(guildId, ALICE, BOB, NOW, NOW.plusSeconds(3600));
        Room room = this.roomRepo.create(guildId, proposal.id(), ALICE, BOB, 1, NOW, NOW.plusSeconds(3600));
        return new Filled(tag, proposal, room);
    }

    private int countTagRoles(long tagId) throws SQLException {
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement("SELECT COUNT(*) FROM tag_role WHERE tag_id = ?")) {
            statement.setLong(1, tagId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    @Test
    void deletingAGuildRemovesAllItsDataAndNothingElse() throws SQLException {
        Filled removed = this.fill(GUILD);
        Filled kept = this.fill(OTHER_GUILD);

        new SqliteGuildDataRepository(this.database).deleteGuild(GUILD);

        assertEquals(0, this.activityRepo.countMessages(GUILD, ALICE, DAY, DAY));
        assertEquals(0, this.activityRepo.countAllMessages(GUILD, ALICE));
        assertEquals(Profile.empty(GUILD, ALICE), this.profileRepo.find(GUILD, ALICE));
        assertTrue(this.tagRepo.findByGuild(GUILD).isEmpty());
        assertEquals(0, this.countTagRoles(removed.tag().id()));
        assertTrue(this.guildConfigRepo.findAdminRoles(GUILD).isEmpty());
        assertEquals(GuildSettings.defaults(GUILD), this.guildConfigRepo.findSettings(GUILD));
        assertFalse(this.proposalRepo.findById(removed.proposal().id()).isPresent());
        assertFalse(this.roomRepo.findById(removed.room().id()).isPresent());

        assertEquals(1, this.activityRepo.countAllMessages(OTHER_GUILD, ALICE));
        assertEquals("Hello", this.profileRepo.find(OTHER_GUILD, ALICE).presentation());
        assertEquals(1, this.countTagRoles(kept.tag().id()));
        assertEquals("Duo", this.guildConfigRepo.findSettings(OTHER_GUILD).roomPrefix());
        assertTrue(this.proposalRepo.findById(kept.proposal().id()).isPresent());
        assertTrue(this.roomRepo.findById(kept.room().id()).isPresent());
    }
}
