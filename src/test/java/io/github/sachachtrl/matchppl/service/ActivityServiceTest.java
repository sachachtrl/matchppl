package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.repository.InMemoryActivityRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityServiceTest {
    private static final long GUILD = 1L;
    private static final long USER = 10L;

    private InMemoryActivityRepository repository;
    private ActivityService service;

    @BeforeEach
    void setUp() {
        this.repository = new InMemoryActivityRepository();
        this.service = new ActivityService(this.repository);
    }

    @Test
    void messageIsCountedOnItsUtcDay() {
        Instant sentAt = Instant.parse("2026-11-03T15:00:00Z");

        this.service.recordMessage(GUILD, USER, sentAt);

        LocalDate day = LocalDate.of(2026, 11, 3);
        assertEquals(1, this.repository.countMessages(GUILD, USER, day, day));
    }

    @Test
    void thePurgeKeepsExactlyTheLastThirtyDaysAndTheAllTimeTotal() {
        LocalDate today = LocalDate.of(2026, 11, 12);
        for (int daysAgo : new int[] {0, 29, 30, 100}) {
            this.service.recordMessage(GUILD, USER, today.minusDays(daysAgo).atStartOfDay().toInstant(ZoneOffset.UTC));
        }

        assertEquals(2, this.service.purgeOldDays(today));

        assertEquals(2, this.service.countMessages(GUILD, USER, ActivityPeriod.THIRTY_DAYS, today));
        assertEquals(1, this.service.countMessages(GUILD, USER, ActivityPeriod.TWO_WEEKS, today));
        assertEquals(4, this.service.countMessages(GUILD, USER, ActivityPeriod.ALL_TIME, today));
    }

    @Test
    void messageAfterMidnightInParisCountsForThePreviousUtcDay() {
        Instant sentAt = OffsetDateTime.parse("2026-11-04T00:30:00+01:00").toInstant();

        this.service.recordMessage(GUILD, USER, sentAt);

        LocalDate utcDay = LocalDate.of(2026, 11, 3);
        LocalDate parisDay = LocalDate.of(2026, 11, 4);
        assertEquals(1, this.repository.countMessages(GUILD, USER, utcDay, utcDay));
        assertEquals(0, this.repository.countMessages(GUILD, USER, parisDay, parisDay));
    }
}
