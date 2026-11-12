package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.ProposalAnswer;
import io.github.sachachtrl.matchppl.model.UserPair;
import io.github.sachachtrl.matchppl.repository.BlockRepository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class BlockService {
    public enum Outcome {
        BLOCKED,
        ALREADY_BLOCKED,
        SELF
    }

    public record BlockResult(Outcome outcome, Optional<ProposalService.AnswerResult> withdrawal) {}

    private final BlockRepository blockRepo;
    private final ProposalService proposalService;
    private final Clock clock;

    public BlockService(BlockRepository blockRepo, ProposalService proposalService, Clock clock) {
        this.blockRepo = blockRepo;
        this.proposalService = proposalService;
        this.clock = clock;
    }

    public synchronized BlockResult block(long guildId, long blockerId, long blockedId) {
        if (blockerId == blockedId) {
            return new BlockResult(Outcome.SELF, Optional.empty());
        }
        if (!this.blockRepo.block(guildId, blockerId, blockedId, this.clock.instant())) {
            return new BlockResult(Outcome.ALREADY_BLOCKED, Optional.empty());
        }
        Optional<ProposalService.AnswerResult> withdrawal = this.proposalService.openProposals(guildId).stream()
                .filter(proposal -> proposal.involves(blockerId) && proposal.involves(blockedId))
                .findFirst()
                .map(proposal -> this.withdraw(proposal, blockerId))
                .filter(result -> !(result instanceof ProposalService.Ignored));
        return new BlockResult(Outcome.BLOCKED, withdrawal);
    }

    public boolean unblock(long guildId, long blockerId, long blockedId) {
        return this.blockRepo.unblock(guildId, blockerId, blockedId);
    }

    public List<Long> blockedMembers(long guildId, long blockerId) {
        return this.blockRepo.findBlocked(guildId, blockerId);
    }

    public Set<UserPair> blockedPairs(long guildId) {
        return this.blockRepo.findBlockedPairs(guildId);
    }

    public void forgetBlocker(long guildId, long blockerId) {
        this.blockRepo.deleteBlocksBy(guildId, blockerId);
    }

    private ProposalService.AnswerResult withdraw(Proposal proposal, long blockerId) {
        ProposalService.Action action = proposal.answerOf(blockerId) == ProposalAnswer.ACCEPTED
                ? ProposalService.Action.CANCEL
                : ProposalService.Action.SKIP;
        return this.proposalService.answer(proposal.id(), blockerId, action);
    }
}
