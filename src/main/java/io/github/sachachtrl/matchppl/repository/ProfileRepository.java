package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.Profile;

public interface ProfileRepository {
    Profile find(long guildId, long userId);

    void save(Profile profile);
}
