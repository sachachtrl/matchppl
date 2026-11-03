package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.repository.ActivityRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class ActivityService {
    private final ActivityRepository activityRepo;

    public ActivityService(ActivityRepository activityRepo) {
        this.activityRepo = activityRepo;
    }

    public void recordMessage(long guildId, long userId, Instant sentAt) {
        activityRepo.incrementMessageCount(guildId, userId, LocalDate.ofInstant(sentAt, ZoneOffset.UTC));
    }
}
