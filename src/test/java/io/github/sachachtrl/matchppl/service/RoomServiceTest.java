package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.GuildSettings;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.ProposalAnswer;
import io.github.sachachtrl.matchppl.model.ProposalStatus;
import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.InMemoryActivityRepository;
import io.github.sachachtrl.matchppl.repository.SqliteGuildConfigRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProfileRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomServiceTest {
    private static final long GUILD = 1L;
    private static final long ALICE = 10L;
    private static final long BOB = 20L;
    private static final long CAROL = 30L;
    private static final long DAVE = 40L;
    private static final long EVE = 50L;
    private static final Instant START = Instant.parse("2026-11-11T12:00:00Z");

    private Database database;
    private MutableClock clock;
    private ProfileService profileService;
    private GuildConfigService guildConfigService;
    private RoomService service;
    private long nextProposalId = 1;

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
        this.clock = new MutableClock(START);
        ConditionChecker checker = new ConditionChecker(new ActivityService(new InMemoryActivityRepository()),
                this.clock);
        TagService tagService = new TagService(new SqliteTagRepository(this.database), checker);
        this.profileService = new ProfileService(
                new SqliteProfileRepository(this.database, new TextCipher(new byte[32])), tagService, this.clock);
        this.guildConfigService = new GuildConfigService(new SqliteGuildConfigRepository(this.database));
        this.service = new RoomService(new SqliteRoomRepository(this.database), this.profileService,
                this.guildConfigService, this.clock);
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    private Proposal match(long first, long second) {
        return new Proposal(this.nextProposalId++, GUILD, first, second, START, START.plus(Duration.ofDays(1)),
                ProposalAnswer.ACCEPTED, ProposalAnswer.ACCEPTED, ProposalStatus.MATCHED, null, null);
    }

    private Room open(long first, long second) {
        return this.service.openIfWanted(this.match(first, second)).orElseThrow();
    }

    @Test
    void aMatchOpensARoomThatKeepsBothMembersBusyUntilItsEnd() {
        Room room = this.open(ALICE, BOB);

        assertEquals(1, room.number());
        assertEquals(START.plus(GuildSettings.DEFAULT_ROOM_LIFETIME), room.deleteAt());
        assertEquals(Set.of(ALICE, BOB), this.service.busyUsers(GUILD));
    }

    @Test
    void noRoomIsOpenedWhenTheServerOrOneMemberDisabledThem() {
        this.profileService.setAllowRooms(GUILD, BOB, false);
        assertTrue(this.service.openIfWanted(this.match(ALICE, BOB)).isEmpty());

        this.guildConfigService.setRoomsEnabled(GUILD, false);
        assertTrue(this.service.openIfWanted(this.match(CAROL, DAVE)).isEmpty());

        assertTrue(this.service.busyUsers(GUILD).isEmpty());
    }

    @Test
    void roomsTakeTheSmallestFreeNumber() {
        Room first = this.open(ALICE, BOB);
        this.open(CAROL, DAVE);
        this.service.close(first.id());

        assertEquals(1, this.open(EVE, ALICE).number());
    }

    @Test
    void roomsLeftWithoutChannelsAfterARestartAreClosed() {
        Room unfinished = this.open(ALICE, BOB);
        Room finished = this.open(CAROL, DAVE);
        this.service.attachChannels(finished.id(), 100L, 101L);

        List<Room> closed = this.service.closeUnfinished();

        assertEquals(List.of(unfinished.id()), closed.stream().map(Room::id).toList());
        assertEquals(Set.of(CAROL, DAVE), this.service.busyUsers(GUILD));
    }

    @Test
    void dueRoomsAreClosedOnlyOnce() {
        this.open(ALICE, BOB);

        this.clock.advance(GuildSettings.DEFAULT_ROOM_LIFETIME);

        assertEquals(1, this.service.closeDue().size());
        assertTrue(this.service.closeDue().isEmpty());
        assertTrue(this.service.busyUsers(GUILD).isEmpty());
    }

    @Test
    void theReminderIsSentOnceAnHourBeforeTheEnd() {
        this.open(ALICE, BOB);

        this.clock.advance(GuildSettings.DEFAULT_ROOM_LIFETIME.minus(Duration.ofHours(2)));
        assertTrue(this.service.remindersDue().isEmpty());

        this.clock.advance(Duration.ofMinutes(61));
        assertEquals(1, this.service.remindersDue().size());
        assertTrue(this.service.remindersDue().isEmpty());
    }

    @Test
    void shortRoomsGetNoReminder() {
        this.guildConfigService.setRoomLifetime(GUILD, Duration.ofMinutes(30));
        this.open(ALICE, BOB);

        this.clock.advance(Duration.ofMinutes(10));

        assertTrue(this.service.remindersDue().isEmpty());
    }

    @Test
    void onlyAMemberOfTheRoomCanEndIt() {
        Room room = this.open(ALICE, BOB);

        assertTrue(this.service.endNow(room.id(), CAROL).isEmpty());
        assertTrue(this.service.endNow(room.id(), BOB).isPresent());
        assertTrue(this.service.endNow(room.id(), ALICE).isEmpty());
    }

    @Test
    void deletingAChannelOrLeavingTheServerClosesTheRoom() {
        Room first = this.open(ALICE, BOB);
        this.service.attachChannels(first.id(), 100L, 101L);
        this.open(CAROL, DAVE);

        assertEquals(first.id(), this.service.closeByChannel(101L).orElseThrow().id());
        assertTrue(this.service.closeByChannel(101L).isEmpty());
        assertEquals(1, this.service.closeAllFor(GUILD, DAVE).size());
        assertFalse(this.service.busyUsers(GUILD).contains(CAROL));
    }
}
