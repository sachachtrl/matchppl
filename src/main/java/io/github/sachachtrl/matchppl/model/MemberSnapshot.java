package io.github.sachachtrl.matchppl.model;

import java.time.Instant;
import java.util.Set;

public record MemberSnapshot(
        long guildId,
        long userId,
        Instant accountCreatedAt,
        Instant joinedAt,
        Set<Long> roleIds,
        boolean customAvatar) {

    public MemberSnapshot {
        roleIds = Set.copyOf(roleIds);
    }
}
