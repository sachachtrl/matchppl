package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.repository.GuildDataRepository;

public class GuildDataService {
    private final GuildDataRepository guildDataRepo;

    public GuildDataService(GuildDataRepository guildDataRepo) {
        this.guildDataRepo = guildDataRepo;
    }

    public void forgetGuild(long guildId) {
        this.guildDataRepo.deleteGuild(guildId);
    }
}
