package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.Profile;

import java.util.List;

public interface ProfileRepository {
    Profile find(long guildId, long userId);

    List<Profile> findSearching(long guildId);

    int countSearching(long guildId);

    int stopAllSearches(long guildId);

    void save(Profile profile);

    void delete(long guildId, long userId);
}
