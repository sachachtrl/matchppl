package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.repository.GuildConfigRepository;

import java.util.Collection;
import java.util.Set;

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

    public boolean isAdmin(long guildId, Collection<Long> memberRoleIds, boolean canManageServer) {
        if (canManageServer) {
            return true;
        }
        Set<Long> adminRoles = this.guildConfigRepo.findAdminRoles(guildId);
        return memberRoleIds.stream().anyMatch(adminRoles::contains);
    }
}
