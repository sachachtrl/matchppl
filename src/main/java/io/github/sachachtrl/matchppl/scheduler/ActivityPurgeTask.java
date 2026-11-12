package io.github.sachachtrl.matchppl.scheduler;

import io.github.sachachtrl.matchppl.service.ActivityService;

import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityPurgeTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ActivityPurgeTask.class);

    private final ActivityService activityService;
    private final Clock clock;

    public ActivityPurgeTask(ActivityService activityService, Clock clock) {
        this.activityService = activityService;
        this.clock = clock;
    }

    @Override
    public void run() {
        try {
            int deleted = this.activityService.purgeOldDays(LocalDate.now(this.clock));
            log.info("Old message counts purged: rows={}", deleted);
        } catch (RuntimeException e) {
            log.error("Failed to purge old message counts", e);
        }
    }
}
