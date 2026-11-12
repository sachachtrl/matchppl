package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.repository.ActivityRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class ActivityService {
    public static final int RETENTION_DAYS = 30;

    private final ActivityRepository activityRepo;

    public ActivityService(ActivityRepository activityRepo) {
        this.activityRepo = activityRepo;
    }

    public void recordMessage(long guildId, long userId, Instant sentAt) {
        this.activityRepo.incrementMessageCount(guildId, userId, LocalDate.ofInstant(sentAt, ZoneOffset.UTC));
    }

    public int countMessages(long guildId, long userId, ActivityPeriod period, LocalDate today) {
        return switch (period) {
            case TWO_WEEKS -> this.activityRepo.countMessages(guildId, userId, today.minusDays(13), today);
            case THIRTY_DAYS -> this.activityRepo.countMessages(guildId, userId, firstKeptDay(today), today);
            case ALL_TIME -> this.activityRepo.countAllMessages(guildId, userId);
        };
    }

    public int purgeOldDays(LocalDate today) {
        return this.activityRepo.deleteDaysBefore(firstKeptDay(today));
    }

    private static LocalDate firstKeptDay(LocalDate today) {
        return today.minusDays(RETENTION_DAYS - 1);
    }
}
