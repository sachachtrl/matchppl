package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.repository.ProfileRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ProfileService {
    public static final int MAX_PRESENTATION_LENGTH = 300;
    public static final int MAX_COMPATIBILITY = 100;

    private final ProfileRepository profileRepo;
    private final TagService tagService;

    public ProfileService(ProfileRepository profileRepo, TagService tagService) {
        this.profileRepo = profileRepo;
        this.tagService = tagService;
    }

    public Profile getProfile(long guildId, long userId) {
        return this.profileRepo.find(guildId, userId);
    }

    public boolean setSearching(long guildId, long userId, boolean searching) {
        Profile profile = this.profileRepo.find(guildId, userId);
        if (searching && profile.tagIds().isEmpty()) {
            return false;
        }
        this.profileRepo.save(profile.withSearching(searching));
        return true;
    }

    public boolean setPresentation(long guildId, long userId, String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.length() > MAX_PRESENTATION_LENGTH) {
            return false;
        }
        Profile profile = this.profileRepo.find(guildId, userId);
        this.profileRepo.save(profile.withPresentation(trimmed.isEmpty() ? null : trimmed));
        return true;
    }

    public Profile setTags(MemberSnapshot member, Set<Long> tagIds) {
        Set<Long> usable = this.tagService.usableTags(member).stream().map(Tag::id).collect(Collectors.toSet());
        Set<Long> chosen = intersection(tagIds, usable);
        Profile profile = this.profileRepo.find(member.guildId(), member.userId());
        return this.saveTagSets(profile, chosen, TagService.without(profile.excludedTagIds(), chosen),
                profile.requiredTagIds());
    }

    public Profile setExcludedTags(long guildId, long userId, Set<Long> tagIds) {
        Set<Long> excluded = intersection(tagIds, this.existingTagIds(guildId));
        Profile profile = this.profileRepo.find(guildId, userId);
        return this.saveTagSets(profile, TagService.without(profile.tagIds(), excluded), excluded,
                TagService.without(profile.requiredTagIds(), excluded));
    }

    public Profile setRequiredTags(long guildId, long userId, Set<Long> tagIds) {
        Set<Long> required = intersection(tagIds, this.existingTagIds(guildId));
        Profile profile = this.profileRepo.find(guildId, userId);
        return this.saveTagSets(profile, profile.tagIds(), TagService.without(profile.excludedTagIds(), required),
                required);
    }

    public Profile setAnonymous(long guildId, long userId, boolean anonymous) {
        Profile updated = this.profileRepo.find(guildId, userId).withAnonymous(anonymous);
        this.profileRepo.save(updated);
        return updated;
    }

    public Profile setRequirePhoto(long guildId, long userId, boolean requirePhoto) {
        Profile updated = this.profileRepo.find(guildId, userId).withRequirePhoto(requirePhoto);
        this.profileRepo.save(updated);
        return updated;
    }

    public boolean setMinCompatibility(long guildId, long userId, Integer percent) {
        if (percent != null && (percent < 0 || percent > MAX_COMPATIBILITY)) {
            return false;
        }
        Profile profile = this.profileRepo.find(guildId, userId);
        this.profileRepo.save(profile.withMinCompatibility(ConditionRules.positiveOrNull(percent)));
        return true;
    }

    public Profile setPartnerConditions(long guildId, long userId, Integer minAccountAgeDays, Integer minMemberDays,
            Integer minMessages, ActivityPeriod period) {
        MemberConditions conditions = ConditionRules.withMinAccountAge(MemberConditions.NONE, minAccountAgeDays);
        conditions = ConditionRules.withMinMemberDays(conditions, minMemberDays);
        conditions = ConditionRules.withActivity(conditions, minMessages, period);
        Profile updated = this.profileRepo.find(guildId, userId).withPartnerConditions(conditions);
        this.profileRepo.save(updated);
        return updated;
    }

    private Profile saveTagSets(Profile profile, Set<Long> chosen, Set<Long> excluded, Set<Long> required) {
        Profile updated = profile.withTagSets(chosen, excluded, required);
        if (chosen.isEmpty()) {
            updated = updated.withSearching(false);
        }
        this.profileRepo.save(updated);
        return updated;
    }

    private Set<Long> existingTagIds(long guildId) {
        return this.tagService.listTags(guildId).stream().map(Tag::id).collect(Collectors.toSet());
    }

    private static Set<Long> intersection(Set<Long> requested, Set<Long> allowed) {
        Set<Long> result = new HashSet<>(requested);
        result.retainAll(allowed);
        return result;
    }
}
