package io.github.sachachtrl.matchppl.model;

import java.util.Set;

public record Profile(
        long guildId,
        long userId,
        boolean searching,
        String presentation,
        boolean anonymous,
        Set<Long> tagIds,
        Set<Long> excludedTagIds,
        Set<Long> requiredTagIds,
        Integer minCompatibility,
        boolean requirePhoto,
        MemberConditions partnerConditions) {

    public Profile {
        tagIds = Set.copyOf(tagIds);
        excludedTagIds = Set.copyOf(excludedTagIds);
        requiredTagIds = Set.copyOf(requiredTagIds);
    }

    public static Profile empty(long guildId, long userId) {
        return new Profile(guildId, userId, false, null, false, Set.of(), Set.of(), Set.of(), null, false,
                MemberConditions.NONE);
    }

    public Profile withSearching(boolean value) {
        return new Profile(this.guildId, this.userId, value, this.presentation, this.anonymous, this.tagIds,
                this.excludedTagIds, this.requiredTagIds, this.minCompatibility, this.requirePhoto,
                this.partnerConditions);
    }

    public Profile withPresentation(String value) {
        return new Profile(this.guildId, this.userId, this.searching, value, this.anonymous, this.tagIds,
                this.excludedTagIds, this.requiredTagIds, this.minCompatibility, this.requirePhoto,
                this.partnerConditions);
    }

    public Profile withAnonymous(boolean value) {
        return new Profile(this.guildId, this.userId, this.searching, this.presentation, value, this.tagIds,
                this.excludedTagIds, this.requiredTagIds, this.minCompatibility, this.requirePhoto,
                this.partnerConditions);
    }

    public Profile withTagSets(Set<Long> tags, Set<Long> excluded, Set<Long> required) {
        return new Profile(this.guildId, this.userId, this.searching, this.presentation, this.anonymous, tags,
                excluded, required, this.minCompatibility, this.requirePhoto, this.partnerConditions);
    }

    public Profile withMinCompatibility(Integer value) {
        return new Profile(this.guildId, this.userId, this.searching, this.presentation, this.anonymous, this.tagIds,
                this.excludedTagIds, this.requiredTagIds, value, this.requirePhoto, this.partnerConditions);
    }

    public Profile withRequirePhoto(boolean value) {
        return new Profile(this.guildId, this.userId, this.searching, this.presentation, this.anonymous, this.tagIds,
                this.excludedTagIds, this.requiredTagIds, this.minCompatibility, value, this.partnerConditions);
    }

    public Profile withPartnerConditions(MemberConditions value) {
        return new Profile(this.guildId, this.userId, this.searching, this.presentation, this.anonymous, this.tagIds,
                this.excludedTagIds, this.requiredTagIds, this.minCompatibility, this.requirePhoto, value);
    }
}
