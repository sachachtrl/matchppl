package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MatchCandidate;
import io.github.sachachtrl.matchppl.model.MatchPair;
import io.github.sachachtrl.matchppl.model.PartnerPreferences;
import io.github.sachachtrl.matchppl.model.UserPair;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

public class MatchingService {
    private final class Prepared {
        private final MatchCandidate candidate;
        private final Set<Long> tags;
        private final Map<ActivityPeriod, Integer> messageCounts = new EnumMap<>(ActivityPeriod.class);

        private Prepared(MatchCandidate candidate) {
            this.candidate = candidate;
            Set<Long> effective = new HashSet<>(candidate.profile().tagIds());
            effective.retainAll(candidate.usableTagIds());
            this.tags = effective;
        }

        private int messages(ActivityPeriod period) {
            return this.messageCounts.computeIfAbsent(period,
                    key -> MatchingService.this.conditionChecker.countMessages(this.candidate.member(), key));
        }

        private Instant waitingSince() {
            Instant since = this.candidate.profile().waitingSince();
            return since == null ? Instant.MAX : since;
        }
    }

    private record Option(MatchPair pair, Instant longestWait, Instant shortestWait) {}

    private static final Comparator<Option> BEST_FIRST = ((Comparator<Option>) MatchingService::compareScores)
            .thenComparing(Option::longestWait)
            .thenComparing(Option::shortestWait);

    private final ConditionChecker conditionChecker;
    private final RandomGenerator random;

    public MatchingService(ConditionChecker conditionChecker, RandomGenerator random) {
        this.conditionChecker = conditionChecker;
        this.random = random;
    }

    public List<MatchPair> findPairs(List<MatchCandidate> candidates, Set<UserPair> blockedPairs) {
        List<Prepared> prepared = candidates.stream()
                .map(Prepared::new)
                .filter(candidate -> !candidate.tags.isEmpty())
                .toList();
        List<Option> options = new ArrayList<>();
        for (long key : this.pairsToEvaluate(prepared)) {
            Prepared first = prepared.get((int) (key >>> Integer.SIZE));
            Prepared second = prepared.get((int) key);
            MatchPair pair = this.evaluate(first, second, blockedPairs);
            if (pair != null) {
                Instant firstWait = first.waitingSince();
                Instant secondWait = second.waitingSince();
                boolean firstWaitsLonger = firstWait.isBefore(secondWait);
                options.add(new Option(pair, firstWaitsLonger ? firstWait : secondWait,
                        firstWaitsLonger ? secondWait : firstWait));
            }
        }
        Collections.shuffle(options, this.random);
        options.sort(BEST_FIRST);
        return selectGreedily(options);
    }

    private Set<Long> pairsToEvaluate(List<Prepared> prepared) {
        Map<Long, List<Integer>> membersByTag = new HashMap<>();
        List<Integer> acceptingNoCommonTag = new ArrayList<>();
        for (int index = 0; index < prepared.size(); index++) {
            for (long tagId : prepared.get(index).tags) {
                membersByTag.computeIfAbsent(tagId, key -> new ArrayList<>()).add(index);
            }
            if (prepared.get(index).candidate.profile().preferences().minCommonTags() == 0) {
                acceptingNoCommonTag.add(index);
            }
        }
        Set<Long> keys = new LinkedHashSet<>();
        for (List<Integer> members : membersByTag.values()) {
            addAllPairs(members, keys);
        }
        addAllPairs(acceptingNoCommonTag, keys);
        return keys;
    }

    private static void addAllPairs(List<Integer> indexes, Set<Long> keys) {
        for (int i = 0; i < indexes.size(); i++) {
            for (int j = i + 1; j < indexes.size(); j++) {
                int low = Math.min(indexes.get(i), indexes.get(j));
                int high = Math.max(indexes.get(i), indexes.get(j));
                keys.add(((long) low << Integer.SIZE) | high);
            }
        }
    }

    private MatchPair evaluate(Prepared first, Prepared second, Set<UserPair> blockedPairs) {
        long firstId = first.candidate.userId();
        long secondId = second.candidate.userId();
        if (firstId == secondId || blockedPairs.contains(new UserPair(firstId, secondId))) {
            return null;
        }
        Set<Long> common = new HashSet<>(first.tags);
        common.retainAll(second.tags);
        int largestTagCount = Math.max(first.tags.size(), second.tags.size());
        MatchPair pair = new MatchPair(firstId, secondId, common.size(), largestTagCount);
        return this.accepts(first, second, pair) && this.accepts(second, first, pair) ? pair : null;
    }

    private boolean accepts(Prepared self, Prepared other, MatchPair pair) {
        PartnerPreferences preferences = self.candidate.profile().preferences();
        if (pair.commonTags() < preferences.minCommonTags()) {
            return false;
        }
        if (preferences.minCompatibility() != null
                && pair.commonTags() * 100 < preferences.minCompatibility() * pair.largestTagCount()) {
            return false;
        }
        if (!other.tags.containsAll(preferences.requiredTagIds())) {
            return false;
        }
        if (self.candidate.profile().excludedTagIds().stream().anyMatch(other.tags::contains)) {
            return false;
        }
        if (preferences.excludeAnonymous() && other.candidate.profile().anonymous()) {
            return false;
        }
        if (preferences.excludeDefaultAvatars() && !other.candidate.member().customAvatar()) {
            return false;
        }
        return preferences.conditions().isEmpty()
                || this.conditionChecker.meets(preferences.conditions(), other.candidate.member(), other::messages);
    }

    private static int compareScores(Option first, Option second) {
        long firstWeight = (long) first.pair().commonTags() * second.pair().largestTagCount();
        long secondWeight = (long) second.pair().commonTags() * first.pair().largestTagCount();
        return Long.compare(secondWeight, firstWeight);
    }

    private static List<MatchPair> selectGreedily(List<Option> options) {
        Set<Long> taken = new HashSet<>();
        List<MatchPair> selected = new ArrayList<>();
        for (Option option : options) {
            MatchPair pair = option.pair();
            if (!taken.contains(pair.firstUserId()) && !taken.contains(pair.secondUserId())) {
                taken.add(pair.firstUserId());
                taken.add(pair.secondUserId());
                selected.add(pair);
            }
        }
        return selected;
    }
}
