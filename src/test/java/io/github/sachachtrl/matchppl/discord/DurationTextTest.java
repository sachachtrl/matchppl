package io.github.sachachtrl.matchppl.discord;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationTextTest {
    @Test
    void readsEveryUnitAndTheirCombinations() {
        assertEquals(Duration.ofHours(24), DurationText.parse("24h"));
        assertEquals(Duration.ofMinutes(90), DurationText.parse("90m"));
        assertEquals(Duration.ofDays(1).plusHours(12), DurationText.parse("1d12h"));
        assertEquals(Duration.ofDays(2).plusHours(3).plusMinutes(4), DurationText.parse(" 2D 3h 4M "));
    }

    @Test
    void rejectsAnythingElse() {
        assertThrows(IllegalArgumentException.class, () -> DurationText.parse(""));
        assertThrows(IllegalArgumentException.class, () -> DurationText.parse("24"));
        assertThrows(IllegalArgumentException.class, () -> DurationText.parse("tomorrow"));
        assertThrows(IllegalArgumentException.class, () -> DurationText.parse("12m3h"));
    }

    @Test
    void writesTheShortestInputBack() {
        assertEquals("1d", DurationText.toInput(Duration.ofHours(24)));
        assertEquals("1h30m", DurationText.toInput(Duration.ofMinutes(90)));
        assertEquals("0m", DurationText.toInput(Duration.ZERO));
    }

    @Test
    void formatsForHumans() {
        assertEquals("1 day 2 hours", DurationText.format(Duration.ofHours(26), Messages.DEFAULT_LOCALE));
        assertEquals("1 minute", DurationText.format(Duration.ofMinutes(1), Messages.DEFAULT_LOCALE));
        assertEquals("0 minutes", DurationText.format(Duration.ZERO, Messages.DEFAULT_LOCALE));
    }
}
