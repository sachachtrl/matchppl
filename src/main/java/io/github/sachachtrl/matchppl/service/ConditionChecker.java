package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

public class ConditionChecker {
    private final ActivityService activityService;
    private final Clock clock;

    public ConditionChecker(ActivityService activityService, Clock clock) {
        this.activityService = activityService;
        this.clock = clock;
    }

    public boolean meets(MemberConditions conditions, MemberSnapshot member) {
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
            int messages = this.activityService.countMessages(member.guildId(), member.userId(),
                    conditions.activityPeriod(), LocalDate.now(this.clock));
            return messages >= conditions.minMessages();
        }
        return true;
    }

    private static long daysBetween(Instant from, Instant to) {
        return Duration.between(from, to).toDays();
    }
}
