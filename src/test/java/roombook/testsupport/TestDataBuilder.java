package roombook.testsupport;

import roombook.kernel.ComparisonKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Будівники тестових сутностей (Room, AppUser, Booking), що пишуть у БД напряму від імені власника (roombook_owner),
 * без проходження через сервіси застосунку (T-03, T-06).
 */
public final class TestDataBuilder {

    private TestDataBuilder() {
    }

    public static AppUserBuilder appUser() {
        return new AppUserBuilder();
    }

    public static RoomBuilder room() {
        return new RoomBuilder();
    }

    public static BookingBuilder booking() {
        return new BookingBuilder();
    }

    public static class AppUserBuilder {
        private String username = "testuser";
        private String displayName = "Test User";
        private String passwordHash = "$2a$04$someValidBcryptHashForTestingOnly";
        private String role = "EMPLOYEE";
        private boolean enabled = true;

        public AppUserBuilder username(String username) {
            this.username = username;
            return this;
        }

        public AppUserBuilder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public AppUserBuilder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public AppUserBuilder role(String role) {
            this.role = role;
            return this;
        }

        public AppUserBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public long insert() {
            String sql = "INSERT INTO app_user (username, username_key, display_name, password_hash, role, enabled) VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
            try (Connection conn = PostgresContainerHolder.getOwnerConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, ComparisonKey.calculate(username));
                ps.setString(3, displayName);
                ps.setString(4, passwordHash);
                ps.setString(5, role);
                ps.setBoolean(6, enabled);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to insert app_user via test builder", e);
            }
        }
    }

    public static class RoomBuilder {
        private String name = "Meeting Room";
        private int floor = 1;
        private int capacity = 10;
        private String equipment = null;
        private boolean active = true;

        public RoomBuilder name(String name) {
            this.name = name;
            return this;
        }

        public RoomBuilder floor(int floor) {
            this.floor = floor;
            return this;
        }

        public RoomBuilder capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        public RoomBuilder equipment(String equipment) {
            this.equipment = equipment;
            return this;
        }

        public RoomBuilder active(boolean active) {
            this.active = active;
            return this;
        }

        public long insert() {
            String sql = "INSERT INTO room (name, name_key, floor, capacity, equipment, active) VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
            try (Connection conn = PostgresContainerHolder.getOwnerConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, ComparisonKey.calculate(name));
                ps.setInt(3, floor);
                ps.setInt(4, capacity);
                ps.setString(5, equipment);
                ps.setBoolean(6, active);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to insert room via test builder", e);
            }
        }
    }

    public static class BookingBuilder {
        private long roomId;
        private long authorId;
        private String title = "Team Sync";
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private String status = "ACTIVE";
        private Instant createdAt = Instant.now();
        private Instant cancelledAt = null;
        private Long cancelledById = null;

        public BookingBuilder roomId(long roomId) {
            this.roomId = roomId;
            return this;
        }

        public BookingBuilder authorId(long authorId) {
            this.authorId = authorId;
            return this;
        }

        public BookingBuilder title(String title) {
            this.title = title;
            return this;
        }

        public BookingBuilder startAt(LocalDateTime startAt) {
            this.startAt = startAt;
            return this;
        }

        public BookingBuilder endAt(LocalDateTime endAt) {
            this.endAt = endAt;
            return this;
        }

        public BookingBuilder status(String status) {
            this.status = status;
            return this;
        }

        public BookingBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public BookingBuilder cancelledAt(Instant cancelledAt) {
            this.cancelledAt = cancelledAt;
            return this;
        }

        public BookingBuilder cancelledById(Long cancelledById) {
            this.cancelledById = cancelledById;
            return this;
        }

        public long insert() {
            String sql = "INSERT INTO booking (room_id, author_id, title, start_at, end_at, status, created_at, cancelled_at, cancelled_by_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
            try (Connection conn = PostgresContainerHolder.getOwnerConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, roomId);
                ps.setLong(2, authorId);
                ps.setString(3, title);
                ps.setTimestamp(4, Timestamp.valueOf(startAt));
                ps.setTimestamp(5, Timestamp.valueOf(endAt));
                ps.setString(6, status);
                ps.setTimestamp(7, Timestamp.from(createdAt));
                if (cancelledAt != null) {
                    ps.setTimestamp(8, Timestamp.from(cancelledAt));
                } else {
                    ps.setNull(8, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
                }
                if (cancelledById != null) {
                    ps.setLong(9, cancelledById);
                } else {
                    ps.setNull(9, java.sql.Types.BIGINT);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to insert booking via test builder", e);
            }
        }
    }
}
