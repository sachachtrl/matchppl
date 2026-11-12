package io.github.sachachtrl.matchppl.model;

import java.time.Instant;

public record Room(
        long id,
        long guildId,
        long proposalId,
        long firstUserId,
        long secondUserId,
        int number,
        Long textChannelId,
        Long voiceChannelId,
        Instant createdAt,
        Instant deleteAt,
        boolean open) {

    public boolean involves(long userId) {
        return userId == this.firstUserId || userId == this.secondUserId;
    }

    public long otherUserId(long userId) {
        return userId == this.firstUserId ? this.secondUserId : this.firstUserId;
    }
}
