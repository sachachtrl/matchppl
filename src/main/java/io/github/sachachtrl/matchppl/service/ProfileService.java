package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.PartnerPreferences;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.repository.ProfileRepository;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class ProfileService {
    public static final int MAX_PRESENTATION_LENGTH = 300;
    public static final int MAX_COMPATIBILITY = 100;
    public static final int MAX_MIN_COMMON_TAGS = 5;

    private final ProfileRepository profileRepo;
    private final TagService tagService;
    private final Clock clock;

    public ProfileService(ProfileRepository profileRepo, TagService tagService, Clock clock) {
        this.profileRepo = profileRepo;
        this.tagService = tagService;
        this.clock = clock;
    }

    public Profile getProfile(long guildId, long userId) {
        return this.profileRepo.find(guildId, userId);
    }

    public synchronized boolean setSearching(long guildId, long userId, boolean searching) {
        Profile profile = this.profileRepo.find(guildId, userId);
        if (searching && profile.tagIds().isEmpty()) {
            return false;
        }
        if (searching == profile.searching()) {
            return true;
        }
        Profile updated = profile.withSearching(searching, searching ? this.clock.instant() : null);
        this.profileRepo.save(searching ? updated.withDmUnreachable(false) : updated);
        return true;
    }

    public List<Profile> searchingProfiles(long guildId) {
        return this.profileRepo.findSearching(guildId);
    }

    public int countSearching(long guildId) {
        return this.profileRepo.countSearching(guildId);
    }

    public synchronized int stopAllSearches(long guildId) {
        return this.profileRepo.stopAllSearches(guildId);
    }

    public synchronized void markUnreachable(long guildId, long userId) {
        Profile profile = this.profileRepo.find(guildId, userId);
        if (!profile.searching()) {
            return;
        }
        this.profileRepo.save(profile.withSearching(false, null).withDmUnreachable(true));
    }

    public synchronized void deleteProfile(long guildId, long userId) {
        this.profileRepo.delete(guildId, userId);
    }

    public synchronized void restartWaiting(long guildId, long userId) {
        Profile profile = this.profileRepo.find(guildId, userId);
        if (profile.searching()) {
            this.profileRepo.save(profile.withSearching(true, this.clock.instant()));
        }
    }

    public synchronized boolean setPresentation(long guildId, long userId, String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.length() > MAX_PRESENTATION_LENGTH) {
            return false;
        }
        Profile profile = this.profileRepo.find(guildId, userId);
        this.profileRepo.save(profile.withPresentation(trimmed.isEmpty() ? null : trimmed));
        return true;
    }

    public synchronized Profile setTags(MemberSnapshot member, Set<Long> tagIds) {
        Set<Long> usable = this.tagService.usableTags(member).stream().map(Tag::id).collect(Collectors.toSet());
        Set<Long> chosen = intersection(tagIds, usable);
        Profile profile = this.profileRepo.find(member.guildId(), member.userId());
        return this.saveTagSets(profile, chosen, TagService.without(profile.excludedTagIds(), chosen),
                profile.preferences().requiredTagIds());
    }

    public synchronized Profile setTags(MemberSnapshot member, Set<Long> shown, Set<Long> selected) {
        Profile profile = this.profileRepo.find(member.guildId(), member.userId());
        return this.setTags(member, replaceWithin(profile.tagIds(), shown, selected));
    }

    public synchronized Profile setExcludedTags(long guildId, long userId, Set<Long> shown, Set<Long> selected) {
        Profile profile = this.profileRepo.find(guildId, userId);
        return this.setExcludedTags(guildId, userId, replaceWithin(profile.excludedTagIds(), shown, selected));
    }

    public synchronized Profile setRequiredTags(long guildId, long userId, Set<Long> shown, Set<Long> selected) {
        Profile profile = this.profileRepo.find(guildId, userId);
        return this.setRequiredTags(guildId, userId,
                replaceWithin(profile.preferences().requiredTagIds(), shown, selected));
    }

    public synchronized Profile setExcludedTags(long guildId, long userId, Set<Long> tagIds) {
        Set<Long> excluded = intersection(tagIds, this.existingTagIds(guildId));
        Profile profile = this.profileRepo.find(guildId, userId);
        return this.saveTagSets(profile, TagService.without(profile.tagIds(), excluded), excluded,
                TagService.without(profile.preferences().requiredTagIds(), excluded));
    }

    public synchronized Profile setRequiredTags(long guildId, long userId, Set<Long> tagIds) {
        Set<Long> required = intersection(tagIds, this.existingTagIds(guildId));
        Profile profile = this.profileRepo.find(guildId, userId);
        return this.saveTagSets(profile, profile.tagIds(), TagService.without(profile.excludedTagIds(), required),
                required);
    }

    public synchronized Profile setAnonymous(long guildId, long userId, boolean anonymous) {
        Profile updated = this.profileRepo.find(guildId, userId).withAnonymous(anonymous);
        this.profileRepo.save(updated);
        return updated;
    }

    public synchronized Profile setAllowRooms(long guildId, long userId, boolean allow) {
        Profile updated = this.profileRepo.find(guildId, userId).withAllowRooms(allow);
        this.profileRepo.save(updated);
        return updated;
    }

    public synchronized Profile setExcludeAnonymous(long guildId, long userId, boolean exclude) {
        return this.updatePreferences(guildId, userId, preferences -> preferences.withExcludeAnonymous(exclude));
    }

    public synchronized Profile setExcludeDefaultAvatars(long guildId, long userId, boolean exclude) {
        return this.updatePreferences(guildId, userId, preferences -> preferences.withExcludeDefaultAvatars(exclude));
    }

    public synchronized boolean setMinCommonTags(long guildId, long userId, int count) {
        if (count < 0 || count > MAX_MIN_COMMON_TAGS) {
            return false;
        }
        this.updatePreferences(guildId, userId, preferences -> preferences.withMinCommonTags(count));
        return true;
    }

    public synchronized boolean setMinCompatibility(long guildId, long userId, Integer percent) {
        if (percent != null && (percent < 0 || percent > MAX_COMPATIBILITY)) {
            return false;
        }
        Integer value = ConditionRules.positiveOrNull(percent);
        this.updatePreferences(guildId, userId, preferences -> preferences.withMinCompatibility(value));
        return true;
    }

    public synchronized Profile setPartnerConditions(long guildId, long userId, Integer minAccountAgeDays,
            Integer minMemberDays, Integer minMessages, ActivityPeriod period) {
        MemberConditions conditions = ConditionRules.withMinAccountAge(MemberConditions.NONE, minAccountAgeDays);
        conditions = ConditionRules.withMinMemberDays(conditions, minMemberDays);
        MemberConditions newConditions = ConditionRules.withActivity(conditions, minMessages, period);
        return this.updatePreferences(guildId, userId, preferences -> preferences.withConditions(newConditions));
    }

    private Profile updatePreferences(long guildId, long userId, UnaryOperator<PartnerPreferences> change) {
        Profile profile = this.profileRepo.find(guildId, userId);
        Profile updated = profile.withPreferences(change.apply(profile.preferences()));
        this.profileRepo.save(updated);
        return updated;
    }

    private Profile saveTagSets(Profile profile, Set<Long> chosen, Set<Long> excluded, Set<Long> required) {
        Profile updated = profile.withTagSets(chosen, excluded, required);
        if (chosen.isEmpty()) {
            updated = updated.withSearching(false, null);
        }
        this.profileRepo.save(updated);
        return updated;
    }

    private Set<Long> existingTagIds(long guildId) {
        return this.tagService.listTags(guildId).stream().map(Tag::id).collect(Collectors.toSet());
    }

    private static Set<Long> replaceWithin(Set<Long> current, Set<Long> shown, Set<Long> selected) {
        Set<Long> result = new HashSet<>(current);
        result.removeAll(shown);
        result.addAll(intersection(selected, shown));
        return result;
    }

    private static Set<Long> intersection(Set<Long> requested, Set<Long> allowed) {
        Set<Long> result = new HashSet<>(requested);
        result.retainAll(allowed);
        return result;
    }
}
