package io.github.sachachtrl.matchppl.repository;

import java.util.Set;

public interface GuildConfigRepository {
    boolean addAdminRole(long guildId, long roleId);

    boolean removeAdminRole(long guildId, long roleId);

    Set<Long> findAdminRoles(long guildId);
}
