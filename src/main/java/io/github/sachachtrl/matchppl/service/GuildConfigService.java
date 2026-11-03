package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.repository.GuildConfigRepository;

public class GuildConfigService {
    private final GuildConfigRepository guildConfigRepo;

    public GuildConfigService(GuildConfigRepository guildConfigRepo) {
        this.guildConfigRepo = guildConfigRepo;
    }

    public boolean addAdminRole(long guildId, long roleId) {
        return this.guildConfigRepo.addAdminRole(guildId, roleId);
    }

    public boolean removeAdminRole(long guildId, long roleId) {
        return this.guildConfigRepo.removeAdminRole(guildId, roleId);
    }
}
