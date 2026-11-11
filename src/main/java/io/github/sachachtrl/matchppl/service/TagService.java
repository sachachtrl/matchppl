package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.repository.TagRepository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

public class TagService {
    public static final int MAX_NAME_LENGTH = 32;
    public static final ActivityPeriod DEFAULT_ACTIVITY_PERIOD = ActivityPeriod.THIRTY_DAYS;

    public sealed interface CreateResult permits Created, NameTaken, InvalidName {}

    public record Created(Tag tag) implements CreateResult {}

    public record NameTaken() implements CreateResult {}

    public record InvalidName() implements CreateResult {}

    private final TagRepository tagRepo;

    public TagService(TagRepository tagRepo) {
        this.tagRepo = tagRepo;
    }

    public CreateResult createTag(long guildId, String name, String emoji) {
        String trimmedName = name.strip();
        if (trimmedName.isEmpty() || trimmedName.length() > MAX_NAME_LENGTH) {
            return new InvalidName();
        }
        if (this.tagRepo.existsByName(guildId, trimmedName)) {
            return new NameTaken();
        }
        return new Created(this.tagRepo.create(guildId, trimmedName, emoji));
    }

    public Optional<Tag> findTag(long guildId, long tagId) {
        return this.tagRepo.findById(guildId, tagId);
    }

    public Optional<Tag> setMinAccountAge(long guildId, long tagId, Integer days) {
        return this.update(guildId, tagId, tag -> tag.withMinAccountAgeDays(positiveOrNull(days)));
    }

    public Optional<Tag> setMinMemberDays(long guildId, long tagId, Integer days) {
        return this.update(guildId, tagId, tag -> tag.withMinMemberDays(positiveOrNull(days)));
    }

    public Optional<Tag> setActivity(long guildId, long tagId, Integer messages, ActivityPeriod period) {
        Integer minMessages = positiveOrNull(messages);
        ActivityPeriod activityPeriod = minMessages == null ? null : (period == null ? DEFAULT_ACTIVITY_PERIOD : period);
        return this.update(guildId, tagId, tag -> tag.withActivity(minMessages, activityPeriod));
    }

    public Optional<Tag> setRequiredRoles(long guildId, long tagId, Set<Long> roleIds) {
        return this.update(guildId, tagId, tag -> tag.withRoles(roleIds, without(tag.forbiddenRoleIds(), roleIds)));
    }

    public Optional<Tag> setForbiddenRoles(long guildId, long tagId, Set<Long> roleIds) {
        return this.update(guildId, tagId, tag -> tag.withRoles(without(tag.requiredRoleIds(), roleIds), roleIds));
    }

    private Optional<Tag> update(long guildId, long tagId, UnaryOperator<Tag> change) {
        return this.tagRepo.findById(guildId, tagId)
                .map(change)
                .map(tag -> {
                    this.tagRepo.update(tag);
                    return tag;
                });
    }

    private static Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private static Set<Long> without(Set<Long> roleIds, Set<Long> removed) {
        Set<Long> result = new HashSet<>(roleIds);
        result.removeAll(removed);
        return result;
    }
}
