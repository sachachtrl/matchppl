package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.MatchPair;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.ProposalStatus;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.InMemoryActivityRepository;
import io.github.sachachtrl.matchppl.repository.SqliteGuildConfigRepository;
import io.github.sachachtrl.matchppl.repository.SqliteHeartbeatRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProfileRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProposalRepository;
import io.github.sachachtrl.matchppl.repository.SqliteRoomRepository;
import io.github.sachachtrl.matchppl.repository.SqliteTagRepository;
import io.github.sachachtrl.matchppl.repository.TextCipher;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatServiceTest {
    private static final long GUILD = 1L;
    private static final long ALICE = 10L;
    private static final long BOB = 20L;
    private static final Instant START = Instant.parse("2026-11-12T12:00:00Z");

    private Database database;
    private MutableClock clock;
    private ProposalService proposalService;
    private HeartbeatService service;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.clock = new MutableClock(START);
        ConditionChecker checker = new ConditionChecker(new ActivityService(new InMemoryActivityRepository()),
                this.clock);
        TagService tagService = new TagService(new SqliteTagRepository(this.database), checker);
        ProfileService profileService = new ProfileService(
                new SqliteProfileRepository(this.database, new TextCipher(new byte[32])), tagService, this.clock);
        this.proposalService = new ProposalService(new SqliteProposalRepository(this.database), profileService,
                new RoomService(new SqliteRoomRepository(this.database), profileService,
                        new GuildConfigService(new SqliteGuildConfigRepository(this.database)), this.clock),
                this.clock);
        this.service = new HeartbeatService(new SqliteHeartbeatRepository(this.database), this.proposalService,
                this.clock);
        Tag tag = ((TagService.Created) tagService.createTag(GUILD, "Fortnite", "🎮")).tag();
        for (long user : List.of(ALICE, BOB)) {
            profileService.setTags(new MemberSnapshot(GUILD, user, START, START, Set.of(), true), Set.of(tag.id()));
            profileService.setSearching(GUILD, user, true);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    private Proposal propose() {
        return this.proposalService.createProposals(GUILD, List.of(new MatchPair(ALICE, BOB, 1, 1)),
                Duration.ofHours(24)).getFirst();
    }

    private ProposalStatus statusOf(Proposal proposal) {
        return this.proposalService.findProposal(proposal.id()).orElseThrow().status();
    }

    @Test
    void theFirstStartIsNotADowntime() {
        Proposal proposal = this.propose();

        assertTrue(this.service.recoverFromDowntime().isEmpty());
        assertEquals(ProposalStatus.OPEN, this.statusOf(proposal));
    }

    @Test
    void aQuickRestartKeepsProposalsOpen() {
        this.service.beat();
        Proposal proposal = this.propose();

        this.clock.advance(HeartbeatService.DOWNTIME_THRESHOLD);

        assertTrue(this.service.recoverFromDowntime().isEmpty());
        assertEquals(ProposalStatus.OPEN, this.statusOf(proposal));
    }

    @Test
    void aLongDowntimeInterruptsOpenProposalsAndFreesTheMembers() {
        this.service.beat();
        Proposal proposal = this.propose();

        this.clock.advance(HeartbeatService.DOWNTIME_THRESHOLD.plusSeconds(1));

        assertEquals(1, this.service.recoverFromDowntime().size());
        assertEquals(ProposalStatus.INTERRUPTED, this.statusOf(proposal));
        assertTrue(this.proposalService.busyUsers(GUILD).isEmpty());
        assertTrue(this.proposalService.blockedPairs(GUILD).isEmpty());
    }

    @Test
    void recoveringRecordsANewHeartbeat() {
        this.service.beat();
        this.clock.advance(Duration.ofHours(1));
        this.service.recoverFromDowntime();

        this.propose();
        this.clock.advance(Duration.ofMinutes(1));

        assertTrue(this.service.recoverFromDowntime().isEmpty());
    }
}
