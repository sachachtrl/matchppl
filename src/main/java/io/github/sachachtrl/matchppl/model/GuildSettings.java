package io.github.sachachtrl.matchppl.model;

import java.time.Duration;

public record GuildSettings(
        long guildId,
        Duration responseDelay,
        Duration matchingInterval,
        boolean roomsEnabled,
        Duration roomLifetime,
        String roomPrefix,
        Long categoryId,
        Long logChannelId) {

    public static final Duration DEFAULT_RESPONSE_DELAY = Duration.ofHours(24);
    public static final Duration DEFAULT_MATCHING_INTERVAL = Duration.ofMinutes(1);
    public static final Duration DEFAULT_ROOM_LIFETIME = Duration.ofHours(24);
    public static final String DEFAULT_ROOM_PREFIX = "Match";

    public static GuildSettings defaults(long guildId) {
        return new GuildSettings(guildId, DEFAULT_RESPONSE_DELAY, DEFAULT_MATCHING_INTERVAL, true,
                DEFAULT_ROOM_LIFETIME, DEFAULT_ROOM_PREFIX, null, null);
    }

    public GuildSettings withResponseDelay(Duration value) {
        return new GuildSettings(this.guildId, value, this.matchingInterval, this.roomsEnabled, this.roomLifetime,
                this.roomPrefix, this.categoryId, this.logChannelId);
    }

    public GuildSettings withMatchingInterval(Duration value) {
        return new GuildSettings(this.guildId, this.responseDelay, value, this.roomsEnabled, this.roomLifetime,
                this.roomPrefix, this.categoryId, this.logChannelId);
    }

    public GuildSettings withRoomsEnabled(boolean value) {
        return new GuildSettings(this.guildId, this.responseDelay, this.matchingInterval, value, this.roomLifetime,
                this.roomPrefix, this.categoryId, this.logChannelId);
    }

    public GuildSettings withRoomLifetime(Duration value) {
        return new GuildSettings(this.guildId, this.responseDelay, this.matchingInterval, this.roomsEnabled, value,
                this.roomPrefix, this.categoryId, this.logChannelId);
    }

    public GuildSettings withRoomPrefix(String value) {
        return new GuildSettings(this.guildId, this.responseDelay, this.matchingInterval, this.roomsEnabled,
                this.roomLifetime, value, this.categoryId, this.logChannelId);
    }

    public GuildSettings withCategoryId(Long value) {
        return new GuildSettings(this.guildId, this.responseDelay, this.matchingInterval, this.roomsEnabled,
                this.roomLifetime, this.roomPrefix, value, this.logChannelId);
    }

    public GuildSettings withLogChannelId(Long value) {
        return new GuildSettings(this.guildId, this.responseDelay, this.matchingInterval, this.roomsEnabled,
                this.roomLifetime, this.roomPrefix, this.categoryId, value);
    }
}
