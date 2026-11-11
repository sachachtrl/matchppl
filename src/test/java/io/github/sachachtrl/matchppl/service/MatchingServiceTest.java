package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MatchCandidate;
import io.github.sachachtrl.matchppl.model.MatchPair;
import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.PartnerPreferences;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.UserPair;
import io.github.sachachtrl.matchppl.repository.InMemoryActivityRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingServiceTest {
    private static final long GUILD = 1L;
    private static final long FINALS = 100L;
    private static final long MEDIUM = 101L;
    private static final long LIGHT = 102L;
    private static final long LILLE = 103L;
    private static final long CINEMA = 104L;
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    private InMemoryActivityRepository activityRepo;
    private MatchingService service;

    @BeforeEach
    void setUp() {
        this.activityRepo = new InMemoryActivityRepository();
        ConditionChecker checker = new ConditionChecker(new ActivityService(this.activityRepo),
                Clock.fixed(NOW, ZoneOffset.UTC));
        this.service = new MatchingService(checker, new Random(42));
    }

    private static MatchCandidate candidate(long userId, Long... tags) {
        return candidate(userId, Duration.ofHours(1), true, UnaryOperator.identity(), tags);
    }

    private static MatchCandidate candidate(long userId, UnaryOperator<PartnerPreferences> preferences, Long... tags) {
        return candidate(userId, Duration.ofHours(1), true, preferences, tags);
    }

    private static MatchCandidate candidate(long userId, Duration waiting, boolean customAvatar,
            UnaryOperator<PartnerPreferences> preferences, Long... tags) {
        Set<Long> tagIds = Set.of(tags);
        Profile profile = Profile.empty(GUILD, userId)
                .withTagSets(tagIds, Set.of(), Set.of())
                .withSearching(true, NOW.minus(waiting))
                .withPreferences(preferences.apply(PartnerPreferences.DEFAULT));
        MemberSnapshot member = new MemberSnapshot(GUILD, userId, NOW.minus(Duration.ofDays(365)),
                NOW.minus(Duration.ofDays(100)), Set.of(), customAvatar);
        return new MatchCandidate(profile, member, tagIds);
    }

    private static MatchCandidate withProfile(MatchCandidate candidate, UnaryOperator<Profile> change) {
        return new MatchCandidate(change.apply(candidate.profile()), candidate.member(), candidate.usableTagIds());
    }

    private List<UserPair> pairs(MatchCandidate... candidates) {
        return this.pairs(Set.of(), candidates);
    }

    private List<UserPair> pairs(Set<UserPair> blocked, MatchCandidate... candidates) {
        return this.service.findPairs(Arrays.asList(candidates), blocked).stream().map(MatchPair::users).toList();
    }

    @Test
    void scoreIsTheSameForBothMembers() {
        MatchPair pair = this.service.findPairs(List.of(
                candidate(1, FINALS, MEDIUM),
                candidate(2, FINALS, LILLE, CINEMA)), Set.of()).getFirst();

        assertEquals(1, pair.commonTags());
        assertEquals(3, pair.largestTagCount());
        assertEquals(1.0 / 3, pair.score(), 1e-9);
    }

    @Test
    void bestScoresArePickedFirstForTheWholeServer() {
        MatchCandidate a = candidate(1, FINALS, MEDIUM);
        MatchCandidate b = candidate(2, FINALS, MEDIUM);
        MatchCandidate c = candidate(3, FINALS, LILLE);
        MatchCandidate d = candidate(4, LILLE, CINEMA);

        List<UserPair> result = this.pairs(a, b, c, d);

        assertEquals(List.of(new UserPair(1, 2), new UserPair(3, 4)), result);
    }

    @Test
    void eachMemberReceivesAtMostOneProposal() {
        List<UserPair> result = this.pairs(candidate(1, FINALS), candidate(2, FINALS), candidate(3, FINALS));

        assertEquals(1, result.size());
    }

    @Test
    void membersWithoutCommonTagAreNotMatchedByDefault() {
        assertTrue(this.pairs(candidate(1, FINALS), candidate(2, LILLE)).isEmpty());
    }

    @Test
    void membersWithoutCommonTagAreMatchedWhenBothAcceptIt() {
        MatchCandidate a = candidate(1, preferences -> preferences.withMinCommonTags(0), FINALS);
        MatchCandidate b = candidate(2, preferences -> preferences.withMinCommonTags(0), LILLE);

        assertEquals(List.of(new UserPair(1, 2)), this.pairs(a, b));
    }

    @Test
    void oneMemberAcceptingNoCommonTagIsNotEnough() {
        MatchCandidate a = candidate(1, preferences -> preferences.withMinCommonTags(0), FINALS);

        assertTrue(this.pairs(a, candidate(2, LILLE)).isEmpty());
    }

    @Test
    void theHighestMinimumOfCommonTagsApplies() {
        MatchCandidate a = candidate(1, preferences -> preferences.withMinCommonTags(2), FINALS, MEDIUM, LILLE);

        assertTrue(this.pairs(a, candidate(2, FINALS, CINEMA)).isEmpty());
        assertEquals(1, this.pairs(a, candidate(3, FINALS, MEDIUM)).size());
    }

    @Test
    void requiredTagsMustAllBePresent() {
        MatchCandidate a = candidate(1, preferences -> preferences.withRequiredTagIds(Set.of(FINALS, LILLE)), FINALS);

        assertTrue(this.pairs(a, candidate(2, FINALS)).isEmpty());
        assertEquals(1, this.pairs(a, candidate(3, FINALS, LILLE)).size());
    }

    @Test
    void excludedTagsBlockThePairInBothDirections() {
        MatchCandidate a = withProfile(candidate(1, FINALS, MEDIUM),
                profile -> profile.withTagSets(profile.tagIds(), Set.of(LIGHT), Set.of()));
        MatchCandidate b = candidate(2, FINALS, LIGHT);

        assertTrue(this.pairs(a, b).isEmpty());
        assertTrue(this.pairs(b, a).isEmpty());
    }

    @Test
    void minimumCompatibilityIsCheckedOnTheSharedScore() {
        MatchCandidate a = candidate(1, preferences -> preferences.withMinCompatibility(50), FINALS, MEDIUM);

        assertTrue(this.pairs(a, candidate(2, FINALS, LILLE, CINEMA)).isEmpty());
        assertEquals(1, this.pairs(a, candidate(3, FINALS, LILLE)).size());
    }

    @Test
    void anonymousMembersCanBeExcluded() {
        MatchCandidate a = candidate(1, preferences -> preferences.withExcludeAnonymous(true), FINALS);
        MatchCandidate anonymous = withProfile(candidate(2, FINALS), profile -> profile.withAnonymous(true));

        assertTrue(this.pairs(a, anonymous).isEmpty());
        assertEquals(1, this.pairs(a, candidate(3, FINALS)).size());
    }

    @Test
    void defaultAvatarsAreExcludedEvenForAnonymousMembers() {
        MatchCandidate a = candidate(1, preferences -> preferences.withExcludeDefaultAvatars(true), FINALS);
        MatchCandidate defaultAvatar = candidate(2, Duration.ofHours(1), false, UnaryOperator.identity(), FINALS);
        MatchCandidate anonymousWithCustomAvatar = withProfile(candidate(3, FINALS),
                profile -> profile.withAnonymous(true));

        assertTrue(this.pairs(a, defaultAvatar).isEmpty());
        assertEquals(1, this.pairs(a, anonymousWithCustomAvatar).size());
    }

    @Test
    void partnerConditionsAreChecked() {
        MemberConditions conditions = new MemberConditions(null, null, 2, ActivityPeriod.TWO_WEEKS);
        MatchCandidate a = candidate(1, preferences -> preferences.withConditions(conditions), FINALS);
        MatchCandidate b = candidate(2, FINALS);
        assertTrue(this.pairs(a, b).isEmpty());

        LocalDate today = LocalDate.ofInstant(NOW, ZoneOffset.UTC);
        this.activityRepo.incrementMessageCount(GUILD, 2, today);
        this.activityRepo.incrementMessageCount(GUILD, 2, today);

        assertEquals(1, this.pairs(a, b).size());
    }

    @Test
    void blockedPairsAreNeverProposed() {
        assertTrue(this.pairs(Set.of(new UserPair(2, 1)), candidate(1, FINALS), candidate(2, FINALS)).isEmpty());
    }

    @Test
    void tagsAMemberCanNoLongerUseDoNotCount() {
        MatchCandidate lostFinals = new MatchCandidate(candidate(2, FINALS, LILLE).profile(),
                candidate(2, FINALS, LILLE).member(), Set.of(LILLE));

        assertTrue(this.pairs(candidate(1, FINALS), lostFinals).isEmpty());
    }

    @Test
    void onEqualScoresTheLongestWaitingMemberIsServedFirst() {
        MatchCandidate a = candidate(1, Duration.ofMinutes(5), true, UnaryOperator.identity(), FINALS);
        MatchCandidate recent = candidate(2, Duration.ofMinutes(1), true, UnaryOperator.identity(), FINALS);
        MatchCandidate waitingLong = candidate(3, Duration.ofDays(2), true, UnaryOperator.identity(), FINALS);

        assertEquals(List.of(new UserPair(1, 3)), this.pairs(a, recent, waitingLong));
    }

    @Test
    void everyMemberAppearsInAtMostOnePairOnALargeServer() {
        List<MatchCandidate> candidates = LongStream.rangeClosed(1, 60)
                .mapToObj(id -> candidate(id, FINALS + id % 4, 200 + id % 3))
                .toList();

        List<MatchPair> result = this.service.findPairs(candidates, Set.of());

        Set<Long> users = result.stream()
                .flatMap(pair -> Stream.of(pair.firstUserId(), pair.secondUserId()))
                .collect(Collectors.toSet());
        assertEquals(result.size() * 2, users.size());
    }
}
