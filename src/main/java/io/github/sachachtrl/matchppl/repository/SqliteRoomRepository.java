package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.Room;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SqliteRoomRepository implements RoomRepository {
    private static final String COLUMNS = """
            id, guild_id, proposal_id, first_user_id, second_user_id, number,
            text_channel_id, voice_channel_id, created_at, delete_at, open
            """;
    private static final String CREATE_SQL = """
            INSERT INTO room (guild_id, proposal_id, first_user_id, second_user_id, number, created_at, delete_at,
                              open)
            VALUES (?, ?, ?, ?, ?, ?, ?, 1)
            RETURNING id
            """;
    private static final String FIND_SQL = "SELECT " + COLUMNS + "FROM room WHERE id = ?";
    private static final String FIND_BY_PROPOSAL_SQL = "SELECT " + COLUMNS + "FROM room WHERE proposal_id = ?";
    private static final String FIND_OPEN_BY_CHANNEL_SQL = "SELECT " + COLUMNS + """
            FROM room
            WHERE open = 1 AND (text_channel_id = ? OR voice_channel_id = ?)
            """;
    private static final String FIND_OPEN_INVOLVING_SQL = "SELECT " + COLUMNS + """
            FROM room
            WHERE guild_id = ? AND open = 1 AND (first_user_id = ? OR second_user_id = ?)
            """;
    private static final String FIND_OPEN_NUMBERS_SQL = """
            SELECT number FROM room WHERE guild_id = ? AND open = 1
            """;
    private static final String FIND_BUSY_SQL = """
            SELECT first_user_id FROM room WHERE guild_id = ? AND open = 1
            UNION
            SELECT second_user_id FROM room WHERE guild_id = ? AND open = 1
            """;
    private static final String FIND_DUE_SQL = "SELECT " + COLUMNS + """
            FROM room
            WHERE open = 1 AND delete_at <= ?
            """;
    private static final String FIND_UNFINISHED_SQL = "SELECT " + COLUMNS + """
            FROM room
            WHERE open = 1 AND text_channel_id IS NULL
            """;
    private static final String FIND_REMINDER_DUE_SQL = "SELECT " + COLUMNS + """
            FROM room
            WHERE open = 1 AND reminder_sent = 0 AND delete_at - ? <= ? AND delete_at > ?
                AND delete_at - created_at > ?
            """;
    private static final String SET_CHANNELS_SQL = """
            UPDATE room SET text_channel_id = ?, voice_channel_id = ? WHERE id = ?
            """;
    private static final String MARK_REMINDER_SENT_SQL = """
            UPDATE room SET reminder_sent = 1 WHERE id = ? AND open = 1 AND reminder_sent = 0
            """;
    private static final String CLOSE_SQL = """
            UPDATE room SET open = 0 WHERE id = ? AND open = 1
            """;

    private final Connection connection;

    public SqliteRoomRepository(Database database) {
        this.connection = database.getConnection();
    }

    @Override
    public Room create(long guildId, long proposalId, long firstUserId, long secondUserId, int number,
            Instant createdAt, Instant deleteAt) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(CREATE_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, proposalId);
                statement.setLong(3, firstUserId);
                statement.setLong(4, secondUserId);
                statement.setInt(5, number);
                statement.setLong(6, createdAt.toEpochMilli());
                statement.setLong(7, deleteAt.toEpochMilli());
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return new Room(result.getLong(1), guildId, proposalId, firstUserId, secondUserId, number, null,
                            null, Instant.ofEpochMilli(createdAt.toEpochMilli()),
                            Instant.ofEpochMilli(deleteAt.toEpochMilli()), true);
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to create room", e);
            }
        }
    }

    @Override
    public Optional<Room> findById(long roomId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_SQL)) {
                statement.setLong(1, roomId);
                return readRooms(statement).stream().findFirst();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find room", e);
            }
        }
    }

    @Override
    public Optional<Room> findByProposal(long proposalId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_BY_PROPOSAL_SQL)) {
                statement.setLong(1, proposalId);
                return readRooms(statement).stream().findFirst();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find room by proposal", e);
            }
        }
    }

    @Override
    public Optional<Room> findOpenByChannel(long channelId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_OPEN_BY_CHANNEL_SQL)) {
                statement.setLong(1, channelId);
                statement.setLong(2, channelId);
                return readRooms(statement).stream().findFirst();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find room by channel", e);
            }
        }
    }

    @Override
    public List<Room> findOpenInvolving(long guildId, long userId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_OPEN_INVOLVING_SQL)) {
                statement.setLong(1, guildId);
                statement.setLong(2, userId);
                statement.setLong(3, userId);
                return readRooms(statement);
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find open rooms", e);
            }
        }
    }

    @Override
    public Set<Integer> findOpenNumbers(long guildId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_OPEN_NUMBERS_SQL)) {
                statement.setLong(1, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    Set<Integer> numbers = new HashSet<>();
                    while (result.next()) {
                        numbers.add(result.getInt(1));
                    }
                    return numbers;
                }
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find room numbers", e);
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
                throw new RepositoryException("Failed to find users with a room", e);
            }
        }
    }

    @Override
    public List<Room> findDue(Instant now) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_DUE_SQL)) {
                statement.setLong(1, now.toEpochMilli());
                return readRooms(statement);
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find due rooms", e);
            }
        }
    }

    @Override
    public List<Room> findOpenWithoutChannels() {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_UNFINISHED_SQL)) {
                return readRooms(statement);
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find unfinished rooms", e);
            }
        }
    }

    @Override
    public List<Room> findReminderDue(Instant now, Duration notice) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(FIND_REMINDER_DUE_SQL)) {
                statement.setLong(1, notice.toMillis());
                statement.setLong(2, now.toEpochMilli());
                statement.setLong(3, now.toEpochMilli());
                statement.setLong(4, notice.toMillis());
                return readRooms(statement);
            } catch (SQLException e) {
                throw new RepositoryException("Failed to find rooms to remind", e);
            }
        }
    }

    @Override
    public void setChannels(long roomId, long textChannelId, long voiceChannelId) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(SET_CHANNELS_SQL)) {
                statement.setLong(1, textChannelId);
                statement.setLong(2, voiceChannelId);
                statement.setLong(3, roomId);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to save room channels", e);
            }
        }
    }

    @Override
    public boolean markReminderSent(long roomId) {
        return this.executeUpdate(MARK_REMINDER_SENT_SQL, roomId, "Failed to mark room reminder");
    }

    @Override
    public boolean close(long roomId) {
        return this.executeUpdate(CLOSE_SQL, roomId, "Failed to close room");
    }

    private boolean executeUpdate(String sql, long roomId, String errorMessage) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
                statement.setLong(1, roomId);
                return statement.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RepositoryException(errorMessage, e);
            }
        }
    }

    private static List<Room> readRooms(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            List<Room> rooms = new ArrayList<>();
            while (result.next()) {
                rooms.add(new Room(
                        result.getLong(1),
                        result.getLong(2),
                        result.getLong(3),
                        result.getLong(4),
                        result.getLong(5),
                        result.getInt(6),
                        getNullableLong(result, 7),
                        getNullableLong(result, 8),
                        Instant.ofEpochMilli(result.getLong(9)),
                        Instant.ofEpochMilli(result.getLong(10)),
                        result.getBoolean(11)));
            }
            return rooms;
        }
    }

    private static Long getNullableLong(ResultSet result, int column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }
}
