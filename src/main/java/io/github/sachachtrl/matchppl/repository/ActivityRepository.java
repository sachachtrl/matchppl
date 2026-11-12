package io.github.sachachtrl.matchppl.repository;

import java.time.LocalDate;

public interface ActivityRepository {
    void incrementMessageCount(long guildId, long userId, LocalDate day);

    int countMessages(long guildId, long userId, LocalDate from, LocalDate to);

    int countAllMessages(long guildId, long userId);

    int deleteDaysBefore(LocalDate day);

    void deleteMember(long guildId, long userId);
}
