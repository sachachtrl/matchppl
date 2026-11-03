package io.github.sachachtrl.matchppl.repository;

public interface GuildConfigRepository {
    boolean addAdminRole(long guildId, long roleId);

    boolean removeAdminRole(long guildId, long roleId);
}
