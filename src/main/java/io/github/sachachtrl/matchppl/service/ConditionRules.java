package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberConditions;

public final class ConditionRules {
    public static final ActivityPeriod DEFAULT_ACTIVITY_PERIOD = ActivityPeriod.THIRTY_DAYS;

    private ConditionRules() {
    }

    public static MemberConditions withMinAccountAge(MemberConditions conditions, Integer days) {
        return conditions.withMinAccountAgeDays(positiveOrNull(days));
    }

    public static MemberConditions withMinMemberDays(MemberConditions conditions, Integer days) {
        return conditions.withMinMemberDays(positiveOrNull(days));
    }

    public static MemberConditions withActivity(MemberConditions conditions, Integer messages, ActivityPeriod period) {
        Integer minMessages = positiveOrNull(messages);
        if (minMessages == null) {
            return conditions.withActivity(null, null);
        }
        return conditions.withActivity(minMessages, period == null ? DEFAULT_ACTIVITY_PERIOD : period);
    }

    static Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }
}
