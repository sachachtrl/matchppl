package io.github.sachachtrl.matchppl.model;

import java.util.Set;

public record Tag(
        long id,
        long guildId,
        String name,
        String emoji,
        Integer minAccountAgeDays,
        Integer minMemberDays,
        Integer minMessages,
        ActivityPeriod activityPeriod,
        Set<Long> requiredRoleIds,
        Set<Long> forbiddenRoleIds) {

    public Tag {
        requiredRoleIds = Set.copyOf(requiredRoleIds);
        forbiddenRoleIds = Set.copyOf(forbiddenRoleIds);
    }

    public Tag withMinAccountAgeDays(Integer days) {
        return new Tag(this.id, this.guildId, this.name, this.emoji, days, this.minMemberDays,
                this.minMessages, this.activityPeriod, this.requiredRoleIds, this.forbiddenRoleIds);
    }

    public Tag withMinMemberDays(Integer days) {
        return new Tag(this.id, this.guildId, this.name, this.emoji, this.minAccountAgeDays, days,
                this.minMessages, this.activityPeriod, this.requiredRoleIds, this.forbiddenRoleIds);
    }

    public Tag withActivity(Integer messages, ActivityPeriod period) {
        return new Tag(this.id, this.guildId, this.name, this.emoji, this.minAccountAgeDays, this.minMemberDays,
                messages, period, this.requiredRoleIds, this.forbiddenRoleIds);
    }

    public Tag withRoles(Set<Long> required, Set<Long> forbidden) {
        return new Tag(this.id, this.guildId, this.name, this.emoji, this.minAccountAgeDays, this.minMemberDays,
                this.minMessages, this.activityPeriod, required, forbidden);
    }
}
