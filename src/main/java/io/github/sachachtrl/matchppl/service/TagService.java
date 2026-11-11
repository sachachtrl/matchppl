package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.repository.TagRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

public class TagService {
    public static final int MAX_NAME_LENGTH = 32;

    public sealed interface CreateResult permits Created, NameTaken, InvalidName {}

    public record Created(Tag tag) implements CreateResult {}

    public record NameTaken() implements CreateResult {}

    public record InvalidName() implements CreateResult {}

    private final TagRepository tagRepo;
    private final ConditionChecker conditionChecker;

    public TagService(TagRepository tagRepo, ConditionChecker conditionChecker) {
        this.tagRepo = tagRepo;
        this.conditionChecker = conditionChecker;
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

    public List<Tag> listTags(long guildId) {
        return this.tagRepo.findByGuild(guildId);
    }

    public List<Tag> usableTags(MemberSnapshot member) {
        return this.listTags(member.guildId()).stream().filter(tag -> this.canUse(tag, member)).toList();
    }

    public boolean canUse(Tag tag, MemberSnapshot member) {
        boolean hasRequiredRole = tag.requiredRoleIds().isEmpty()
                || tag.requiredRoleIds().stream().anyMatch(member.roleIds()::contains);
        boolean hasForbiddenRole = tag.forbiddenRoleIds().stream().anyMatch(member.roleIds()::contains);
        return hasRequiredRole && !hasForbiddenRole && this.conditionChecker.meets(tag.conditions(), member);
    }

    public boolean deleteTag(long guildId, long tagId) {
        return this.tagRepo.delete(guildId, tagId);
    }

    public Optional<Tag> setMinAccountAge(long guildId, long tagId, Integer days) {
        return this.update(guildId, tagId,
                tag -> tag.withConditions(ConditionRules.withMinAccountAge(tag.conditions(), days)));
    }

    public Optional<Tag> setMinMemberDays(long guildId, long tagId, Integer days) {
        return this.update(guildId, tagId,
                tag -> tag.withConditions(ConditionRules.withMinMemberDays(tag.conditions(), days)));
    }

    public Optional<Tag> setActivity(long guildId, long tagId, Integer messages, ActivityPeriod period) {
        return this.update(guildId, tagId,
                tag -> tag.withConditions(ConditionRules.withActivity(tag.conditions(), messages, period)));
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

    static Set<Long> without(Set<Long> ids, Set<Long> removed) {
        Set<Long> result = new HashSet<>(ids);
        result.removeAll(removed);
        return result;
    }
}
