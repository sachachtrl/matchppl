package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.repository.RoomRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RoomService {
    public static final Duration REMINDER_NOTICE = Duration.ofHours(1);

    private final RoomRepository roomRepo;
    private final ProfileService profileService;
    private final GuildConfigService guildConfigService;
    private final Clock clock;

    public RoomService(RoomRepository roomRepo, ProfileService profileService, GuildConfigService guildConfigService,
            Clock clock) {
        this.roomRepo = roomRepo;
        this.profileService = profileService;
        this.guildConfigService = guildConfigService;
        this.clock = clock;
    }

    public synchronized Optional<Room> openIfWanted(Proposal proposal) {
        long guildId = proposal.guildId();
        if (!this.guildConfigService.settings(guildId).roomsEnabled()
                || !this.profileService.getProfile(guildId, proposal.firstUserId()).allowRooms()
                || !this.profileService.getProfile(guildId, proposal.secondUserId()).allowRooms()) {
            return Optional.empty();
        }
        Set<Integer> used = this.roomRepo.findOpenNumbers(guildId);
        int number = 1;
        while (used.contains(number)) {
            number++;
        }
        Instant now = this.clock.instant();
        return Optional.of(this.roomRepo.create(guildId, proposal.id(), proposal.firstUserId(),
                proposal.secondUserId(), number, now, now.plus(this.guildConfigService.roomLifetime(guildId))));
    }

    public void attachChannels(long roomId, long textChannelId, long voiceChannelId) {
        this.roomRepo.setChannels(roomId, textChannelId, voiceChannelId);
    }

    public Optional<Room> findRoom(long roomId) {
        return this.roomRepo.findById(roomId);
    }

    public Optional<Room> findByProposal(long proposalId) {
        return this.roomRepo.findByProposal(proposalId);
    }

    public Set<Long> busyUsers(long guildId) {
        return this.roomRepo.findBusyUsers(guildId);
    }

    public synchronized boolean close(long roomId) {
        Optional<Room> room = this.roomRepo.findById(roomId);
        if (room.isEmpty() || !this.roomRepo.close(roomId)) {
            return false;
        }
        this.releaseMembers(room.get());
        return true;
    }

    public synchronized Optional<Room> endNow(long roomId, long userId) {
        Optional<Room> room = this.roomRepo.findById(roomId).filter(found -> found.involves(userId));
        return room.isPresent() && this.close(roomId) ? room : Optional.empty();
    }

    public synchronized Optional<Room> closeByChannel(long channelId) {
        Optional<Room> room = this.roomRepo.findOpenByChannel(channelId);
        return room.isPresent() && this.close(room.get().id()) ? room : Optional.empty();
    }

    public synchronized List<Room> closeAllFor(long guildId, long userId) {
        List<Room> closed = new ArrayList<>();
        for (Room room : this.roomRepo.findOpenInvolving(guildId, userId)) {
            if (this.close(room.id())) {
                closed.add(room);
            }
        }
        return closed;
    }

    public synchronized List<Room> closeDue() {
        List<Room> closed = new ArrayList<>();
        for (Room room : this.roomRepo.findDue(this.clock.instant())) {
            if (this.close(room.id())) {
                closed.add(room);
            }
        }
        return closed;
    }

    public synchronized List<Room> remindersDue() {
        return this.roomRepo.findReminderDue(this.clock.instant(), REMINDER_NOTICE).stream()
                .filter(room -> this.roomRepo.markReminderSent(room.id()))
                .toList();
    }

    private void releaseMembers(Room room) {
        this.profileService.restartWaiting(room.guildId(), room.firstUserId());
        this.profileService.restartWaiting(room.guildId(), room.secondUserId());
    }
}
