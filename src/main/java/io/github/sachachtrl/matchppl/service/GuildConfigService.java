package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.GuildSettings;
import io.github.sachachtrl.matchppl.repository.GuildConfigRepository;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.function.UnaryOperator;

public class GuildConfigService {
    public static final Duration MIN_RESPONSE_DELAY = Duration.ofMinutes(1);
    public static final Duration MAX_RESPONSE_DELAY = Duration.ofDays(7);
    public static final Duration MIN_MATCHING_INTERVAL = Duration.ofMinutes(1);
    public static final Duration MAX_MATCHING_INTERVAL = Duration.ofDays(1);
    public static final Duration MIN_ROOM_LIFETIME = Duration.ofMinutes(10);
    public static final Duration MAX_ROOM_LIFETIME = Duration.ofDays(30);
    public static final int MAX_ROOM_PREFIX_LENGTH = 40;

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

    public GuildSettings settings(long guildId) {
        return this.guildConfigRepo.findSettings(guildId);
    }

    public Duration responseDelay(long guildId) {
        return this.settings(guildId).responseDelay();
    }

    public Duration matchingInterval(long guildId) {
        return this.settings(guildId).matchingInterval();
    }

    public Duration roomLifetime(long guildId) {
        return this.settings(guildId).roomLifetime();
    }

    public boolean setResponseDelay(long guildId, Duration delay) {
        return within(delay, MIN_RESPONSE_DELAY, MAX_RESPONSE_DELAY)
                && this.update(guildId, settings -> settings.withResponseDelay(delay));
    }

    public boolean setMatchingInterval(long guildId, Duration interval) {
        return within(interval, MIN_MATCHING_INTERVAL, MAX_MATCHING_INTERVAL)
                && this.update(guildId, settings -> settings.withMatchingInterval(interval));
    }

    public boolean setRoomLifetime(long guildId, Duration lifetime) {
        return within(lifetime, MIN_ROOM_LIFETIME, MAX_ROOM_LIFETIME)
                && this.update(guildId, settings -> settings.withRoomLifetime(lifetime));
    }

    public boolean setRoomPrefix(long guildId, String prefix) {
        String trimmed = prefix == null ? "" : prefix.strip();
        if (trimmed.length() > MAX_ROOM_PREFIX_LENGTH) {
            return false;
        }
        String value = trimmed.isEmpty() ? GuildSettings.DEFAULT_ROOM_PREFIX : trimmed;
        return this.update(guildId, settings -> settings.withRoomPrefix(value));
    }

    public void setRoomsEnabled(long guildId, boolean enabled) {
        this.update(guildId, settings -> settings.withRoomsEnabled(enabled));
    }

    public void setCategory(long guildId, Long categoryId) {
        this.update(guildId, settings -> settings.withCategoryId(categoryId));
    }

    public void setLogChannel(long guildId, Long channelId) {
        this.update(guildId, settings -> settings.withLogChannelId(channelId));
    }

    private boolean update(long guildId, UnaryOperator<GuildSettings> change) {
        this.guildConfigRepo.saveSettings(change.apply(this.settings(guildId)));
        return true;
    }

    private static boolean within(Duration value, Duration min, Duration max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}
