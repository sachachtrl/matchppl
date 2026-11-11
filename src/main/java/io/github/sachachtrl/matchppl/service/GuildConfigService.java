package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.repository.GuildConfigRepository;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

public class GuildConfigService {
    public static final Duration DEFAULT_RESPONSE_DELAY = Duration.ofHours(24);
    public static final Duration DEFAULT_ROOM_LIFETIME = Duration.ofHours(24);
    public static final Duration DEFAULT_MATCHING_INTERVAL = Duration.ofMinutes(1);

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

    public Duration responseDelay(long guildId) {
        return DEFAULT_RESPONSE_DELAY;
    }

    public Duration roomLifetime(long guildId) {
        return DEFAULT_ROOM_LIFETIME;
    }

    public Duration matchingInterval(long guildId) {
        return DEFAULT_MATCHING_INTERVAL;
    }

    public Set<Long> listAdminRoles(long guildId) {
        return Set.copyOf(this.guildConfigRepo.findAdminRoles(guildId));
    }

    public boolean isAdmin(long guildId, Collection<Long> memberRoleIds, boolean canManageServer) {
        if (canManageServer) {
            return true;
        }
        Set<Long> adminRoles = this.guildConfigRepo.findAdminRoles(guildId);
        return memberRoleIds.stream().anyMatch(adminRoles::contains);
    }
}
