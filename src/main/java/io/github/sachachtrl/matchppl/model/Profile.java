package io.github.sachachtrl.matchppl.model;

import java.time.Instant;
import java.util.Set;

public record Profile(
        long guildId,
        long userId,
        boolean searching,
        Instant waitingSince,
        boolean dmUnreachable,
        String presentation,
        boolean anonymous,
        Set<Long> tagIds,
        Set<Long> excludedTagIds,
        PartnerPreferences preferences) {

    public Profile {
        tagIds = Set.copyOf(tagIds);
        excludedTagIds = Set.copyOf(excludedTagIds);
    }

    public static Profile empty(long guildId, long userId) {
        return new Profile(guildId, userId, false, null, false, null, false, Set.of(), Set.of(),
                PartnerPreferences.DEFAULT);
    }

    public Profile withSearching(boolean value, Instant since) {
        return new Profile(this.guildId, this.userId, value, since, this.dmUnreachable, this.presentation,
                this.anonymous, this.tagIds, this.excludedTagIds, this.preferences);
    }

    public Profile withDmUnreachable(boolean value) {
        return new Profile(this.guildId, this.userId, this.searching, this.waitingSince, value, this.presentation,
                this.anonymous, this.tagIds, this.excludedTagIds, this.preferences);
    }

    public Profile withPresentation(String value) {
        return new Profile(this.guildId, this.userId, this.searching, this.waitingSince, this.dmUnreachable, value,
                this.anonymous, this.tagIds, this.excludedTagIds, this.preferences);
    }

    public Profile withAnonymous(boolean value) {
        return new Profile(this.guildId, this.userId, this.searching, this.waitingSince, this.dmUnreachable,
                this.presentation, value, this.tagIds, this.excludedTagIds, this.preferences);
    }

    public Profile withTagSets(Set<Long> tags, Set<Long> excluded, Set<Long> required) {
        return new Profile(this.guildId, this.userId, this.searching, this.waitingSince, this.dmUnreachable,
                this.presentation, this.anonymous, tags, excluded, this.preferences.withRequiredTagIds(required));
    }

    public Profile withPreferences(PartnerPreferences value) {
        return new Profile(this.guildId, this.userId, this.searching, this.waitingSince, this.dmUnreachable,
                this.presentation, this.anonymous, this.tagIds, this.excludedTagIds, value);
    }
}
