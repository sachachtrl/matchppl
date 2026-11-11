package io.github.sachachtrl.matchppl.model;

import java.util.Set;

public record Tag(
        long id,
        long guildId,
        String name,
        String emoji,
        MemberConditions conditions,
        Set<Long> requiredRoleIds,
        Set<Long> forbiddenRoleIds) {

    public Tag {
        requiredRoleIds = Set.copyOf(requiredRoleIds);
        forbiddenRoleIds = Set.copyOf(forbiddenRoleIds);
    }

    public Tag withConditions(MemberConditions newConditions) {
        return new Tag(this.id, this.guildId, this.name, this.emoji, newConditions,
                this.requiredRoleIds, this.forbiddenRoleIds);
    }

    public Tag withRoles(Set<Long> required, Set<Long> forbidden) {
        return new Tag(this.id, this.guildId, this.name, this.emoji, this.conditions, required, forbidden);
    }
}
