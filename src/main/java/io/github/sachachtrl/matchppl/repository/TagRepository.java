package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.Tag;

import java.util.List;
import java.util.Optional;

public interface TagRepository {
    Tag create(long guildId, String name, String emoji);

    Optional<Tag> findById(long guildId, long tagId);

    List<Tag> findByGuild(long guildId);

    boolean delete(long guildId, long tagId);

    boolean existsByName(long guildId, String name);

    void update(Tag tag);
}
