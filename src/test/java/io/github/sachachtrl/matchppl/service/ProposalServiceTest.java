package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.MatchPair;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.ProposalAnswer;
import io.github.sachachtrl.matchppl.model.ProposalStatus;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.model.UserPair;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.InMemoryActivityRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProfileRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProposalRepository;
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

class ProposalServiceTest {
    private static final long GUILD = 1L;
    private static final long ALICE = 10L;
    private static final long BOB = 20L;
    private static final long CAROL = 30L;
    private static final Duration DELAY = Duration.ofHours(24);
    private static final Instant START = Instant.parse("2026-11-11T12:00:00Z");

    private Database database;
    private MutableClock clock;
    private ProfileService profileService;
    private ProposalService service;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.clock = new MutableClock(START);
        ConditionChecker checker = new ConditionChecker(new ActivityService(new InMemoryActivityRepository()),
                this.clock);
        TagService tagService = new TagService(new SqliteTagRepository(this.database), checker);
        this.profileService = new ProfileService(
                new SqliteProfileRepository(this.database, new TextCipher(new byte[32])), tagService, this.clock);
        this.service = new ProposalService(new SqliteProposalRepository(this.database), this.profileService,
                this.clock);
        Tag tag = ((TagService.Created) tagService.createTag(GUILD, "Fortnite", "🎮")).tag();
        for (long user : List.of(ALICE, BOB, CAROL)) {
            this.profileService.setTags(new MemberSnapshot(GUILD, user, START, START, Set.of(), true),
                    Set.of(tag.id()));
            this.profileService.setSearching(GUILD, user, true);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    private Proposal propose(long first, long second) {
        return this.service.createProposals(GUILD, List.of(new MatchPair(first, second, 1, 1)), DELAY).getFirst();
    }

    private Proposal reload(Proposal proposal) {
        return this.service.findProposal(proposal.id()).orElseThrow();
    }

    @Test
    void newProposalIsOpenUntilItsDeadlineAndKeepsBothMembersBusy() {
        Proposal proposal = this.propose(ALICE, BOB);

        assertEquals(ProposalStatus.OPEN, proposal.status());
        assertEquals(START.plus(DELAY), proposal.deadline());
        assertEquals(Set.of(ALICE, BOB), this.service.busyUsers(GUILD));
    }

    @Test
    void pairsWithABusyMemberAreNotCreated() {
        this.propose(ALICE, BOB);

        List<Proposal> created = this.service.createProposals(GUILD, List.of(new MatchPair(BOB, CAROL, 1, 1)), DELAY);

        assertTrue(created.isEmpty());
    }

    @Test
    void skippingFreesOnlyTheMemberWhoSkipped() {
        Proposal proposal = this.propose(ALICE, BOB);

        assertInstanceOf(ProposalService.Skipped.class, this.service.answer(proposal.id(), ALICE,
                ProposalService.Action.SKIP));

        Proposal updated = this.reload(proposal);
        assertEquals(ProposalStatus.OPEN, updated.status());
        assertEquals(ProposalAnswer.PENDING, updated.answerOf(BOB));
        assertEquals(Set.of(BOB), this.service.busyUsers(GUILD));
    }

    @Test
    void acceptingKeepsTheMemberBusyUntilTheDeadline() {
        Proposal proposal = this.propose(ALICE, BOB);

        assertInstanceOf(ProposalService.Accepted.class, this.service.answer(proposal.id(), ALICE,
                ProposalService.Action.ACCEPT));

        assertTrue(this.service.busyUsers(GUILD).contains(ALICE));
    }

    @Test
    void bothAcceptingCreatesAMatch() {
        Proposal proposal = this.propose(ALICE, BOB);
        this.service.answer(proposal.id(), ALICE, ProposalService.Action.ACCEPT);

        ProposalService.AnswerResult result = this.service.answer(proposal.id(), BOB, ProposalService.Action.ACCEPT);

        assertInstanceOf(ProposalService.Matched.class, result);
        assertEquals(ProposalStatus.MATCHED, this.reload(proposal).status());
    }

    @Test
    void cancellingAfterAcceptingFreesTheMember() {
        Proposal proposal = this.propose(ALICE, BOB);
        this.service.answer(proposal.id(), ALICE, ProposalService.Action.ACCEPT);

        assertInstanceOf(ProposalService.Cancelled.class, this.service.answer(proposal.id(), ALICE,
                ProposalService.Action.CANCEL));

        assertEquals(ProposalAnswer.CANCELLED, this.reload(proposal).answerOf(ALICE));
        assertFalse(this.service.busyUsers(GUILD).contains(ALICE));
    }

    @Test
    void cancellingWithoutAcceptingIsIgnored() {
        Proposal proposal = this.propose(ALICE, BOB);

        assertInstanceOf(ProposalService.Ignored.class, this.service.answer(proposal.id(), ALICE,
                ProposalService.Action.CANCEL));
    }

    @Test
    void cancellationArrivingAfterTheMatchIsIgnored() {
        Proposal proposal = this.propose(ALICE, BOB);
        this.service.answer(proposal.id(), ALICE, ProposalService.Action.ACCEPT);
        this.service.answer(proposal.id(), BOB, ProposalService.Action.ACCEPT);

        assertInstanceOf(ProposalService.Ignored.class, this.service.answer(proposal.id(), ALICE,
                ProposalService.Action.CANCEL));
        assertEquals(ProposalStatus.MATCHED, this.reload(proposal).status());
    }

    @Test
    void answeringTwiceIsIgnored() {
        Proposal proposal = this.propose(ALICE, BOB);
        this.service.answer(proposal.id(), ALICE, ProposalService.Action.SKIP);

        assertInstanceOf(ProposalService.Ignored.class, this.service.answer(proposal.id(), ALICE,
                ProposalService.Action.ACCEPT));
    }

    @Test
    void outsidersCannotAnswer() {
        Proposal proposal = this.propose(ALICE, BOB);

        assertInstanceOf(ProposalService.Ignored.class, this.service.answer(proposal.id(), CAROL,
                ProposalService.Action.ACCEPT));
    }

    @Test
    void expiredProposalsBlockThePairAndFreeTheMembers() {
        Proposal proposal = this.propose(ALICE, BOB);
        this.service.answer(proposal.id(), ALICE, ProposalService.Action.ACCEPT);
        assertTrue(this.service.expireDue().isEmpty());

        this.clock.advance(DELAY);

        assertEquals(List.of(proposal.id()), this.service.expireDue().stream().map(Proposal::id).toList());
        assertEquals(ProposalStatus.NO_MATCH, this.reload(proposal).status());
        assertEquals(Set.of(new UserPair(ALICE, BOB)), this.service.blockedPairs(GUILD));
        assertTrue(this.service.busyUsers(GUILD).isEmpty());
    }

    @Test
    void matchedProposalsDoNotExpire() {
        Proposal proposal = this.propose(ALICE, BOB);
        this.service.answer(proposal.id(), ALICE, ProposalService.Action.ACCEPT);
        this.service.answer(proposal.id(), BOB, ProposalService.Action.ACCEPT);
        this.clock.advance(DELAY);

        assertTrue(this.service.expireDue().isEmpty());
        assertTrue(this.service.blockedPairs(GUILD).isEmpty());
    }

    @Test
    void interruptedProposalsDoNotBlockThePair() {
        Proposal proposal = this.propose(ALICE, BOB);

        assertTrue(this.service.interrupt(proposal.id()));

        assertEquals(ProposalStatus.INTERRUPTED, this.reload(proposal).status());
        assertTrue(this.service.blockedPairs(GUILD).isEmpty());
        assertTrue(this.service.busyUsers(GUILD).isEmpty());
    }

    @Test
    void leavingTheServerInterruptsOnlyTheMembersOpenProposals() {
        Proposal withBob = this.propose(ALICE, BOB);

        List<Proposal> interrupted = this.service.interruptAllFor(GUILD, BOB);

        assertEquals(List.of(withBob.id()), interrupted.stream().map(Proposal::id).toList());
        assertTrue(this.service.interruptAllFor(GUILD, CAROL).isEmpty());
    }

    @Test
    void becomingFreeAgainRestartsTheWait() {
        Proposal proposal = this.propose(ALICE, BOB);
        this.clock.advance(Duration.ofHours(3));

        this.service.answer(proposal.id(), ALICE, ProposalService.Action.SKIP);

        assertEquals(START.plus(Duration.ofHours(3)), this.profileService.getProfile(GUILD, ALICE).waitingSince());
        assertEquals(START, this.profileService.getProfile(GUILD, BOB).waitingSince());
    }

    @Test
    void messageIdsAreRecordedForEachMember() {
        Proposal proposal = this.propose(ALICE, BOB);

        this.service.recordMessage(proposal.id(), ALICE, 111L);
        this.service.recordMessage(proposal.id(), BOB, 222L);

        Proposal updated = this.reload(proposal);
        assertEquals(111L, updated.messageIdOf(ALICE));
        assertEquals(222L, updated.messageIdOf(BOB));
    }
}
