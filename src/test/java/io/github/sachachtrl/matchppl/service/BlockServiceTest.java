package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.MatchPair;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.ProposalAnswer;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.model.UserPair;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.InMemoryActivityRepository;
import io.github.sachachtrl.matchppl.repository.SqliteBlockRepository;
import io.github.sachachtrl.matchppl.repository.SqliteGuildConfigRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockServiceTest {
    private static final long GUILD = 1L;
    private static final long OTHER_GUILD = 2L;
    private static final long ALICE = 10L;
    private static final long BOB = 20L;
    private static final long CAROL = 30L;
    private static final Instant START = Instant.parse("2026-11-12T12:00:00Z");

    private Database database;
    private MutableClock clock;
    private ProposalService proposalService;
    private BlockService service;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.clock = new MutableClock(START);
        TagService tagService = new TagService(new SqliteTagRepository(this.database),
                new ConditionChecker(new ActivityService(new InMemoryActivityRepository()), this.clock));
        ProfileService profileService = new ProfileService(
                new SqliteProfileRepository(this.database, new TextCipher(new byte[32])), tagService, this.clock);
        this.proposalService = new ProposalService(new SqliteProposalRepository(this.database), profileService,
                new RoomService(new SqliteRoomRepository(this.database), profileService,
                        new GuildConfigService(new SqliteGuildConfigRepository(this.database)), this.clock),
                this.clock);
        this.service = new BlockService(new SqliteBlockRepository(this.database), this.proposalService, this.clock);
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

    private Proposal reload(Proposal proposal) {
        return this.proposalService.findProposal(proposal.id()).orElseThrow();
    }

    @Test
    void blocksAreListedInOrderAndCanBeLifted() {
        assertEquals(BlockService.Outcome.BLOCKED, this.service.block(GUILD, ALICE, CAROL).outcome());
        this.clock.advance(Duration.ofMinutes(1));
        this.service.block(GUILD, ALICE, BOB);

        assertEquals(BlockService.Outcome.ALREADY_BLOCKED, this.service.block(GUILD, ALICE, BOB).outcome());
        assertEquals(List.of(CAROL, BOB), this.service.blockedMembers(GUILD, ALICE));

        assertTrue(this.service.unblock(GUILD, ALICE, CAROL));
        assertFalse(this.service.unblock(GUILD, ALICE, CAROL));
        assertEquals(List.of(BOB), this.service.blockedMembers(GUILD, ALICE));
    }

    @Test
    void aMemberCannotBlockThemself() {
        assertEquals(BlockService.Outcome.SELF, this.service.block(GUILD, ALICE, ALICE).outcome());
        assertTrue(this.service.blockedMembers(GUILD, ALICE).isEmpty());
    }

    @Test
    void aBlockWorksInBothDirectionsOnOneServerOnly() {
        this.service.block(GUILD, ALICE, BOB);

        assertEquals(Set.of(new UserPair(BOB, ALICE)), this.service.blockedPairs(GUILD));
        assertTrue(this.service.blockedPairs(OTHER_GUILD).isEmpty());
    }

    @Test
    void blockingDuringAPendingProposalCountsAsASkip() {
        Proposal proposal = this.propose();

        BlockService.BlockResult result = this.service.block(GUILD, ALICE, BOB);

        assertInstanceOf(ProposalService.Skipped.class, result.withdrawal().orElseThrow());
        assertEquals(ProposalAnswer.SKIPPED, this.reload(proposal).answerOf(ALICE));
        assertEquals(ProposalAnswer.PENDING, this.reload(proposal).answerOf(BOB));
    }

    @Test
    void blockingAfterAcceptingCountsAsACancel() {
        Proposal proposal = this.propose();
        this.proposalService.answer(proposal.id(), ALICE, ProposalService.Action.ACCEPT);

        BlockService.BlockResult result = this.service.block(GUILD, ALICE, BOB);

        assertInstanceOf(ProposalService.Cancelled.class, result.withdrawal().orElseThrow());
        assertEquals(ProposalAnswer.CANCELLED, this.reload(proposal).answerOf(ALICE));
    }
}
