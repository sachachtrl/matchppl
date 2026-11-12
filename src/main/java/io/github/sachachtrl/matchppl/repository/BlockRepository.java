package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.UserPair;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface BlockRepository {
    boolean block(long guildId, long blockerId, long blockedId, Instant createdAt);

    boolean unblock(long guildId, long blockerId, long blockedId);

    boolean isBlocked(long guildId, long firstUserId, long secondUserId);

    List<Long> findBlocked(long guildId, long blockerId);

    Set<UserPair> findBlockedPairs(long guildId);

    void deleteBlocksBy(long guildId, long blockerId);
}
