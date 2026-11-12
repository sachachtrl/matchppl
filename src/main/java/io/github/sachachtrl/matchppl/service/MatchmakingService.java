package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.MatchCandidate;
import io.github.sachachtrl.matchppl.model.MatchPair;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.model.UserPair;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

public class MatchmakingService {
    private static final Duration SCHEDULING_TOLERANCE = Duration.ofSeconds(5);

    private final ProfileService profileService;
    private final TagService tagService;
    private final MatchingService matchingService;
    private final ProposalService proposalService;
    private final GuildConfigService guildConfigService;
    private final BlockService blockService;
    private final Clock clock;
    private final Map<Long, Instant> lastRuns = new ConcurrentHashMap<>();

    public MatchmakingService(ProfileService profileService, TagService tagService, MatchingService matchingService,
            ProposalService proposalService, GuildConfigService guildConfigService, BlockService blockService,
            Clock clock) {
        this.profileService = profileService;
        this.tagService = tagService;
        this.matchingService = matchingService;
        this.proposalService = proposalService;
        this.guildConfigService = guildConfigService;
        this.blockService = blockService;
        this.clock = clock;
    }

    public List<Proposal> runRound(long guildId, LongFunction<Optional<MemberSnapshot>> members) {
        Instant now = this.clock.instant();
        Instant lastRun = this.lastRuns.get(guildId);
        Instant nextRun = lastRun == null
                ? now
                : lastRun.plus(this.guildConfigService.matchingInterval(guildId)).minus(SCHEDULING_TOLERANCE);
        if (now.isBefore(nextRun)) {
            return List.of();
        }
        this.lastRuns.put(guildId, now);
        List<MatchCandidate> candidates = this.freeCandidates(guildId, members);
        Set<UserPair> blocked = new HashSet<>(this.proposalService.blockedPairs(guildId));
        blocked.addAll(this.blockService.blockedPairs(guildId));
        List<MatchPair> pairs = this.matchingService.findPairs(candidates, blocked);
        return this.proposalService.createProposals(guildId, pairs, this.guildConfigService.responseDelay(guildId));
    }

    private List<MatchCandidate> freeCandidates(long guildId, LongFunction<Optional<MemberSnapshot>> members) {
        Set<Long> busy = this.proposalService.busyUsers(guildId);
        List<Tag> tags = this.tagService.listTags(guildId);
        List<MatchCandidate> candidates = new ArrayList<>();
        for (Profile profile : this.profileService.searchingProfiles(guildId)) {
            if (busy.contains(profile.userId())) {
                continue;
            }
            Optional<MemberSnapshot> member = members.apply(profile.userId());
            if (member.isEmpty()) {
                continue;
            }
            Set<Long> usable = tags.stream()
                    .filter(tag -> this.tagService.canUse(tag, member.get()))
                    .map(Tag::id)
                    .collect(Collectors.toSet());
            candidates.add(new MatchCandidate(profile, member.get(), usable));
        }
        return candidates;
    }
}
