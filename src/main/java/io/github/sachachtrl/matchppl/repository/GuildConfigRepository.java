package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.GuildSettings;

import java.util.Set;

public interface GuildConfigRepository {
    boolean addAdminRole(long guildId, long roleId);

    boolean removeAdminRole(long guildId, long roleId);

    Set<Long> findAdminRoles(long guildId);

    GuildSettings findSettings(long guildId);

    void saveSettings(GuildSettings settings);
}
