package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.InMemoryActivityRepository;
import io.github.sachachtrl.matchppl.repository.SqliteGuildConfigRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProfileRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProposalRepository;
import io.github.sachachtrl.matchppl.repository.SqliteTagRepository;
import io.github.sachachtrl.matchppl.repository.TextCipher;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchmakingServiceTest {
    private static final long GUILD = 1L;
    private static final long ALICE = 10L;
    private static final long BOB = 20L;
    private static final long CAROL = 30L;
    private static final Instant START = Instant.parse("2026-11-11T12:00:00Z");

    private Database database;
    private MutableClock clock;
    private ProfileService profileService;
    private ProposalService proposalService;
    private MatchmakingService service;
    private Tag tag;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.clock = new MutableClock(START);
        ConditionChecker checker = new ConditionChecker(new ActivityService(new InMemoryActivityRepository()),
                this.clock);
        TagService tagService = new TagService(new SqliteTagRepository(this.database), checker);
        this.profileService = new ProfileService(
                new SqliteProfileRepository(this.database, new TextCipher(new byte[32])), tagService, this.clock);
        this.proposalService = new ProposalService(new SqliteProposalRepository(this.database), this.profileService,
                this.clock);
        GuildConfigService guildConfigService = new GuildConfigService(new SqliteGuildConfigRepository(this.database));
        this.service = new MatchmakingService(this.profileService, tagService,
                new MatchingService(checker, new Random(1)), this.proposalService, guildConfigService, this.clock);
        this.tag = ((TagService.Created) tagService.createTag(GUILD, "Fortnite", "🎮")).tag();
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    private static MemberSnapshot member(long userId) {
        return new MemberSnapshot(GUILD, userId, START.minus(Duration.ofDays(365)), START.minus(Duration.ofDays(30)),
                Set.of(), true);
    }

    private void startSearching(long userId) {
        this.profileService.setTags(member(userId), Set.of(this.tag.id()));
        this.profileService.setSearching(GUILD, userId, true);
    }

    private List<Proposal> round(Set<Long> presentMembers) {
        return this.service.runRound(GUILD,
                userId -> presentMembers.contains(userId) ? Optional.of(member(userId)) : Optional.empty());
    }

    @Test
    void aRoundProposesFreeSearchingMembers() {
        this.startSearching(ALICE);
        this.startSearching(BOB);

        List<Proposal> created = this.round(Set.of(ALICE, BOB));

        assertEquals(1, created.size());
        assertEquals(START.plus(GuildConfigService.DEFAULT_RESPONSE_DELAY), created.getFirst().deadline());
    }

    @Test
    void busyMembersAreLeftOutOfTheNextRound() {
        this.startSearching(ALICE);
        this.startSearching(BOB);
        this.round(Set.of(ALICE, BOB));
        this.startSearching(CAROL);
        this.clock.advance(Duration.ofMinutes(1));

        assertTrue(this.round(Set.of(ALICE, BOB, CAROL)).isEmpty());
    }

    @Test
    void membersNoLongerOnTheServerAreIgnored() {
        this.startSearching(ALICE);
        this.startSearching(BOB);

        assertTrue(this.round(Set.of(ALICE)).isEmpty());
    }

    @Test
    void roundsDoNotRunMoreOftenThanTheMatchingInterval() {
        this.startSearching(ALICE);
        this.round(Set.of(ALICE));
        this.startSearching(BOB);

        this.clock.advance(Duration.ofSeconds(20));
        assertTrue(this.round(Set.of(ALICE, BOB)).isEmpty());

        this.clock.advance(Duration.ofSeconds(40));
        assertEquals(1, this.round(Set.of(ALICE, BOB)).size());
    }

    @Test
    void aPairThatEndedWithoutMatchIsNeverProposedAgain() {
        this.startSearching(ALICE);
        this.startSearching(BOB);
        Proposal proposal = this.round(Set.of(ALICE, BOB)).getFirst();
        this.proposalService.answer(proposal.id(), ALICE, ProposalService.Action.SKIP);

        this.clock.advance(GuildConfigService.DEFAULT_RESPONSE_DELAY);
        this.proposalService.expireDue();

        assertTrue(this.round(Set.of(ALICE, BOB)).isEmpty());
    }

    @Test
    void aSkippedPairIsNotProposedAgainBeforeTheDeadlineWhenBothAreFree() {
        this.startSearching(ALICE);
        this.startSearching(BOB);
        Proposal proposal = this.round(Set.of(ALICE, BOB)).getFirst();
        this.proposalService.answer(proposal.id(), ALICE, ProposalService.Action.SKIP);
        this.proposalService.answer(proposal.id(), BOB, ProposalService.Action.ACCEPT);
        this.proposalService.answer(proposal.id(), BOB, ProposalService.Action.CANCEL);
        this.clock.advance(Duration.ofMinutes(1));

        assertTrue(this.round(Set.of(ALICE, BOB)).isEmpty());
    }
}
