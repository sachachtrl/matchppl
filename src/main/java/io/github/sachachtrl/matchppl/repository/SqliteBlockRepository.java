package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.UserPair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SqliteBlockRepository implements BlockRepository {
    private static final String BLOCK_SQL = """
            INSERT OR IGNORE INTO member_block (guild_id, blocker_id, blocked_id, created_at) VALUES (?, ?, ?, ?)
            """;
    private static final String UNBLOCK_SQL = """
            DELETE FROM member_block WHERE guild_id = ? AND blocker_id = ? AND blocked_id = ?
            """;
    private static final String FIND_BLOCKED_SQL = """
            SELECT blocked_id FROM member_block WHERE guild_id = ? AND blocker_id = ? ORDER BY created_at
            """;
    private static final String IS_BLOCKED_SQL = """
            SELECT 1 FROM member_block
            WHERE guild_id = ? AND ((blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?))
            """;
    private static final String FIND_PAIRS_SQL = """
            SELECT blocker_id, blocked_id FROM member_block WHERE guild_id = ?
            """;
    private static final String DELETE_BY_SQL = """
            DELETE FROM member_block WHERE guild_id = ? AND blocker_id = ?
            """;

    private final Connection connection;

    public SqliteBlockRepository(Database database) {
        this.connection = database.getConnection();
    }

    @Override
    public boolean block(long guildId, long blockerId, long blockedId, Instant createdAt) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(BLOCK_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, blockerId);
                statement.setLong(3, blockedId);
                statement.setLong(4, createdAt.toEpochMilli());
                return statement.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RepositoryException("Failed to block member", e);
            }
        }
    }

    @Override
    public boolean unblock(long guildId, long blockerId, long blockedId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(UNBLOCK_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, blockerId);
                statement.setLong(3, blockedId);
                return statement.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RepositoryException("Failed to unblock member", e);
            }
        }
    }

    @Override
    public boolean isBlocked(long guildId, long firstUserId, long secondUserId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(IS_BLOCKED_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, firstUserId);
                statement.setLong(3, secondUserId);
                statement.setLong(4, secondUserId);
                statement.setLong(5, firstUserId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to check member block", e);
            }
        }
    }

    @Override
    public List<Long> findBlocked(long guildId, long blockerId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_BLOCKED_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, blockerId);
                try (ResultSet result = statement.executeQuery()) {
                    List<Long> blocked = new ArrayList<>();
                    while (result.next()) {
                        blocked.add(result.getLong(1));
                    }
                    return blocked;
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find blocked members", e);
            }
        }
    }

    @Override
    public Set<UserPair> findBlockedPairs(long guildId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_PAIRS_SQL)) {
                statement.setLong(1, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    Set<UserPair> pairs = new HashSet<>();
                    while (result.next()) {
                        pairs.add(new UserPair(result.getLong(1), result.getLong(2)));
                    }
                    return pairs;
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find member blocks", e);
            }
        }
    }

    @Override
    public void deleteBlocksBy(long guildId, long blockerId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(DELETE_BY_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, blockerId);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to delete member blocks", e);
            }
        }
    }
}
