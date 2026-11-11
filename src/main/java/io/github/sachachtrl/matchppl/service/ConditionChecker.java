package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.ToIntFunction;

public class ConditionChecker {
    private final ActivityService activityService;
    private final Clock clock;

    public ConditionChecker(ActivityService activityService, Clock clock) {
        this.activityService = activityService;
        this.clock = clock;
    }

    public boolean meets(MemberConditions conditions, MemberSnapshot member) {
        return this.meets(conditions, member, period -> this.countMessages(member, period));
    }

    public boolean meets(MemberConditions conditions, MemberSnapshot member,
            ToIntFunction<ActivityPeriod> messageCount) {
        Instant now = this.clock.instant();
        if (conditions.minAccountAgeDays() != null
                && daysBetween(member.accountCreatedAt(), now) < conditions.minAccountAgeDays()) {
            return false;
        }
        if (conditions.minMemberDays() != null
                && daysBetween(member.joinedAt(), now) < conditions.minMemberDays()) {
            return false;
        }
        if (conditions.minMessages() != null) {
            return messageCount.applyAsInt(conditions.activityPeriod()) >= conditions.minMessages();
        }
        return true;
    }

    public int countMessages(MemberSnapshot member, ActivityPeriod period) {
        return this.activityService.countMessages(member.guildId(), member.userId(), period,
                LocalDate.now(this.clock));
    }

    private static long daysBetween(Instant from, Instant to) {
        return Duration.between(from, to).toDays();
    }
}
