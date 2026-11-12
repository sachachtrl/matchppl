package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.Room;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoomRepository {
    Room create(long guildId, long proposalId, long firstUserId, long secondUserId, int number, Instant createdAt,
            Instant deleteAt);

    Optional<Room> findById(long roomId);

    Optional<Room> findByProposal(long proposalId);

    Optional<Room> findOpenByChannel(long channelId);

    List<Room> findOpenInvolving(long guildId, long userId);

    Set<Integer> findOpenNumbers(long guildId);

    Set<Long> findBusyUsers(long guildId);

    List<Room> findDue(Instant now);

    List<Room> findReminderDue(Instant now, Duration notice);

    void setChannels(long roomId, long textChannelId, long voiceChannelId);

    boolean markReminderSent(long roomId);

    boolean close(long roomId);
}
