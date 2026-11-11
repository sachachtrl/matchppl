package io.github.sachachtrl.matchppl.model;

import java.util.Set;

public record PartnerPreferences(
        Set<Long> requiredTagIds,
        Integer minCompatibility,
        int minCommonTags,
        boolean excludeAnonymous,
        boolean excludeDefaultAvatars,
        MemberConditions conditions) {

    public static final int DEFAULT_MIN_COMMON_TAGS = 1;
    public static final PartnerPreferences DEFAULT = new PartnerPreferences(
            Set.of(), null, DEFAULT_MIN_COMMON_TAGS, false, false, MemberConditions.NONE);

    public PartnerPreferences {
        requiredTagIds = Set.copyOf(requiredTagIds);
    }

    public PartnerPreferences withRequiredTagIds(Set<Long> value) {
        return new PartnerPreferences(value, this.minCompatibility, this.minCommonTags, this.excludeAnonymous,
                this.excludeDefaultAvatars, this.conditions);
    }

    public PartnerPreferences withMinCompatibility(Integer value) {
        return new PartnerPreferences(this.requiredTagIds, value, this.minCommonTags, this.excludeAnonymous,
                this.excludeDefaultAvatars, this.conditions);
    }

    public PartnerPreferences withMinCommonTags(int value) {
        return new PartnerPreferences(this.requiredTagIds, this.minCompatibility, value, this.excludeAnonymous,
                this.excludeDefaultAvatars, this.conditions);
    }

    public PartnerPreferences withExcludeAnonymous(boolean value) {
        return new PartnerPreferences(this.requiredTagIds, this.minCompatibility, this.minCommonTags, value,
                this.excludeDefaultAvatars, this.conditions);
    }

    public PartnerPreferences withExcludeDefaultAvatars(boolean value) {
        return new PartnerPreferences(this.requiredTagIds, this.minCompatibility, this.minCommonTags,
                this.excludeAnonymous, value, this.conditions);
    }

    public PartnerPreferences withConditions(MemberConditions value) {
        return new PartnerPreferences(this.requiredTagIds, this.minCompatibility, this.minCommonTags,
                this.excludeAnonymous, this.excludeDefaultAvatars, value);
    }
}
