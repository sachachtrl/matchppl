package io.github.sachachtrl.matchppl.repository;

import java.time.Instant;
import java.util.Optional;

public interface HeartbeatRepository {
    Optional<Instant> findLast();

    void save(Instant beatAt);
}
