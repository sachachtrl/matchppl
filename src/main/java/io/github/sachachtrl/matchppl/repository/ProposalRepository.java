package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.ProposalAnswer;
import io.github.sachachtrl.matchppl.model.ProposalStatus;
import io.github.sachachtrl.matchppl.model.UserPair;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProposalRepository {
    Proposal create(long guildId, long firstUserId, long secondUserId, Instant createdAt, Instant deadline);

    Optional<Proposal> findById(long proposalId);

    Set<Long> findBusyUsers(long guildId);

    Set<UserPair> findBlockedPairs(long guildId);

    int countBlockedPairs(long guildId);

    int countBlockedPairsInvolving(long guildId, long userId);

    void unblockAll(long guildId);

    void unblockInvolving(long guildId, long userId);

    List<Proposal> findExpired(Instant now);

    List<Proposal> findOpen();

    List<Proposal> findOpenInvolving(long guildId, long userId);

    boolean updateAnswer(long proposalId, long userId, ProposalAnswer expected, ProposalAnswer next);

    boolean markMatched(long proposalId);

    boolean close(long proposalId, ProposalStatus status);

    void setMessageId(long proposalId, long userId, long messageId);
}
