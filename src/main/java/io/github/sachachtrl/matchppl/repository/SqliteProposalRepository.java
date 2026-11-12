package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.ProposalAnswer;
import io.github.sachachtrl.matchppl.model.ProposalStatus;
import io.github.sachachtrl.matchppl.model.UserPair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SqliteProposalRepository implements ProposalRepository {
    private static final String COLUMNS = """
            id, guild_id, first_user_id, second_user_id, created_at, deadline,
            first_answer, second_answer, status, first_message_id, second_message_id
            """;
    private static final String CREATE_SQL = """
            INSERT INTO proposal (guild_id, first_user_id, second_user_id, created_at, deadline,
                                  first_answer, second_answer, status)
            VALUES (?, ?, ?, ?, ?, 'PENDING', 'PENDING', 'OPEN')
            RETURNING id
            """;
    private static final String FIND_SQL = "SELECT " + COLUMNS + "FROM proposal WHERE id = ?";
    private static final String FIND_EXPIRED_SQL = "SELECT " + COLUMNS + """
            FROM proposal
            WHERE status = 'OPEN' AND deadline <= ?
            """;
    private static final String FIND_OPEN_SQL = "SELECT " + COLUMNS + """
            FROM proposal
            WHERE status = 'OPEN'
            """;
    private static final String FIND_OPEN_INVOLVING_SQL = "SELECT " + COLUMNS + """
            FROM proposal
            WHERE guild_id = ? AND status = 'OPEN' AND (first_user_id = ? OR second_user_id = ?)
            """;
    private static final String FIND_BUSY_SQL = """
            SELECT first_user_id FROM proposal
            WHERE guild_id = ? AND status = 'OPEN' AND first_answer IN ('PENDING', 'ACCEPTED')
            UNION
            SELECT second_user_id FROM proposal
            WHERE guild_id = ? AND status = 'OPEN' AND second_answer IN ('PENDING', 'ACCEPTED')
            """;
    private static final String WHERE_BLOCKING = """
            WHERE guild_id = ? AND unblocked = 0 AND (
                status = 'NO_MATCH'
                OR (status IN ('OPEN', 'INTERRUPTED') AND (first_answer IN ('SKIPPED', 'CANCELLED')
                                                           OR second_answer IN ('SKIPPED', 'CANCELLED'))))
            """;
    private static final String FIND_BLOCKED_SQL = """
            SELECT first_user_id, second_user_id FROM proposal
            """ + WHERE_BLOCKING;
    private static final String COUNT_BLOCKED_SQL = """
            SELECT COUNT(DISTINCT MIN(first_user_id, second_user_id) || ':' || MAX(first_user_id, second_user_id))
            FROM proposal
            """ + WHERE_BLOCKING;
    private static final String COUNT_BLOCKED_INVOLVING_SQL = COUNT_BLOCKED_SQL + """
            AND (first_user_id = ? OR second_user_id = ?)
            """;
    private static final String UNBLOCK_SQL = """
            UPDATE proposal SET unblocked = 1
            """ + WHERE_BLOCKING;
    private static final String UNBLOCK_INVOLVING_SQL = UNBLOCK_SQL + """
            AND (first_user_id = ? OR second_user_id = ?)
            """;
    private static final String UNBLOCK_PAIR_SQL = UNBLOCK_SQL + """
            AND ((first_user_id = ? AND second_user_id = ?) OR (first_user_id = ? AND second_user_id = ?))
            """;
    private static final String UPDATE_FIRST_ANSWER_SQL = """
            UPDATE proposal SET first_answer = ?
            WHERE id = ? AND status = 'OPEN' AND first_user_id = ? AND first_answer = ?
            """;
    private static final String UPDATE_SECOND_ANSWER_SQL = """
            UPDATE proposal SET second_answer = ?
            WHERE id = ? AND status = 'OPEN' AND second_user_id = ? AND second_answer = ?
            """;
    private static final String MARK_MATCHED_SQL = """
            UPDATE proposal SET status = 'MATCHED'
            WHERE id = ? AND status = 'OPEN' AND first_answer = 'ACCEPTED' AND second_answer = 'ACCEPTED'
            """;
    private static final String CLOSE_SQL = """
            UPDATE proposal SET status = ? WHERE id = ? AND status = 'OPEN'
            """;
    private static final String SET_FIRST_MESSAGE_SQL = """
            UPDATE proposal SET first_message_id = ? WHERE id = ? AND first_user_id = ?
            """;
    private static final String SET_SECOND_MESSAGE_SQL = """
            UPDATE proposal SET second_message_id = ? WHERE id = ? AND second_user_id = ?
            """;

    private final Connection connection;

    public SqliteProposalRepository(Database database) {
        this.connection = database.getConnection();
    }

    @Override
    public Proposal create(long guildId, long firstUserId, long secondUserId, Instant createdAt, Instant deadline) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(CREATE_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, firstUserId);
                statement.setLong(3, secondUserId);
                statement.setLong(4, createdAt.toEpochMilli());
                statement.setLong(5, deadline.toEpochMilli());
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    Instant storedCreatedAt = Instant.ofEpochMilli(createdAt.toEpochMilli());
                    Instant storedDeadline = Instant.ofEpochMilli(deadline.toEpochMilli());
                    return new Proposal(result.getLong(1), guildId, firstUserId, secondUserId, storedCreatedAt,
                            storedDeadline, ProposalAnswer.PENDING, ProposalAnswer.PENDING, ProposalStatus.OPEN,
                            null, null);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to create proposal", e);
            }
        }
    }

    @Override
    public Optional<Proposal> findById(long proposalId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_SQL)) {
                statement.setLong(1, proposalId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readProposal(result)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find proposal", e);
            }
        }
    }

    @Override
    public Set<Long> findBusyUsers(long guildId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_BUSY_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    Set<Long> users = new HashSet<>();
                    while (result.next()) {
                        users.add(result.getLong(1));
                    }
                    return users;
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find busy users", e);
            }
        }
    }

    @Override
    public Set<UserPair> findBlockedPairs(long guildId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_BLOCKED_SQL)) {
                statement.setLong(1, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    Set<UserPair> pairs = new HashSet<>();
                    while (result.next()) {
                        pairs.add(new UserPair(result.getLong(1), result.getLong(2)));
                    }
                    return pairs;
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find blocked pairs", e);
            }
        }
    }

    @Override
    public int countBlockedPairs(long guildId) {
        return this.executeCount(COUNT_BLOCKED_SQL, guildId, null);
    }

    @Override
    public int countBlockedPairsInvolving(long guildId, long userId) {
        return this.executeCount(COUNT_BLOCKED_INVOLVING_SQL, guildId, userId);
    }

    @Override
    public void unblockAll(long guildId) {
        this.executeUnblock(UNBLOCK_SQL, guildId, null);
    }

    @Override
    public void unblockInvolving(long guildId, long userId) {
        this.executeUnblock(UNBLOCK_INVOLVING_SQL, guildId, userId);
    }

    @Override
    public boolean unblockPair(long guildId, long firstUserId, long secondUserId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(UNBLOCK_PAIR_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, firstUserId);
                statement.setLong(3, secondUserId);
                statement.setLong(4, secondUserId);
                statement.setLong(5, firstUserId);
                return statement.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RepositoryException("Failed to unblock pair", e);
            }
        }
    }

    @Override
    public List<Proposal> findExpired(Instant now) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_EXPIRED_SQL)) {
                statement.setLong(1, now.toEpochMilli());
                return readProposals(statement);
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find expired proposals", e);
            }
        }
    }

    @Override
    public List<Proposal> findOpen() {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_OPEN_SQL)) {
                return readProposals(statement);
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find open proposals", e);
            }
        }
    }

    @Override
    public List<Proposal> findOpenInvolving(long guildId, long userId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_OPEN_INVOLVING_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, userId);
                statement.setLong(3, userId);
                return readProposals(statement);
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find open proposals", e);
            }
        }
    }

    @Override
    public boolean updateAnswer(long proposalId, long userId, ProposalAnswer expected, ProposalAnswer next) {
        synchronized (this.connection) {
            try {
                return this.executeAnswerUpdate(UPDATE_FIRST_ANSWER_SQL, proposalId, userId, expected, next)
                        || this.executeAnswerUpdate(UPDATE_SECOND_ANSWER_SQL, proposalId, userId, expected, next);
            } catch (SQLException e) {
                throw new RepositoryException("Failed to update answer", e);
            }
        }
    }

    @Override
    public boolean markMatched(long proposalId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(MARK_MATCHED_SQL)) {
                statement.setLong(1, proposalId);
                return statement.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RepositoryException("Failed to mark proposal as matched", e);
            }
        }
    }

    @Override
    public boolean close(long proposalId, ProposalStatus status) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(CLOSE_SQL)) {
                statement.setString(1, status.name());
                statement.setLong(2, proposalId);
                return statement.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RepositoryException("Failed to close proposal", e);
            }
        }
    }

    @Override
    public void setMessageId(long proposalId, long userId, long messageId) {
        synchronized (this.connection) {
            try {
                if (!this.executeMessageUpdate(SET_FIRST_MESSAGE_SQL, proposalId, userId, messageId)) {
                    this.executeMessageUpdate(SET_SECOND_MESSAGE_SQL, proposalId, userId, messageId);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to save proposal message", e);
            }
        }
    }

    private int executeCount(String sql, long guildId, Long userId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
                setGuildAndUser(statement, guildId, userId);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to count blocked pairs", e);
            }
        }
    }

    private void executeUnblock(String sql, long guildId, Long userId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
                setGuildAndUser(statement, guildId, userId);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to unblock pairs", e);
            }
        }
    }

    private static void setGuildAndUser(PreparedStatement statement, long guildId, Long userId) throws SQLException {
        statement.setLong(1, guildId);
        if (userId != null) {
            statement.setLong(2, userId);
            statement.setLong(3, userId);
        }
    }

    private boolean executeAnswerUpdate(String sql, long proposalId, long userId, ProposalAnswer expected,
            ProposalAnswer next) throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, next.name());
            statement.setLong(2, proposalId);
            statement.setLong(3, userId);
            statement.setString(4, expected.name());
            return statement.executeUpdate() > 0;
        }
    }

    private boolean executeMessageUpdate(String sql, long proposalId, long userId, long messageId)
            throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setLong(1, messageId);
            statement.setLong(2, proposalId);
            statement.setLong(3, userId);
            return statement.executeUpdate() > 0;
        }
    }

    private static List<Proposal> readProposals(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            List<Proposal> proposals = new ArrayList<>();
            while (result.next()) {
                proposals.add(readProposal(result));
            }
            return proposals;
        }
    }

    private static Proposal readProposal(ResultSet result) throws SQLException {
        return new Proposal(
                result.getLong(1),
                result.getLong(2),
                result.getLong(3),
                result.getLong(4),
                Instant.ofEpochMilli(result.getLong(5)),
                Instant.ofEpochMilli(result.getLong(6)),
                ProposalAnswer.valueOf(result.getString(7)),
                ProposalAnswer.valueOf(result.getString(8)),
                ProposalStatus.valueOf(result.getString(9)),
                getNullableLong(result, 10),
                getNullableLong(result, 11));
    }

    private static Long getNullableLong(ResultSet result, int column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }
}
