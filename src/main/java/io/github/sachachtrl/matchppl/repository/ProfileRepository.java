package io.github.sachachtrl.matchppl.repository;

public interface ProfileRepository {
    boolean isSearching(long guildId, long userId);
    void setSearching(long guildId, long userId, boolean searching);
}
