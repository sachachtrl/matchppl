package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.repository.ProfileRepository;

public class ProfileService {
    private final ProfileRepository profileRepo;

    public ProfileService(ProfileRepository profileRepo) {
        this.profileRepo = profileRepo;
    }

    public boolean isSearching(long guildId, long userId) {
        return profileRepo.isSearching(guildId, userId);
    }

    public void setSearching(long guildId, long userId, boolean searching) {
        profileRepo.setSearching(guildId, userId, searching);
    }
}
