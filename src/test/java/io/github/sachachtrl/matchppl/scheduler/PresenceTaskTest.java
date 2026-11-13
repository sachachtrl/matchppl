package io.github.sachachtrl.matchppl.scheduler;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceTaskTest {
    private static final int MAX_STATUS_LENGTH = 128;

    @Test
    void statusesFollowEachOtherInALoop() {
        List<String> shown = new ArrayList<>();
        PresenceTask task = new PresenceTask(List.of("first", "second", "third"), shown::add);

        for (int i = 0; i < 4; i++) {
            task.run();
        }

        assertEquals(List.of("first", "second", "third", "first"), shown);
    }

    @Test
    void theStatusesFileIsValidForDiscord() {
        List<String> statuses = PresenceTask.loadStatuses();

        assertTrue(statuses.size() > 1);
        assertTrue(statuses.stream().allMatch(status -> status.length() <= MAX_STATUS_LENGTH));
        assertEquals("currently matching two strangers…", statuses.getFirst());
    }

    @Test
    void anEmptyListIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new PresenceTask(List.of(), status -> { }));
    }
}
