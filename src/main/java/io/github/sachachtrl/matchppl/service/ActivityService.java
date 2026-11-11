package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.repository.ActivityRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class ActivityService {
    private static final LocalDate DISCORD_EPOCH = LocalDate.of(2015, 1, 1);

    private final ActivityRepository activityRepo;

    public ActivityService(ActivityRepository activityRepo) {
        this.activityRepo = activityRepo;
    }

    public void recordMessage(long guildId, long userId, Instant sentAt) {
        this.activityRepo.incrementMessageCount(guildId, userId, LocalDate.ofInstant(sentAt, ZoneOffset.UTC));
    }

    public int countMessages(long guildId, long userId, ActivityPeriod period, LocalDate today) {
        LocalDate from = switch (period) {
            case TWO_WEEKS -> today.minusDays(13);
            case THIRTY_DAYS -> today.minusDays(29);
            case ALL_TIME -> DISCORD_EPOCH;
        };
        return this.activityRepo.countMessages(guildId, userId, from, today);
    }
}
