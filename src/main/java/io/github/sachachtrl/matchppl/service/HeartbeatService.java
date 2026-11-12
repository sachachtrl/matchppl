package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.repository.HeartbeatRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class HeartbeatService {
    public static final Duration DOWNTIME_THRESHOLD = Duration.ofMinutes(5);

    private final HeartbeatRepository heartbeatRepo;
    private final ProposalService proposalService;
    private final Clock clock;

    public HeartbeatService(HeartbeatRepository heartbeatRepo, ProposalService proposalService, Clock clock) {
        this.heartbeatRepo = heartbeatRepo;
        this.proposalService = proposalService;
        this.clock = clock;
    }

    public Optional<Duration> downtime() {
        Instant now = this.clock.instant();
        return this.heartbeatRepo.findLast()
                .map(last -> Duration.between(last, now))
                .filter(gap -> gap.compareTo(DOWNTIME_THRESHOLD) > 0);
    }

    public List<Proposal> recoverFromDowntime() {
        List<Proposal> interrupted = this.downtime().isPresent() ? this.proposalService.interruptAllOpen() : List.of();
        this.beat();
        return interrupted;
    }

    public void beat() {
        this.heartbeatRepo.save(this.clock.instant());
    }
}
