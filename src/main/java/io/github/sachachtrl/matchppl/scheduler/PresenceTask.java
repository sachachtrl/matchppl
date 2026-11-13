package io.github.sachachtrl.matchppl.scheduler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PresenceTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(PresenceTask.class);

    private static final String STATUSES_FILE = "/statuses.txt";

    private final List<String> statuses;
    private final Consumer<String> show;
    private int next;

    public PresenceTask(List<String> statuses, Consumer<String> show) {
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException("At least one status is needed");
        }
        this.statuses = List.copyOf(statuses);
        this.show = show;
    }

    public static List<String> loadStatuses() {
        try (InputStream stream = PresenceTask.class.getResourceAsStream(STATUSES_FILE);
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + STATUSES_FILE, e);
        }
    }

    @Override
    public void run() {
        try {
            this.show.accept(this.statuses.get(this.next));
            this.next = (this.next + 1) % this.statuses.size();
        } catch (RuntimeException e) {
            log.error("Failed to update the bot status", e);
        }
    }
}
