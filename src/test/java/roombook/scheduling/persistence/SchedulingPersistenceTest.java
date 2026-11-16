package roombook.scheduling.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.OfficeClock;
import roombook.kernel.Reason;
import roombook.scheduling.domain.Booking;
import roombook.scheduling.domain.BookingInterval;
import roombook.scheduling.domain.BookingStatus;
import roombook.scheduling.domain.Room;
import roombook.testsupport.InterleavingUtility;
import roombook.testsupport.PostgresContainerHolder;
import roombook.testsupport.TestDataBuilder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Інтеграційні тести збереження модуля scheduling (T-11, TS-15, D-04, D-09, AR-04, AR-10, AR-12, TR-02, INV-02, INV-08, INV-09, INV-11).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SchedulingPersistenceTest {

    @DynamicPropertySource
    static void configureDatabaseProperties(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> c = PostgresContainerHolder.getInstance();
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/roombook", c.getHost(), c.getMappedPort(5432));
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> "roombook_app");
        registry.add("spring.datasource.password", () -> "app_pass");
        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", () -> "roombook_owner");
        registry.add("spring.flyway.password", () -> "owner_pass");
    }

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Environment environment;

    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        PostgresContainerHolder.cleanDatabase();
        txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    @Tag("TS-15")
    @Tag("TR-02")
    @Tag("D-09")
    @DisplayName("TS-15: startAt (10:00) and createdAt read without shift across JVM and DB session timezones")
    void timestampRoundTripWithoutShift() throws SQLException {
        long authorId = TestDataBuilder.appUser().username("user_tz").insert();
        Room room = txTemplate.execute(status ->
                roomRepository.saveAndFlush(new Room("TZ Room", 1, 10, null))
        );

        LocalDateTime startAt = LocalDateTime.of(2026, 11, 1, 10, 0, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 11, 1, 11, 0, 0);
        Instant createdAt = Instant.parse("2026-11-01T08:00:00Z");

        Booking saved = txTemplate.execute(status -> {
            Booking b = new Booking(room.getId(), authorId, "TZ Test Meeting", startAt, endAt, createdAt);
            return bookingRepository.saveAndFlush(b);
        });

        assertNotNull(saved);
        Long bookingId = saved.getId();

        // 1. Читання через репозиторій у сесії БД з таймзоною UTC
        txTemplate.execute(status -> {
            entityManager.clear();
            entityManager.createNativeQuery("SET TIME ZONE 'UTC'").executeUpdate();
            Booking read = bookingRepository.findById(bookingId).orElseThrow();
            assertEquals(startAt, read.getStartAt(), "startAt must have no shift under DB session timezone UTC");
            assertEquals(endAt, read.getEndAt(), "endAt must have no shift under DB session timezone UTC");
            assertEquals(createdAt, read.getCreatedAt(), "createdAt must have no shift under DB session timezone UTC");
            return null;
        });

        // 2. Читання через репозиторій у сесії БД з таймзоною Pacific/Auckland
        txTemplate.execute(status -> {
            entityManager.clear();
            entityManager.createNativeQuery("SET TIME ZONE 'Pacific/Auckland'").executeUpdate();
            Booking read = bookingRepository.findById(bookingId).orElseThrow();
            assertEquals(startAt, read.getStartAt(), "startAt must have no shift under DB session timezone Pacific/Auckland");
            assertEquals(endAt, read.getEndAt(), "endAt must have no shift under DB session timezone Pacific/Auckland");
            assertEquals(createdAt, read.getCreatedAt(), "createdAt must have no shift under DB session timezone Pacific/Auckland");
            return null;
        });

        // 3. Пряма перевірка через JDBC для обох зон БД {UTC, Pacific/Auckland}
        for (String dbTz : List.of("UTC", "Pacific/Auckland")) {
            try (Connection conn = PostgresContainerHolder.getAppConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET TIME ZONE '" + dbTz + "'");
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT start_at, end_at, created_at FROM booking WHERE id = " + bookingId)) {
                        assertTrue(rs.next());
                        assertEquals(startAt, rs.getTimestamp("start_at").toLocalDateTime(),
                                "JDBC start_at must equal 10:00 under DB session timezone " + dbTz);
                        assertEquals(endAt, rs.getTimestamp("end_at").toLocalDateTime(),
                                "JDBC end_at must equal 11:00 under DB session timezone " + dbTz);
                        assertEquals(createdAt, rs.getTimestamp("created_at").toInstant(),
                                "JDBC created_at must equal exact Instant under DB session timezone " + dbTz);
                    }
                }
            }
        }
    }

    @Test
    @Tag("AR-10")
    @Tag("D-09")
    @DisplayName("AR-10: OSIV is disabled and Hibernate schema validation passes")
    void osivDisabledAndHibernateValidationPasses() {
        Boolean osivEnabled = environment.getProperty("spring.jpa.open-in-view", Boolean.class);
        assertEquals(Boolean.FALSE, osivEnabled, "OSIV must be disabled (spring.jpa.open-in-view=false)");

        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");
        assertEquals("validate", ddlAuto, "Hibernate ddl-auto must be 'validate'");

        // Перевіряємо, що EntityManager доступний і успішно ініціалізований
        assertNotNull(entityManager);
        assertTrue(entityManager.isOpen());
    }

    @Test
    @Tag("D-04")
    @Tag("INV-09")
    @DisplayName("D-04: RoomRepository.lockById blocks concurrent transaction until commit")
    void roomLockByIdBlocksConcurrentTransaction() throws Exception {
        long roomId = TestDataBuilder.room().name("Locking Room Test").insert();

        Room lockedRoom = InterleavingUtility.runWithRowLock(
                "room",
                "id = " + roomId,
                holderConn -> {
                    // Утримувач оновлює поверх за блокуванням
                    try (Statement stmt = holderConn.createStatement()) {
                        stmt.execute("UPDATE room SET floor = 7 WHERE id = " + roomId);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> txTemplate.execute(status -> {
                    try {
                        entityManager.createNativeQuery("SET application_name = '" + InterleavingUtility.TARGET_APP_NAME + "'").executeUpdate();
                        return roomRepository.lockById(roomId).orElseThrow();
                    } finally {
                        entityManager.createNativeQuery("SET application_name = 'roombook-app'").executeUpdate();
                    }
                })
        );

        assertNotNull(lockedRoom);
        assertEquals(roomId, lockedRoom.getId());
        assertEquals(7, lockedRoom.getFloor(), "Після завершення блокування транзакція читає оновлений стан");
    }

    @Test
    @Tag("D-04")
    @Tag("INV-08")
    @DisplayName("D-04: BookingRepository.lockById blocks concurrent transaction until commit")
    void bookingLockByIdBlocksConcurrentTransaction() throws Exception {
        long authorId = TestDataBuilder.appUser().username("author_lock").insert();
        long roomId = TestDataBuilder.room().name("Booking Lock Room").insert();
        long bookingId = TestDataBuilder.booking()
                .roomId(roomId)
                .authorId(authorId)
                .title("Initial Booking Title")
                .startAt(LocalDateTime.of(2026, 11, 1, 10, 0))
                .endAt(LocalDateTime.of(2026, 11, 1, 11, 0))
                .status("ACTIVE")
                .insert();

        Booking lockedBooking = InterleavingUtility.runWithRowLock(
                "booking",
                "id = " + bookingId,
                holderConn -> {
                    try (Statement stmt = holderConn.createStatement()) {
                        stmt.execute("UPDATE booking SET title = 'Updated by Holder' WHERE id = " + bookingId);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> txTemplate.execute(status -> {
                    try {
                        entityManager.createNativeQuery("SET application_name = '" + InterleavingUtility.TARGET_APP_NAME + "'").executeUpdate();
                        return bookingRepository.lockById(bookingId).orElseThrow();
                    } finally {
                        entityManager.createNativeQuery("SET application_name = 'roombook-app'").executeUpdate();
                    }
                })
        );

        assertNotNull(lockedBooking);
        assertEquals(bookingId, lockedBooking.getId());
        assertEquals("Updated by Holder", lockedBooking.getTitle());
    }

    @Test
    @Tag("INV-02")
    @Tag("INV-08")
    @Tag("TS-15")
    @DisplayName("INV-02, INV-08: findActiveOverlapping excludes adjacent intervals, CANCELLED bookings, and other rooms")
    void findActiveOverlappingFiltersCorrectly() {
        long authorId = TestDataBuilder.appUser().username("author_overlap").insert();
        Room room1 = txTemplate.execute(status -> roomRepository.saveAndFlush(new Room("Room 1 Overlap", 1, 10, null)));
        Room room2 = txTemplate.execute(status -> roomRepository.saveAndFlush(new Room("Room 2 Overlap", 1, 10, null)));
        Room room3 = txTemplate.execute(status -> roomRepository.saveAndFlush(new Room("Room 3 Overlap", 1, 10, null)));
        Room room4 = txTemplate.execute(status -> roomRepository.saveAndFlush(new Room("Room 4 Overlap", 1, 10, null)));
        Room room5 = txTemplate.execute(status -> roomRepository.saveAndFlush(new Room("Room 5 Overlap", 1, 10, null)));

        Instant now = Instant.parse("2026-11-01T08:00:00Z");
        LocalDateTime targetStart = LocalDateTime.of(2026, 11, 1, 10, 0);
        LocalDateTime targetEnd = LocalDateTime.of(2026, 11, 1, 11, 0);

        txTemplate.execute(status -> {
            // У Room 1:
            // 1. Суміжне зліва [09:00, 10:00) Room 1 - не повинно повертатися
            bookingRepository.saveAndFlush(new Booking(room1.getId(), authorId, "Adjacent Before",
                    LocalDateTime.of(2026, 11, 1, 9, 0), LocalDateTime.of(2026, 11, 1, 10, 0), now));

            // 2. Суміжне справа [11:00, 12:00) Room 1 - не повинно повертатися
            bookingRepository.saveAndFlush(new Booking(room1.getId(), authorId, "Adjacent After",
                    LocalDateTime.of(2026, 11, 1, 11, 0), LocalDateTime.of(2026, 11, 1, 12, 0), now));

            // 3. CANCELLED [10:15, 10:45) Room 1 - не повинно повертатися
            Booking cancelled = new Booking(room1.getId(), authorId, "Cancelled in Slot",
                    LocalDateTime.of(2026, 11, 1, 10, 15), LocalDateTime.of(2026, 11, 1, 10, 45), now);
            cancelled.cancel(new Actor(authorId, roombook.kernel.Role.EMPLOYEE), new OfficeClock.Snapshot(now, LocalDateTime.of(2026, 11, 1, 8, 30), LocalDate.of(2026, 11, 1)));
            bookingRepository.saveAndFlush(cancelled);

            // 4. Інша кімната Room 2 [10:15, 10:45) - не повинно повертатися для Room 1
            bookingRepository.saveAndFlush(new Booking(room2.getId(), authorId, "Other Room Slot",
                    LocalDateTime.of(2026, 11, 1, 10, 15), LocalDateTime.of(2026, 11, 1, 10, 45), now));

            // 5. Перетин: усередині [10:15, 10:45) Room 1 - повинно повертатися
            bookingRepository.saveAndFlush(new Booking(room1.getId(), authorId, "Inside Slot",
                    LocalDateTime.of(2026, 11, 1, 10, 15), LocalDateTime.of(2026, 11, 1, 10, 45), now));

            // 6. Перетин у Room 3: початок раніше [09:30, 10:30) - повинно повертатися
            bookingRepository.saveAndFlush(new Booking(room3.getId(), authorId, "Overlap Start",
                    LocalDateTime.of(2026, 11, 1, 9, 30), LocalDateTime.of(2026, 11, 1, 10, 30), now));

            // 7. Перетин у Room 4: кінець пізніше [10:30, 11:30) - повинно повертатися
            bookingRepository.saveAndFlush(new Booking(room4.getId(), authorId, "Overlap End",
                    LocalDateTime.of(2026, 11, 1, 10, 30), LocalDateTime.of(2026, 11, 1, 11, 30), now));

            // 8. Перетин у Room 5: охоплює повністю [09:30, 11:30) - повинно повертатися
            bookingRepository.saveAndFlush(new Booking(room5.getId(), authorId, "Enclosing Slot",
                    LocalDateTime.of(2026, 11, 1, 9, 30), LocalDateTime.of(2026, 11, 1, 11, 30), now));

            return null;
        });

        // Вибірка за Room 1: знайдено лише Inside Slot
        List<Booking> overlapping1 = bookingRepository.findActiveOverlapping(room1.getId(), targetStart, targetEnd);
        assertEquals(1, overlapping1.size(), "Для кімнати 1 має бути знайдено рівно 1 бронювання (Inside Slot)");
        assertEquals("Inside Slot", overlapping1.get(0).getTitle());

        // Перевірка перевантаженого методу з BookingInterval для Room 1
        List<Booking> overlappingByInterval = bookingRepository.findActiveOverlapping(room1.getId(), new BookingInterval(targetStart, targetEnd));
        assertEquals(1, overlappingByInterval.size());
        assertEquals("Inside Slot", overlappingByInterval.get(0).getTitle());

        // Вибірка за іншими кімнатами (різні форми перетину)
        List<Booking> overlapping3 = bookingRepository.findActiveOverlapping(room3.getId(), targetStart, targetEnd);
        assertEquals(1, overlapping3.size());
        assertEquals("Overlap Start", overlapping3.get(0).getTitle());

        List<Booking> overlapping4 = bookingRepository.findActiveOverlapping(room4.getId(), targetStart, targetEnd);
        assertEquals(1, overlapping4.size());
        assertEquals("Overlap End", overlapping4.get(0).getTitle());

        List<Booking> overlapping5 = bookingRepository.findActiveOverlapping(room5.getId(), targetStart, targetEnd);
        assertEquals(1, overlapping5.size());
        assertEquals("Enclosing Slot", overlapping5.get(0).getTitle());
    }

    @Test
    @Tag("INV-09")
    @Tag("TS-15")
    @DisplayName("INV-09: existsUnfinished returns false when endAt == now, true when endAt > now, ignores CANCELLED")
    void existsUnfinishedEvaluatesCorrectly() {
        long authorId = TestDataBuilder.appUser().username("author_unfinished").insert();
        Room room1 = txTemplate.execute(status -> roomRepository.saveAndFlush(new Room("Room Unfinished 1", 1, 10, null)));
        Room room2 = txTemplate.execute(status -> roomRepository.saveAndFlush(new Room("Room Unfinished 2", 1, 10, null)));
        Room room3 = txTemplate.execute(status -> roomRepository.saveAndFlush(new Room("Room Unfinished 3", 1, 10, null)));

        LocalDateTime targetMoment = LocalDateTime.of(2026, 11, 1, 12, 0, 0);
        Instant now = Instant.parse("2026-11-01T08:00:00Z");

        // 1. Порожня кімната -> false
        assertFalse(bookingRepository.existsUnfinished(room1.getId(), targetMoment));

        // 2. Бронювання закінчується рівно в targetMoment [11:00, 12:00) -> false (вже завершене за TR-05)
        txTemplate.execute(status ->
                bookingRepository.saveAndFlush(new Booking(room1.getId(), authorId, "Ends at moment",
                        LocalDateTime.of(2026, 11, 1, 11, 0), targetMoment, now))
        );
        assertFalse(bookingRepository.existsUnfinished(room1.getId(), targetMoment),
                "Бронювання, кінець якого дорівнює моменту, вже завершене (TR-05) і дає false");

        // 3. Бронювання закінчується строго пізніше targetMoment [11:00, 12:01) у Room 2 -> true
        Booking bUnfinished = txTemplate.execute(status ->
                bookingRepository.saveAndFlush(new Booking(room2.getId(), authorId, "Ends after moment",
                        LocalDateTime.of(2026, 11, 1, 11, 0), LocalDateTime.of(2026, 11, 1, 12, 1), now))
        );
        assertTrue(bookingRepository.existsUnfinished(room2.getId(), targetMoment),
                "Бронювання, кінець якого пізніше моменту, дає true");

        // 4. Скасування незавершеного бронювання повертає false
        txTemplate.execute(status -> {
            Booking b = bookingRepository.findById(bUnfinished.getId()).orElseThrow();
            b.cancel(new Actor(authorId, roombook.kernel.Role.EMPLOYEE), new OfficeClock.Snapshot(now, LocalDateTime.of(2026, 11, 1, 11, 30), LocalDate.of(2026, 11, 1)));
            bookingRepository.saveAndFlush(b);
            return null;
        });
        assertFalse(bookingRepository.existsUnfinished(room2.getId(), targetMoment),
                "CANCELLED бронювання ігнорується навіть якщо endAt > now");

        // 5. Бронювання в Room 3 не впливає на Room 1
        txTemplate.execute(status ->
                bookingRepository.saveAndFlush(new Booking(room3.getId(), authorId, "Room 3 Active",
                        LocalDateTime.of(2026, 11, 1, 11, 0), LocalDateTime.of(2026, 11, 1, 13, 0), now))
        );
        assertFalse(bookingRepository.existsUnfinished(room1.getId(), targetMoment));
        assertTrue(bookingRepository.existsUnfinished(room3.getId(), targetMoment));
    }

    @Test
    @Tag("AR-12")
    @Tag("INV-08")
    @DisplayName("AR-12: Direct overlapping booking via repository throws SLOT_TAKEN and rolls back cleanly")
    void overlappingBookingThrowsSlotTakenAndRollsBack() {
        long authorId = TestDataBuilder.appUser().username("author_ar12").insert();
        Room room = txTemplate.execute(status -> roomRepository.saveAndFlush(new Room("Room AR12", 1, 10, null)));

        LocalDateTime startAt = LocalDateTime.of(2026, 11, 1, 10, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 11, 1, 11, 0);
        Instant now = Instant.parse("2026-11-01T08:00:00Z");

        // 1. Перше бронювання успішно записується
        txTemplate.execute(status -> {
            Booking b1 = new Booking(room.getId(), authorId, "First Booking", startAt, endAt, now);
            return bookingRepository.saveAndFlush(b1);
        });

        // 2. Друге перетинне бронювання напряму через репозиторій кидає DomainException(Reason.SLOT_TAKEN)
        LocalDateTime overlapStart = LocalDateTime.of(2026, 11, 1, 10, 30);
        LocalDateTime overlapEnd = LocalDateTime.of(2026, 11, 1, 11, 30);

        DomainException ex = assertThrows(DomainException.class, () ->
                txTemplate.execute(status -> {
                    Booking b2 = new Booking(room.getId(), authorId, "Overlapping Booking", overlapStart, overlapEnd, now);
                    try {
                        return bookingRepository.saveAndFlush(b2);
                    } catch (Throwable t) {
                        status.setRollbackOnly();
                        throw SchedulingConstraints.translate(t);
                    }
                })
        );

        assertEquals(Reason.SLOT_TAKEN, ex.getReason());

        // 3. Перевіряємо, що транзакція повністю відкочена: друге бронювання не збережене
        List<Booking> active = bookingRepository.findActiveOverlapping(room.getId(), startAt, endAt);
        assertEquals(1, active.size());
        assertEquals("First Booking", active.get(0).getTitle());
    }

    @Test
    @Tag("AR-12")
    @Tag("INV-11")
    @DisplayName("AR-12: Duplicate room name key via repository throws ROOM_NAME_TAKEN and rolls back cleanly")
    void duplicateRoomNameThrowsRoomNameTakenAndRollsBack() {
        // 1. Створюємо першу кімнату
        txTemplate.execute(status -> roomRepository.saveAndFlush(new Room("Конференц-Зал 1", 1, 10, null)));

        // 2. Спроба створити кімнату з тим самим ключем назви
        DomainException ex = assertThrows(DomainException.class, () ->
                txTemplate.execute(status -> {
                    Room duplicate = new Room("  конференц-зал 1  ", 2, 20, null);
                    try {
                        return roomRepository.saveAndFlush(duplicate);
                    } catch (Throwable t) {
                        status.setRollbackOnly();
                        throw SchedulingConstraints.translate(t);
                    }
                })
        );

        assertEquals(Reason.ROOM_NAME_TAKEN, ex.getReason());

        // 3. Транзакція відкотилася: у БД лише одна кімната з цим ключем назви
        Long count = txTemplate.execute(status ->
                entityManager.createQuery("SELECT COUNT(r) FROM Room r WHERE r.nameKey = :k", Long.class)
                        .setParameter("k", "конференц-зал 1")
                        .getSingleResult()
        );
        assertEquals(1L, count, "У БД має лишитися лише одна кімната з цим ключем назви");
    }

    @Test
    @Tag("INV-08")
    @DisplayName("INV-08: Cancelled booking does not block slot for new active booking")
    void cancelledBookingDoesNotBlockSlot() {
        long authorId = TestDataBuilder.appUser().username("author_cancel_slot").insert();
        Room room = txTemplate.execute(status -> roomRepository.saveAndFlush(new Room("Room Rebook Slot", 1, 10, null)));

        LocalDateTime startAt = LocalDateTime.of(2026, 11, 1, 14, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 11, 1, 15, 0);
        Instant now = Instant.parse("2026-11-01T08:00:00Z");

        Booking b1 = txTemplate.execute(status -> {
            Booking b = new Booking(room.getId(), authorId, "Initial Booking to Cancel", startAt, endAt, now);
            return bookingRepository.saveAndFlush(b);
        });

        // Скасовуємо бронювання
        txTemplate.execute(status -> {
            Booking b = bookingRepository.findById(b1.getId()).orElseThrow();
            b.cancel(new Actor(authorId, roombook.kernel.Role.EMPLOYEE), new OfficeClock.Snapshot(now, LocalDateTime.of(2026, 11, 1, 9, 0), LocalDate.of(2026, 11, 1)));
            return bookingRepository.saveAndFlush(b);
        });

        // Створюємо нове активне бронювання у тому ж слоті - обмеження ex_booking_no_overlap дозволяє
        assertDoesNotThrow(() ->
                txTemplate.execute(status -> {
                    Booking b2 = new Booking(room.getId(), authorId, "New Booking in Same Slot", startAt, endAt, now);
                    return bookingRepository.saveAndFlush(b2);
                })
        );

        List<Booking> active = bookingRepository.findActiveOverlapping(room.getId(), startAt, endAt);
        assertEquals(1, active.size());
        assertEquals("New Booking in Same Slot", active.get(0).getTitle());
    }

    @Test
    @Tag("AR-12")
    @DisplayName("AR-12: SchedulingConstraints utility methods wrap and translate properly")
    void schedulingConstraintsUtilityTests() {
        // execute(Supplier) успішний
        String res = SchedulingConstraints.execute(() -> "success");
        assertEquals("success", res);

        // execute(Runnable) успішний
        assertDoesNotThrow(() -> SchedulingConstraints.execute(() -> {}));

        // Невідомий виняток прокидається як RuntimeException
        RuntimeException normalEx = assertThrows(RuntimeException.class, () ->
                SchedulingConstraints.execute(() -> {
                    throw new IllegalStateException("Ordinary error");
                })
        );
        assertEquals("Ordinary error", normalEx.getMessage());
    }
}
