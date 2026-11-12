package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.MatchPair;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.ProposalAnswer;
import io.github.sachachtrl.matchppl.model.ProposalStatus;
import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.model.UserPair;
import io.github.sachachtrl.matchppl.repository.ProposalRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ProposalService {
    public enum Action {
        ACCEPT,
        SKIP,
        CANCEL
    }

    public sealed interface AnswerResult permits Accepted, Matched, Skipped, Cancelled, Ignored {}

    public record Accepted(Proposal proposal) implements AnswerResult {}

    public record Matched(Proposal proposal, Optional<Room> room) implements AnswerResult {}

    public record Skipped(Proposal proposal) implements AnswerResult {}

    public record Cancelled(Proposal proposal) implements AnswerResult {}

    public record Ignored() implements AnswerResult {}

    private final ProposalRepository proposalRepo;
    private final ProfileService profileService;
    private final RoomService roomService;
    private final Clock clock;

    public ProposalService(ProposalRepository proposalRepo, ProfileService profileService, RoomService roomService,
            Clock clock) {
        this.proposalRepo = proposalRepo;
        this.profileService = profileService;
        this.roomService = roomService;
        this.clock = clock;
    }

    public synchronized List<Proposal> createProposals(long guildId, List<MatchPair> pairs, Duration responseDelay) {
        Instant now = this.clock.instant();
        List<Proposal> created = new ArrayList<>();
        Set<Long> busy = this.busyUsers(guildId);
        for (MatchPair pair : pairs) {
            if (busy.contains(pair.firstUserId()) || busy.contains(pair.secondUserId())) {
                continue;
            }
            created.add(this.proposalRepo.create(guildId, pair.firstUserId(), pair.secondUserId(), now,
                    now.plus(responseDelay)));
            busy.add(pair.firstUserId());
            busy.add(pair.secondUserId());
        }
        return created;
    }

    public synchronized AnswerResult answer(long proposalId, long userId, Action action) {
        Optional<Proposal> found = this.proposalRepo.findById(proposalId);
        if (found.isEmpty() || !found.get().involves(userId)) {
            return new Ignored();
        }
        Proposal proposal = found.get();
        return switch (action) {
            case ACCEPT -> this.accept(proposal, userId);
            case SKIP -> this.leave(proposal, userId, ProposalAnswer.PENDING, ProposalAnswer.SKIPPED);
            case CANCEL -> this.leave(proposal, userId, ProposalAnswer.ACCEPTED, ProposalAnswer.CANCELLED);
        };
    }

    public synchronized List<Proposal> expireDue() {
        List<Proposal> expired = new ArrayList<>();
        for (Proposal proposal : this.proposalRepo.findExpired(this.clock.instant())) {
            if (this.proposalRepo.close(proposal.id(), ProposalStatus.NO_MATCH)) {
                this.releaseMembers(proposal);
                expired.add(proposal);
            }
        }
        return expired;
    }

    public synchronized boolean interrupt(long proposalId) {
        Optional<Proposal> proposal = this.proposalRepo.findById(proposalId);
        if (proposal.isEmpty() || !this.proposalRepo.close(proposalId, ProposalStatus.INTERRUPTED)) {
            return false;
        }
        this.releaseMembers(proposal.get());
        return true;
    }

    public synchronized List<Proposal> interruptAllFor(long guildId, long userId) {
        List<Proposal> interrupted = new ArrayList<>();
        for (Proposal proposal : this.proposalRepo.findOpenInvolving(guildId, userId)) {
            if (this.interrupt(proposal.id())) {
                interrupted.add(proposal);
            }
        }
        return interrupted;
    }

    public synchronized List<Proposal> interruptAllOpen() {
        List<Proposal> interrupted = new ArrayList<>();
        for (Proposal proposal : this.proposalRepo.findOpen()) {
            if (this.interrupt(proposal.id())) {
                interrupted.add(proposal);
            }
        }
        return interrupted;
    }

    public Optional<Proposal> findProposal(long proposalId) {
        return this.proposalRepo.findById(proposalId);
    }

    public Set<Long> busyUsers(long guildId) {
        Set<Long> busy = new HashSet<>(this.proposalRepo.findBusyUsers(guildId));
        busy.addAll(this.roomService.busyUsers(guildId));
        return busy;
    }

    public Set<UserPair> blockedPairs(long guildId) {
        return this.proposalRepo.findBlockedPairs(guildId);
    }

    public int countBlockedPairs(long guildId) {
        return this.proposalRepo.countBlockedPairs(guildId);
    }

    public int countBlockedPairs(long guildId, long userId) {
        return this.proposalRepo.countBlockedPairsInvolving(guildId, userId);
    }

    public synchronized int resetAllBlocks(long guildId) {
        int count = this.proposalRepo.countBlockedPairs(guildId);
        this.proposalRepo.unblockAll(guildId);
        return count;
    }

    public synchronized int resetBlocks(long guildId, long userId) {
        int count = this.proposalRepo.countBlockedPairsInvolving(guildId, userId);
        this.proposalRepo.unblockInvolving(guildId, userId);
        return count;
    }

    public boolean isBlocked(long guildId, long firstUserId, long secondUserId) {
        return this.proposalRepo.findBlockedPairs(guildId).contains(new UserPair(firstUserId, secondUserId));
    }

    public synchronized boolean resetPair(long guildId, long firstUserId, long secondUserId) {
        return firstUserId != secondUserId && this.proposalRepo.unblockPair(guildId, firstUserId, secondUserId);
    }

    public void recordMessage(long proposalId, long userId, long messageId) {
        this.proposalRepo.setMessageId(proposalId, userId, messageId);
    }

    private AnswerResult accept(Proposal proposal, long userId) {
        if (!this.proposalRepo.updateAnswer(proposal.id(), userId, ProposalAnswer.PENDING, ProposalAnswer.ACCEPTED)) {
            return new Ignored();
        }
        Proposal updated = this.proposalRepo.findById(proposal.id()).orElseThrow();
        if (this.proposalRepo.markMatched(proposal.id())) {
            Proposal matched = this.proposalRepo.findById(proposal.id()).orElseThrow();
            this.releaseMembers(matched);
            return new Matched(matched, this.roomService.openIfWanted(matched));
        }
        return new Accepted(updated);
    }

    private AnswerResult leave(Proposal proposal, long userId, ProposalAnswer expected, ProposalAnswer next) {
        if (!this.proposalRepo.updateAnswer(proposal.id(), userId, expected, next)) {
            return new Ignored();
        }
        this.profileService.restartWaiting(proposal.guildId(), userId);
        Proposal updated = this.proposalRepo.findById(proposal.id()).orElseThrow();
        return next == ProposalAnswer.SKIPPED ? new Skipped(updated) : new Cancelled(updated);
    }

    private void releaseMembers(Proposal proposal) {
        for (long userId : List.of(proposal.firstUserId(), proposal.secondUserId())) {
            if (proposal.answerOf(userId).keepsMemberBusy()) {
                this.profileService.restartWaiting(proposal.guildId(), userId);
            }
        }
    }
}
