package roombook.testsupport;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.kernel.ComparisonKey;
import roombook.platform.ConstraintNames;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("TS-13")
@Tag("TS-14")
@Tag("INV-02")
@Tag("INV-07")
@Tag("INV-08")
@Tag("INV-10")
@Tag("INV-11")
@Tag("INV-13")
@Tag("FR-12")
@Tag("D-03")
@Tag("D-05")
@Tag("D-20")
@Tag("A-23")
@Tag("A-24")
@Tag("OPS-10")
class DatabaseSchemaMigrationTest {

    @BeforeEach
    void setUp() {
        PostgresContainerHolder.cleanDatabase("roombook");
        PostgresContainerHolder.cleanDatabase("roombook_c");
    }

    @Test
    @DisplayName("TS-13, A-24, D-20: Каталог містить кожне ім'я таблиць, обмежень, індексів та btree_gist в обох базах (roombook та roombook_c)")
    void catalogContainsEverySchemaObjectFromStage3() throws Exception {
        verifyCatalog("roombook", PostgresContainerHolder.getOwnerConnection());
        verifyCatalog("roombook_c", PostgresContainerHolder.getCLocaleOwnerConnection());
    }

    private void verifyCatalog(String dbName, Connection conn) throws SQLException {
        try (conn; Statement stmt = conn.createStatement()) {
            // 1. Розширення btree_gist
            try (ResultSet rs = stmt.executeQuery("SELECT extname FROM pg_extension WHERE extname = 'btree_gist'")) {
                assertTrue(rs.next(), "Розширення btree_gist має бути встановлене в базі " + dbName);
            }

            // 2. Таблиці: room, app_user, booking
            Set<String> tables = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            assertTrue(tables.contains("room"), "Таблиця room має існувати в " + dbName);
            assertTrue(tables.contains("app_user"), "Таблиця app_user має існувати в " + dbName);
            assertTrue(tables.contains("booking"), "Таблиця booking має існувати в " + dbName);

            // 3. Обмеження (pg_constraint)
            Set<String> constraints = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery("SELECT conname FROM pg_constraint JOIN pg_namespace n ON n.oid = connamespace WHERE n.nspname = 'public'")) {
                while (rs.next()) {
                    constraints.add(rs.getString(1));
                }
            }

            // Первинні ключі
            assertTrue(constraints.contains("pk_room"), "pk_room має бути в " + dbName);
            assertTrue(constraints.contains("pk_app_user"), "pk_app_user має бути в " + dbName);
            assertTrue(constraints.contains("pk_booking"), "pk_booking має бути в " + dbName);

            // Зовнішні ключі
            assertTrue(constraints.contains("fk_booking_room"), "fk_booking_room має бути в " + dbName);
            assertTrue(constraints.contains("fk_booking_author"), "fk_booking_author має бути в " + dbName);
            assertTrue(constraints.contains("fk_booking_cancelled_by"), "fk_booking_cancelled_by має бути в " + dbName);

            // Унікальні обмеження
            assertTrue(constraints.contains("uq_room_name_key"), "uq_room_name_key має бути в " + dbName);
            assertTrue(constraints.contains("uq_app_user_username_key"), "uq_app_user_username_key має бути в " + dbName);

            // Виключення (GiST)
            assertTrue(constraints.contains("ex_booking_no_overlap"), "ex_booking_no_overlap має бути в " + dbName);

            // CHECK обмеження
            assertTrue(constraints.contains("ck_booking_interval"), "ck_booking_interval має бути в " + dbName);
            assertTrue(constraints.contains("ck_booking_cancel_state"), "ck_booking_cancel_state має бути в " + dbName);
            assertTrue(constraints.contains("ck_booking_status"), "ck_booking_status має бути в " + dbName);
            assertTrue(constraints.contains("ck_booking_title_not_blank"), "ck_booking_title_not_blank має бути в " + dbName);
            assertTrue(constraints.contains("ck_room_name_not_blank"), "ck_room_name_not_blank має бути в " + dbName);
            assertTrue(constraints.contains("ck_room_capacity_positive"), "ck_room_capacity_positive має бути в " + dbName);
            assertTrue(constraints.contains("ck_app_user_role"), "ck_app_user_role має бути в " + dbName);

            // 4. Індекси (pg_indexes)
            Set<String> indexes = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery("SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'public'")) {
                while (rs.next()) {
                    String indexName = rs.getString("indexname");
                    String indexDef = rs.getString("indexdef");
                    indexes.add(indexName);

                    if ("ix_booking_room_start".equals(indexName)) {
                        assertTrue(indexDef.contains("DESC"), "ix_booking_room_start має містити DESC сортування");
                    }
                    if ("ix_booking_author_start".equals(indexName)) {
                        assertTrue(indexDef.contains("DESC"), "ix_booking_author_start має містити DESC сортування");
                    }
                }
            }
            assertTrue(indexes.contains("ix_booking_start"), "ix_booking_start має бути в " + dbName);
            assertTrue(indexes.contains("ix_booking_room_start"), "ix_booking_room_start має бути в " + dbName);
            assertTrue(indexes.contains("ix_booking_author_start"), "ix_booking_author_start має бути в " + dbName);
        }
    }

    @Test
    @DisplayName("TS-13, OPS-10: Роль roombook_app не має прав DELETE та DDL")
    void appRoleCannotDeleteOrExecuteDdl() throws Exception {
        try (Connection appConn = PostgresContainerHolder.getAppConnection();
             Statement stmt = appConn.createStatement()) {

            // Заборона DELETE на room
            SQLException delRoom = assertThrows(SQLException.class, () -> stmt.execute("DELETE FROM room"));
            assertEquals("42501", delRoom.getSQLState(), "Очікується помилка прав доступу (42501) для DELETE room");

            // Заборона DELETE на app_user
            SQLException delUser = assertThrows(SQLException.class, () -> stmt.execute("DELETE FROM app_user"));
            assertEquals("42501", delUser.getSQLState(), "Очікується помилка прав доступу (42501) для DELETE app_user");

            // Заборона DELETE на booking
            SQLException delBooking = assertThrows(SQLException.class, () -> stmt.execute("DELETE FROM booking"));
            assertEquals("42501", delBooking.getSQLState(), "Очікується помилка прав доступу (42501) для DELETE booking");

            // Заборона DDL (CREATE TABLE)
            SQLException createTable = assertThrows(SQLException.class, () ->
                    stmt.execute("CREATE TABLE forbidden_test (id int)"));
            assertEquals("42501", createTable.getSQLState(), "Очікується помилка прав доступу (42501) для CREATE TABLE");

            // Заборона DDL (DROP TABLE)
            SQLException dropTable = assertThrows(SQLException.class, () -> stmt.execute("DROP TABLE room"));
            assertEquals("42501", dropTable.getSQLState(), "Очікується помилка прав доступу (42501) для DROP TABLE");
        }
    }

    @Test
    @DisplayName("TS-14: Кожне ex_, uq_, ck_, fk_ обмеження перевіряється безпосередньо через JDBC і відхиляється")
    void everyConstraintDirectlyViolatedAndRejected() throws Exception {
        // Базові коректні сутності
        long roomId = TestDataBuilder.room().name("Головна переговорна").capacity(12).insert();
        long authorId = TestDataBuilder.appUser().username("john_doe").role("EMPLOYEE").insert();

        // 1. ex_booking_no_overlap (INV-08, D-03)
        TestDataBuilder.booking()
                .roomId(roomId)
                .authorId(authorId)
                .startAt(LocalDateTime.of(2026, 11, 2, 10, 0))
                .endAt(LocalDateTime.of(2026, 11, 2, 11, 0))
                .status("ACTIVE")
                .insert();

        // Перетин у тому самому інтервалі тієї самої кімнати
        IllegalStateException exOverlap = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.booking()
                    .roomId(roomId)
                    .authorId(authorId)
                    .startAt(LocalDateTime.of(2026, 11, 2, 10, 30))
                    .endAt(LocalDateTime.of(2026, 11, 2, 11, 30))
                    .status("ACTIVE")
                    .insert();
        });
        assertEquals("ex_booking_no_overlap", ConstraintNames.getConstraintName(exOverlap));
        assertEquals("23P01", ConstraintNames.getSqlState(exOverlap));

        // 2. uq_room_name_key (INV-11, D-05)
        IllegalStateException exRoomUq = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.room().name(" головна  переговорна ").insert();
        });
        assertEquals("uq_room_name_key", ConstraintNames.getConstraintName(exRoomUq));
        assertEquals("23505", ConstraintNames.getSqlState(exRoomUq));

        // 3. uq_app_user_username_key (INV-13, D-05)
        IllegalStateException exUserUq = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.appUser().username("JOHN_DOE").insert();
        });
        assertEquals("uq_app_user_username_key", ConstraintNames.getConstraintName(exUserUq));
        assertEquals("23505", ConstraintNames.getSqlState(exUserUq));

        // 4. ck_booking_interval (INV-02)
        // 4.1 start_at >= end_at
        IllegalStateException exInterval1 = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.booking()
                    .roomId(roomId)
                    .authorId(authorId)
                    .startAt(LocalDateTime.of(2026, 11, 2, 15, 0))
                    .endAt(LocalDateTime.of(2026, 11, 2, 14, 0))
                    .insert();
        });
        assertEquals("ck_booking_interval", ConstraintNames.getConstraintName(exInterval1));
        assertEquals("23514", ConstraintNames.getSqlState(exInterval1));

        // 4.2 Дати початку і кінця не збігаються
        IllegalStateException exInterval2 = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.booking()
                    .roomId(roomId)
                    .authorId(authorId)
                    .startAt(LocalDateTime.of(2026, 11, 2, 23, 0))
                    .endAt(LocalDateTime.of(2026, 11, 3, 1, 0))
                    .insert();
        });
        assertEquals("ck_booking_interval", ConstraintNames.getConstraintName(exInterval2));
        assertEquals("23514", ConstraintNames.getSqlState(exInterval2));

        // 5. ck_booking_cancel_state (INV-07)
        // 5.1 ACTIVE, але вказано cancelled_at
        IllegalStateException exCancel1 = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.booking()
                    .roomId(roomId)
                    .authorId(authorId)
                    .startAt(LocalDateTime.of(2026, 11, 2, 14, 0))
                    .endAt(LocalDateTime.of(2026, 11, 2, 15, 0))
                    .status("ACTIVE")
                    .cancelledAt(Instant.now())
                    .insert();
        });
        assertEquals("ck_booking_cancel_state", ConstraintNames.getConstraintName(exCancel1));
        assertEquals("23514", ConstraintNames.getSqlState(exCancel1));

        // 5.2 CANCELLED, але cancelled_at відсутній
        IllegalStateException exCancel2 = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.booking()
                    .roomId(roomId)
                    .authorId(authorId)
                    .startAt(LocalDateTime.of(2026, 11, 2, 14, 0))
                    .endAt(LocalDateTime.of(2026, 11, 2, 15, 0))
                    .status("CANCELLED")
                    .insert();
        });
        assertEquals("ck_booking_cancel_state", ConstraintNames.getConstraintName(exCancel2));
        assertEquals("23514", ConstraintNames.getSqlState(exCancel2));

        // 6. ck_booking_status
        IllegalStateException exStatus = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.booking()
                    .roomId(roomId)
                    .authorId(authorId)
                    .startAt(LocalDateTime.of(2026, 11, 2, 16, 0))
                    .endAt(LocalDateTime.of(2026, 11, 2, 17, 0))
                    .status("PENDING")
                    .insert();
        });
        assertEquals("ck_booking_status", ConstraintNames.getConstraintName(exStatus));
        assertEquals("23514", ConstraintNames.getSqlState(exStatus));

        // 7. ck_booking_title_not_blank (INV-01)
        IllegalStateException exTitle = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.booking()
                    .roomId(roomId)
                    .authorId(authorId)
                    .title("   ")
                    .startAt(LocalDateTime.of(2026, 11, 2, 16, 0))
                    .endAt(LocalDateTime.of(2026, 11, 2, 17, 0))
                    .insert();
        });
        assertEquals("ck_booking_title_not_blank", ConstraintNames.getConstraintName(exTitle));
        assertEquals("23514", ConstraintNames.getSqlState(exTitle));

        // 8. ck_room_name_not_blank (INV-11)
        IllegalStateException exRoomName = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.room().name("   ").insert();
        });
        assertEquals("ck_room_name_not_blank", ConstraintNames.getConstraintName(exRoomName));
        assertEquals("23514", ConstraintNames.getSqlState(exRoomName));

        // 9. ck_room_capacity_positive (INV-12)
        IllegalStateException exCapacity = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.room().name("Мала кімната").capacity(0).insert();
        });
        assertEquals("ck_room_capacity_positive", ConstraintNames.getConstraintName(exCapacity));
        assertEquals("23514", ConstraintNames.getSqlState(exCapacity));

        // 10. ck_app_user_role (INV-13)
        IllegalStateException exRole = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.appUser().username("guest_user").role("GUEST").insert();
        });
        assertEquals("ck_app_user_role", ConstraintNames.getConstraintName(exRole));
        assertEquals("23514", ConstraintNames.getSqlState(exRole));

        // 11. fk_booking_room (INV-10)
        IllegalStateException exFkRoom = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.booking()
                    .roomId(999999L)
                    .authorId(authorId)
                    .startAt(LocalDateTime.of(2026, 11, 2, 16, 0))
                    .endAt(LocalDateTime.of(2026, 11, 2, 17, 0))
                    .insert();
        });
        assertEquals("fk_booking_room", ConstraintNames.getConstraintName(exFkRoom));
        assertEquals("23503", ConstraintNames.getSqlState(exFkRoom));

        // 12. fk_booking_author (INV-10)
        IllegalStateException exFkAuthor = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.booking()
                    .roomId(roomId)
                    .authorId(999999L)
                    .startAt(LocalDateTime.of(2026, 11, 2, 16, 0))
                    .endAt(LocalDateTime.of(2026, 11, 2, 17, 0))
                    .insert();
        });
        assertEquals("fk_booking_author", ConstraintNames.getConstraintName(exFkAuthor));
        assertEquals("23503", ConstraintNames.getSqlState(exFkAuthor));

        // 13. fk_booking_cancelled_by (INV-10)
        IllegalStateException exFkCancel = assertThrows(IllegalStateException.class, () -> {
            TestDataBuilder.booking()
                    .roomId(roomId)
                    .authorId(authorId)
                    .startAt(LocalDateTime.of(2026, 11, 2, 16, 0))
                    .endAt(LocalDateTime.of(2026, 11, 2, 17, 0))
                    .status("CANCELLED")
                    .cancelledAt(Instant.now())
                    .cancelledById(999999L)
                    .insert();
        });
        assertEquals("fk_booking_cancelled_by", ConstraintNames.getConstraintName(exFkCancel));
        assertEquals("23503", ConstraintNames.getSqlState(exFkCancel));
    }

    @Test
    @DisplayName("TS-14, FR-12, D-03: Суміжні інтервали, різні кімнати й CANCELLED-бронювання не конфліктують; скасований слот можна зайняти знову")
    void nonConflictingBookingScenariosSucceed() {
        long room1 = TestDataBuilder.room().name("Кімната 1").capacity(5).insert();
        long room2 = TestDataBuilder.room().name("Кімната 2").capacity(8).insert();
        long user1 = TestDataBuilder.appUser().username("user1").insert();

        // 1. Суміжні інтервали в одній кімнаті (10:00-11:00 та 11:00-12:00) успішно співіснують
        long b1 = TestDataBuilder.booking()
                .roomId(room1)
                .authorId(user1)
                .startAt(LocalDateTime.of(2026, 11, 2, 10, 0))
                .endAt(LocalDateTime.of(2026, 11, 2, 11, 0))
                .insert();
        assertTrue(b1 > 0);

        long b2 = TestDataBuilder.booking()
                .roomId(room1)
                .authorId(user1)
                .startAt(LocalDateTime.of(2026, 11, 2, 11, 0))
                .endAt(LocalDateTime.of(2026, 11, 2, 12, 0))
                .insert();
        assertTrue(b2 > 0);

        // 2. Той самий інтервал (10:00-11:00), але в іншій кімнаті успішно створюється
        long b3 = TestDataBuilder.booking()
                .roomId(room2)
                .authorId(user1)
                .startAt(LocalDateTime.of(2026, 11, 2, 10, 0))
                .endAt(LocalDateTime.of(2026, 11, 2, 11, 0))
                .insert();
        assertTrue(b3 > 0);

        // 3. CANCELLED-бронювання не блокує зайняття слота (12:00-13:00)
        long bCancelled = TestDataBuilder.booking()
                .roomId(room1)
                .authorId(user1)
                .startAt(LocalDateTime.of(2026, 11, 2, 12, 0))
                .endAt(LocalDateTime.of(2026, 11, 2, 13, 0))
                .status("CANCELLED")
                .cancelledAt(Instant.now())
                .cancelledById(user1)
                .insert();
        assertTrue(bCancelled > 0);

        // Після скасування той самий слот (12:00-13:00) у room1 успішно займається новим ACTIVE-бронюванням
        long bRebooked = TestDataBuilder.booking()
                .roomId(room1)
                .authorId(user1)
                .startAt(LocalDateTime.of(2026, 11, 2, 12, 0))
                .endAt(LocalDateTime.of(2026, 11, 2, 13, 0))
                .status("ACTIVE")
                .insert();
        assertTrue(bRebooked > 0);
    }

    @Test
    @DisplayName("Приймання 3: Контрольна сума застосованої міграції перевіряється Flyway (правило «Незмінність»)")
    void flywayValidatesChecksumImmutability() {
        var container = PostgresContainerHolder.getInstance();
        String url = String.format("jdbc:postgresql://%s:%d/roombook", container.getHost(), container.getMappedPort(5432));

        Flyway flyway = Flyway.configure()
                .dataSource(url, "roombook_owner", "owner_pass")
                .locations("classpath:db/migration")
                .load();

        // Поточні міграції на диску мають збігатися з записами в flyway_schema_history
        assertDoesNotThrow(flyway::validate, "Flyway validate має проходити успішно для незмінених міграцій");
    }
}
