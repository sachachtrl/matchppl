package io.github.sachachtrl.matchppl.model;

import java.time.Instant;

public record Proposal(
        long id,
        long guildId,
        long firstUserId,
        long secondUserId,
        Instant createdAt,
        Instant deadline,
        ProposalAnswer firstAnswer,
        ProposalAnswer secondAnswer,
        ProposalStatus status,
        Long firstMessageId,
        Long secondMessageId) {

    public boolean involves(long userId) {
        return userId == this.firstUserId || userId == this.secondUserId;
    }

    public long otherUserId(long userId) {
        return userId == this.firstUserId ? this.secondUserId : this.firstUserId;
    }

    public ProposalAnswer answerOf(long userId) {
        return userId == this.firstUserId ? this.firstAnswer : this.secondAnswer;
    }

    public Long messageIdOf(long userId) {
        return userId == this.firstUserId ? this.firstMessageId : this.secondMessageId;
    }

    public UserPair users() {
        return new UserPair(this.firstUserId, this.secondUserId);
    }
}
