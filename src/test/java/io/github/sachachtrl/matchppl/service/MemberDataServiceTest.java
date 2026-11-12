package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MatchPair;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.ProposalStatus;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.InMemoryActivityRepository;
import io.github.sachachtrl.matchppl.repository.SqliteGuildConfigRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProfileRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProposalRepository;
import io.github.sachachtrl.matchppl.repository.SqliteRoomRepository;
import io.github.sachachtrl.matchppl.repository.SqliteTagRepository;
import io.github.sachachtrl.matchppl.repository.TextCipher;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberDataServiceTest {
    private static final long GUILD = 1L;
    private static final long ALICE = 10L;
    private static final long BOB = 20L;
    private static final Instant START = Instant.parse("2026-11-12T12:00:00Z");

    private Database database;
    private ActivityService activityService;
    private ProfileService profileService;
    private ProposalService proposalService;
    private RoomService roomService;
    private MemberDataService service;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        MutableClock clock = new MutableClock(START);
        this.activityService = new ActivityService(new InMemoryActivityRepository());
        TagService tagService = new TagService(new SqliteTagRepository(this.database),
                new ConditionChecker(this.activityService, clock));
        this.profileService = new ProfileService(
                new SqliteProfileRepository(this.database, new TextCipher(new byte[32])), tagService, clock);
        this.roomService = new RoomService(new SqliteRoomRepository(this.database), this.profileService,
                new GuildConfigService(new SqliteGuildConfigRepository(this.database)), clock);
        this.proposalService = new ProposalService(new SqliteProposalRepository(this.database), this.profileService,
                this.roomService, clock);
        this.service = new MemberDataService(this.profileService, this.activityService, this.proposalService,
                this.roomService);
        Tag tag = ((TagService.Created) tagService.createTag(GUILD, "Fortnite", "🎮")).tag();
        for (long user : List.of(ALICE, BOB)) {
            this.profileService.setTags(new MemberSnapshot(GUILD, user, START, START, Set.of(), true),
                    Set.of(tag.id()));
            this.profileService.setSearching(GUILD, user, true);
            this.activityService.recordMessage(GUILD, user, START);
        }
        this.profileService.setPresentation(GUILD, ALICE, "Hello");
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    private Proposal propose() {
        return this.proposalService.createProposals(GUILD, List.of(new MatchPair(ALICE, BOB, 1, 1)),
                Duration.ofHours(24)).getFirst();
    }

    private Proposal match() {
        Proposal proposal = this.propose();
        this.proposalService.answer(proposal.id(), ALICE, ProposalService.Action.ACCEPT);
        this.proposalService.answer(proposal.id(), BOB, ProposalService.Action.ACCEPT);
        return proposal;
    }

    @Test
    void aRequestDeletesTheProfileAndActivityAndInterruptsProposals() {
        Proposal proposal = this.propose();

        MemberDataService.Forgotten forgotten = this.service.deleteOnRequest(GUILD, ALICE);

        assertEquals(1, forgotten.interruptedProposals().size());
        assertEquals(ProposalStatus.INTERRUPTED,
                this.proposalService.findProposal(proposal.id()).orElseThrow().status());
        assertEquals(Profile.empty(GUILD, ALICE), this.profileService.getProfile(GUILD, ALICE));
        assertEquals(0, this.activityService.countMessages(GUILD, ALICE, ActivityPeriod.ALL_TIME,
                LocalDate.of(2026, 11, 12)));
        assertEquals(1, this.activityService.countMessages(GUILD, BOB, ActivityPeriod.ALL_TIME,
                LocalDate.of(2026, 11, 12)));
    }

    @Test
    void aRequestKeepsAnOpenChannel() {
        this.match();

        MemberDataService.Forgotten forgotten = this.service.deleteOnRequest(GUILD, ALICE);

        assertTrue(forgotten.closedRooms().isEmpty());
        assertEquals(Set.of(ALICE, BOB), this.roomService.busyUsers(GUILD));
    }

    @Test
    void leavingTheServerAlsoClosesTheChannel() {
        this.match();

        MemberDataService.Forgotten forgotten = this.service.forgetLeavingMember(GUILD, ALICE);

        assertEquals(1, forgotten.closedRooms().size());
        assertTrue(this.roomService.busyUsers(GUILD).isEmpty());
        assertEquals(Profile.empty(GUILD, ALICE), this.profileService.getProfile(GUILD, ALICE));
    }
}
