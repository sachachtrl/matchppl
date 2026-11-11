package io.github.sachachtrl.matchppl.model;

import java.util.Set;

public record MatchCandidate(Profile profile, MemberSnapshot member, Set<Long> usableTagIds) {

    public MatchCandidate {
        usableTagIds = Set.copyOf(usableTagIds);
    }

    public long userId() {
        return this.profile.userId();
    }
}
