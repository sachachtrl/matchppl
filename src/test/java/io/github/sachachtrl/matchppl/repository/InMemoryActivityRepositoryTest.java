package io.github.sachachtrl.matchppl.repository;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryActivityRepositoryTest {
    private static final long GUILD = 1L;
    private static final long OTHER_GUILD = 2L;
    private static final long USER = 10L;
    private static final long OTHER_USER = 20L;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);

    private InMemoryActivityRepository repository;

    @BeforeEach
    void setUp() {
        this.repository = new InMemoryActivityRepository();
    }

    @Test
    void countIsZeroWithoutMessages() {
        assertEquals(0, this.repository.countMessages(GUILD, USER, DAY, DAY));
    }

    @Test
    void incrementsAccumulateOnTheSameDay() {
        this.repository.incrementMessageCount(GUILD, USER, DAY);
        this.repository.incrementMessageCount(GUILD, USER, DAY);
        this.repository.incrementMessageCount(GUILD, USER, DAY);

        assertEquals(3, this.repository.countMessages(GUILD, USER, DAY, DAY));
    }

    @Test
    void countIncludesBothBoundsOfThePeriod() {
        this.repository.incrementMessageCount(GUILD, USER, DAY.minusDays(1));
        this.repository.incrementMessageCount(GUILD, USER, DAY);
        this.repository.incrementMessageCount(GUILD, USER, DAY.plusDays(1));
        this.repository.incrementMessageCount(GUILD, USER, DAY.plusDays(2));

        assertEquals(3, this.repository.countMessages(GUILD, USER, DAY.minusDays(1), DAY.plusDays(1)));
    }

    @Test
    void countersAreSeparatedByGuildAndUser() {
        this.repository.incrementMessageCount(GUILD, USER, DAY);
        this.repository.incrementMessageCount(OTHER_GUILD, USER, DAY);
        this.repository.incrementMessageCount(GUILD, OTHER_USER, DAY);

        assertEquals(1, this.repository.countMessages(GUILD, USER, DAY, DAY));
    }
}
