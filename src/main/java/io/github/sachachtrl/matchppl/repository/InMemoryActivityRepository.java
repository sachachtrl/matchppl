package io.github.sachachtrl.matchppl.repository;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActivityRepository implements ActivityRepository {
    private record Key(long guildId, long userId, LocalDate day) {}

    private record TotalKey(long guildId, long userId) {}

    private final Map<Key, Integer> counts = new ConcurrentHashMap<>();
    private final Map<TotalKey, Integer> totals = new ConcurrentHashMap<>();

    @Override
    public void incrementMessageCount(long guildId, long userId, LocalDate day) {
        this.counts.merge(new Key(guildId, userId, day), 1, Integer::sum);
        this.totals.merge(new TotalKey(guildId, userId), 1, Integer::sum);
    }

    @Override
    public int countMessages(long guildId, long userId, LocalDate from, LocalDate to) {
        int res = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            res += this.counts.getOrDefault(new Key(guildId, userId, day), 0);
        }
        return res;
    }

    @Override
    public int countAllMessages(long guildId, long userId) {
        return this.totals.getOrDefault(new TotalKey(guildId, userId), 0);
    }

    @Override
    public int deleteDaysBefore(LocalDate day) {
        int before = this.counts.size();
        this.counts.keySet().removeIf(key -> key.day().isBefore(day));
        return before - this.counts.size();
    }
}
