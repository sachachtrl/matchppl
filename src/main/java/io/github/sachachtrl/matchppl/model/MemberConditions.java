package io.github.sachachtrl.matchppl.model;

public record MemberConditions(
        Integer minAccountAgeDays,
        Integer minMemberDays,
        Integer minMessages,
        ActivityPeriod activityPeriod) {

    public static final MemberConditions NONE = new MemberConditions(null, null, null, null);

    public MemberConditions withMinAccountAgeDays(Integer days) {
        return new MemberConditions(days, this.minMemberDays, this.minMessages, this.activityPeriod);
    }

    public MemberConditions withMinMemberDays(Integer days) {
        return new MemberConditions(this.minAccountAgeDays, days, this.minMessages, this.activityPeriod);
    }

    public MemberConditions withActivity(Integer messages, ActivityPeriod period) {
        return new MemberConditions(this.minAccountAgeDays, this.minMemberDays, messages, period);
    }

    public boolean isEmpty() {
        return this.minAccountAgeDays == null && this.minMemberDays == null && this.minMessages == null;
    }
}
