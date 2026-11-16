package roombook.scheduling.application;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import roombook.accounts.api.UserDirectory;
import roombook.accounts.domain.AppUser;
import roombook.accounts.persistence.AppUserRepository;
import roombook.kernel.*;
import roombook.scheduling.domain.*;
import roombook.scheduling.persistence.BookingRepository;
import roombook.scheduling.persistence.RoomRepository;
import roombook.testsupport.PostgresContainerHolder;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Інтеграційні тести конкуренції модуля scheduling (T-14, TS-19, CS-01...CS-08, CS-11, CS-12, CS-13, CS-14, TS-05, INV-08, INV-09, D-04).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Tag("TS-19")
@Tag("TS-18")
@Tag("D-24")
@Tag("INV-08")
@Tag("INV-09")
@Tag("D-04")
class SchedulingConcurrencyTest {

    @DynamicPropertySource
    static void configureDatabaseProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> c = PostgresContainerHolder.getInstance();
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/roombook", c.getHost(), c.getMappedPort(5432));
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> "roombook_app");
        registry.add("spring.datasource.password", () -> "app_pass");
        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", () -> "roombook_owner");
        registry.add("spring.flyway.password", () -> "owner_pass");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 60);
        registry.add("spring.datasource.hikari.connection-timeout", () -> 15000);
    }

    @Autowired
    private RoomService roomService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingQueries bookingQueries;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OfficeClock officeClock;

    @Autowired
    private BookingTimePolicy bookingTimePolicy;

    @Autowired
    private UserDirectory userDirectory;

    private TransactionTemplate txTemplate;
    private Actor adminActor;
    private Actor employeeActor;
    private final ZoneId kyivZone = ZoneId.of("Europe/Kyiv");

    @BeforeEach
    void setUp() {
        PostgresContainerHolder.cleanDatabase();
        txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Створюємо базових користувачів для тестів
        txTemplate.execute(status -> {
            AppUser admin = new AppUser("admin.user", "Admin User", "$2a$12$hash1111111111111111111111111111111111111111111111111111111", Role.ADMIN);
            AppUser emp = new AppUser("employee.user", "Employee User", "$2a$12$hash1111111111111111111111111111111111111111111111111111111", Role.EMPLOYEE);
            Long aId = appUserRepository.saveAndFlush(admin).getId();
            Long eId = appUserRepository.saveAndFlush(emp).getId();
            adminActor = new Actor(aId, Role.ADMIN);
            employeeActor = new Actor(eId, Role.EMPLOYEE);
            return null;
        });
    }

    private Long createRoom(String name, int floor, int capacity) {
        return txTemplate.execute(status -> {
            Room r = new Room(name, floor, capacity, "Екран");
            return roomRepository.saveAndFlush(r).getId();
        });
    }

    private void assertInv08AuditClean() {
        txTemplate.execute(status -> {
            Long violations = (Long) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM booking b1 " +
                    "JOIN booking b2 ON b1.room_id = b2.room_id AND b1.id <> b2.id " +
                    "AND b1.status = 'ACTIVE' AND b2.status = 'ACTIVE' " +
                    "AND tsrange(b1.start_at, b1.end_at) && tsrange(b2.start_at, b2.end_at)"
            ).getSingleResult();
            assertEquals(0L, violations, "Аудит INV-08 має бути порожнім (немає перетинів активних бронювань однієї кімнати)");
            return null;
        });
    }

    private void assertInv09AuditClean() {
        txTemplate.execute(status -> {
            LocalDateTime localNow = officeClock.snapshot().localNow();
            Long violations = (Long) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM room r " +
                    "JOIN booking b ON r.id = b.room_id " +
                    "WHERE r.active = false AND b.status = 'ACTIVE' AND b.end_at > :now"
            ).setParameter("now", localNow).getSingleResult();
            assertEquals(0L, violations, "Аудит INV-09 має бути порожнім (немає неактивних кімнат із незавершеними активними бронюваннями)");
            return null;
        });
    }

    // -------------------------------------------------------------------------------------------------
    // CS-01: 50 потоків із бар'єром одночасно створюють бронювання на один інтервал (20 повторів)
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-01")
    @DisplayName("CS-01: 50 потоків із бар'єром одночасно на один інтервал (20 повторів) -> рівно 1 успіх, 49 SLOT_TAKEN, 0 5xx, INV-08 чистий")
    void cs01_concurrentBookingSingleInterval50Threads20Iterations() throws Exception {
        Long roomId = createRoom("CS01 Room", 1, 10);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(2);

        int iterations = 20;
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            for (int i = 0; i < iterations; i++) {
                LocalTime startTime = LocalTime.of(8, 0).plusMinutes(i * 15L);
                LocalTime endTime = startTime.plusMinutes(15);
                LocalDateTime startAt = LocalDateTime.of(targetDate, startTime);
                LocalDateTime endAt = LocalDateTime.of(targetDate, endTime);

                CyclicBarrier barrier = new CyclicBarrier(threadCount);
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger slotTakenCount = new AtomicInteger(0);
                AtomicReference<Throwable> otherError = new AtomicReference<>();

                List<Callable<Void>> tasks = new ArrayList<>();
                for (int t = 0; t < threadCount; t++) {
                    final String title = "Booking " + i + "-" + t;
                    tasks.add(() -> {
                        try {
                            barrier.await();
                            bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, title, startAt, endAt));
                            successCount.incrementAndGet();
                        } catch (DomainException de) {
                            if (de.getReason() == Reason.SLOT_TAKEN) {
                                slotTakenCount.incrementAndGet();
                            } else {
                                otherError.set(de);
                            }
                        } catch (Throwable ex) {
                            otherError.set(ex);
                        }
                        return null;
                    });
                }

                List<Future<Void>> futures = executor.invokeAll(tasks);
                for (Future<Void> f : futures) {
                    f.get(10, TimeUnit.SECONDS);
                }

                if (otherError.get() != null) {
                    fail("Неочікувана помилка в ітерації " + i + ": " + otherError.get().getMessage(), otherError.get());
                }

                assertEquals(1, successCount.get(), "В ітерації " + i + " мав успішно створитися рівно 1 запис");
                assertEquals(49, slotTakenCount.get(), "В ітерації " + i + " рівно 49 потоків мали отримати SLOT_TAKEN");

                // Перевіряємо в БД, що для цього слота рівно один активний запис
                txTemplate.execute(status -> {
                    Long count = (Long) entityManager.createQuery(
                            "SELECT COUNT(b) FROM Booking b WHERE b.roomId = :rId AND b.startAt = :st AND b.endAt = :et AND b.status = :stt"
                    ).setParameter("rId", roomId)
                     .setParameter("st", startAt)
                     .setParameter("et", endAt)
                     .setParameter("stt", BookingStatus.ACTIVE)
                     .getSingleResult();
                    assertEquals(1L, count, "У БД має бути рівно 1 ACTIVE-запис для інтервалу");
                    return null;
                });
            }

            assertInv08AuditClean();
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // CS-02: Паралельне створення суміжних інтервалів однієї кімнати: 08:00..20:00 (48 слотів)
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-02")
    @DisplayName("CS-02: Паралельне створення 48 суміжних слотів по 15 хв на одну кімнату -> усі 48 успішні, жодних хибних конфліктів")
    void cs02_concurrentAdjacentIntervalsAllSucceed() throws Exception {
        Long roomId = createRoom("CS02 Room", 2, 8);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(3);

        int slotCount = 48; // від 08:00 до 20:00 по 15 хв
        ExecutorService executor = Executors.newFixedThreadPool(slotCount);

        try {
            CyclicBarrier barrier = new CyclicBarrier(slotCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicReference<Throwable> otherError = new AtomicReference<>();

            List<Callable<Void>> tasks = new ArrayList<>();
            for (int s = 0; s < slotCount; s++) {
                LocalTime start = LocalTime.of(8, 0).plusMinutes(s * 15L);
                LocalTime end = start.plusMinutes(15);
                LocalDateTime startAt = LocalDateTime.of(targetDate, start);
                LocalDateTime endAt = LocalDateTime.of(targetDate, end);

                tasks.add(() -> {
                    try {
                        barrier.await();
                        bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, "Slot " + start, startAt, endAt));
                        successCount.incrementAndGet();
                    } catch (Throwable t) {
                        otherError.set(t);
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }

            if (otherError.get() != null) {
                fail("Сусідній слот завершився помилкою: " + otherError.get().getMessage(), otherError.get());
            }

            assertEquals(slotCount, successCount.get(), "Усі 48 суміжних слотів мали успішно створитися");

            // Перевіряємо в БД наявність 48 записів
            txTemplate.execute(status -> {
                Long count = (Long) entityManager.createQuery(
                        "SELECT COUNT(b) FROM Booking b WHERE b.roomId = :rId AND b.status = :stt"
                ).setParameter("rId", roomId)
                 .setParameter("stt", BookingStatus.ACTIVE)
                 .getSingleResult();
                assertEquals((long) slotCount, count);
                return null;
            });

            assertInv08AuditClean();
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // CS-03: Паралельні частково перетинні інтервали
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-03")
    @DisplayName("CS-03: Паралельні частково перетинні інтервали -> успішні попарно не перетинаються, решта SLOT_TAKEN, INV-08 чистий")
    void cs03_concurrentPartiallyOverlappingIntervals() throws Exception {
        Long roomId = createRoom("CS03 Room", 1, 12);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(4);

        // Список частково перетинних інтервалів
        List<BookingInterval> testIntervals = List.of(
                new BookingInterval(LocalDateTime.of(targetDate, LocalTime.of(10, 0)), LocalDateTime.of(targetDate, LocalTime.of(11, 0))),
                new BookingInterval(LocalDateTime.of(targetDate, LocalTime.of(10, 30)), LocalDateTime.of(targetDate, LocalTime.of(11, 30))),
                new BookingInterval(LocalDateTime.of(targetDate, LocalTime.of(10, 45)), LocalDateTime.of(targetDate, LocalTime.of(11, 45))),
                new BookingInterval(LocalDateTime.of(targetDate, LocalTime.of(11, 15)), LocalDateTime.of(targetDate, LocalTime.of(12, 15))),
                new BookingInterval(LocalDateTime.of(targetDate, LocalTime.of(11, 30)), LocalDateTime.of(targetDate, LocalTime.of(12, 30))),
                new BookingInterval(LocalDateTime.of(targetDate, LocalTime.of(12, 0)), LocalDateTime.of(targetDate, LocalTime.of(13, 0)))
        );

        int count = testIntervals.size();
        ExecutorService executor = Executors.newFixedThreadPool(count);

        try {
            CyclicBarrier barrier = new CyclicBarrier(count);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger slotTakenCount = new AtomicInteger(0);
            AtomicReference<Throwable> otherError = new AtomicReference<>();

            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final BookingInterval bi = testIntervals.get(i);
                final int idx = i;
                tasks.add(() -> {
                    try {
                        barrier.await();
                        bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, "Overlap " + idx, bi.startAt(), bi.endAt()));
                        successCount.incrementAndGet();
                    } catch (DomainException de) {
                        if (de.getReason() == Reason.SLOT_TAKEN) {
                            slotTakenCount.incrementAndGet();
                        } else {
                            otherError.set(de);
                        }
                    } catch (Throwable t) {
                        otherError.set(t);
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }

            if (otherError.get() != null) {
                fail("Неочікувана помилка в CS-03: " + otherError.get().getMessage(), otherError.get());
            }

            assertTrue(successCount.get() >= 1, "Принаймні одне бронювання мало створитися");
            assertEquals(count, successCount.get() + slotTakenCount.get(), "Кожен запит мав завершитися або успіхом, або SLOT_TAKEN");

            assertInv08AuditClean();
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // CS-04: Подвійне надсилання (паралельно і послідовно)
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-04")
    @DisplayName("CS-04: Подвійне надсилання: 2 паралельні дають рівно один 201 і один SLOT_TAKEN, 2 послідовні дають обидва SLOT_TAKEN")
    void cs04_doubleSubmissionParallelAndSequential() throws Exception {
        Long roomId = createRoom("CS04 Room", 1, 6);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(2);
        LocalDateTime startAt = LocalDateTime.of(targetDate, LocalTime.of(14, 0));
        LocalDateTime endAt = LocalDateTime.of(targetDate, LocalTime.of(15, 0));

        CreateBookingCommand cmd = new CreateBookingCommand(roomId, "Подвійне надсилання", startAt, endAt);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Два паралельні запити
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger slotTakenCount = new AtomicInteger(0);
            AtomicReference<Throwable> otherError = new AtomicReference<>();

            Callable<Void> task = () -> {
                try {
                    barrier.await();
                    bookingService.createBooking(employeeActor, cmd);
                    successCount.incrementAndGet();
                } catch (DomainException de) {
                    if (de.getReason() == Reason.SLOT_TAKEN) {
                        slotTakenCount.incrementAndGet();
                    } else {
                        otherError.set(de);
                    }
                } catch (Throwable t) {
                    otherError.set(t);
                }
                return null;
            };

            Future<Void> f1 = executor.submit(task);
            Future<Void> f2 = executor.submit(task);
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            assertNull(otherError.get());
            assertEquals(1, successCount.get(), "Паралельні: рівно 1 успіх");
            assertEquals(1, slotTakenCount.get(), "Паралельні: рівно 1 SLOT_TAKEN");

            // Два послідовні запити
            DomainException exSeq1 = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, cmd));
            assertEquals(Reason.SLOT_TAKEN, exSeq1.getReason());

            DomainException exSeq2 = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, cmd));
            assertEquals(Reason.SLOT_TAKEN, exSeq2.getReason());

            assertInv08AuditClean();
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // CS-05: Створення перетинного бронювання після фіксації першого
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-05")
    @DisplayName("CS-05: Друге створення перетинного бронювання після фіксації першого повертає SLOT_TAKEN")
    void cs05_secondBookingGivesSlotTaken() {
        Long roomId = createRoom("CS05 Room", 1, 10);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(2);
        LocalDateTime startAt = LocalDateTime.of(targetDate, LocalTime.of(16, 0));
        LocalDateTime endAt = LocalDateTime.of(targetDate, LocalTime.of(17, 0));

        BookingView first = bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, "Перше", startAt, endAt));
        assertNotNull(first);

        CreateBookingCommand overlappingCmd = new CreateBookingCommand(
                roomId,
                "Друге перетинне",
                LocalDateTime.of(targetDate, LocalTime.of(16, 30)),
                LocalDateTime.of(targetDate, LocalTime.of(17, 30))
        );

        DomainException ex = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, overlappingCmd));
        assertEquals(Reason.SLOT_TAKEN, ex.getReason());

        assertInv08AuditClean();
    }

    // -------------------------------------------------------------------------------------------------
    // CS-06: Тест тримає блокування Room, createBooking чекає. Тест ставить active = false і комітить
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-06")
    @DisplayName("CS-06: Тест тримає блокування Room, деактивує її та комітить -> createBooking чекає і повертає ROOM_INACTIVE (INV-09, D-04)")
    void cs06_concurrentCreateBookingAndDeactivation() throws Exception {
        Long roomId = createRoom("CS06 Room", 1, 10);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(2);
        LocalDateTime startAt = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
        LocalDateTime endAt = LocalDateTime.of(targetDate, LocalTime.of(11, 0));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch tx1Locked = new CountDownLatch(1);
        CountDownLatch tx2Started = new CountDownLatch(1);
        AtomicReference<Throwable> tx2Error = new AtomicReference<>();
        AtomicReference<BookingView> tx2Result = new AtomicReference<>();

        try {
            // Транзакція 1: тримає блокування Room
            Future<?> tx1Future = executor.submit(() -> {
                txTemplate.execute(status -> {
                    Room room = roomRepository.lockById(roomId).orElseThrow();
                    tx1Locked.countDown();

                    try {
                        tx2Started.await(5, TimeUnit.SECONDS);
                        LockSupport.parkNanos(100_000_000L); // 100 мс чекаємо поки createBooking застрягне на lockById
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    room.deactivate();
                    roomRepository.saveAndFlush(room);
                    return null;
                });
            });

            assertTrue(tx1Locked.await(5, TimeUnit.SECONDS));

            // Транзакція 2: намагається створити бронювання
            Future<?> tx2Future = executor.submit(() -> {
                tx2Started.countDown();
                try {
                    BookingView v = bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, "Зустріч", startAt, endAt));
                    tx2Result.set(v);
                } catch (Throwable t) {
                    tx2Error.set(t);
                }
            });

            tx1Future.get(5, TimeUnit.SECONDS);
            tx2Future.get(5, TimeUnit.SECONDS);

            assertNotNull(tx2Error.get(), "createBooking мав завершитися помилкою");
            assertInstanceOf(DomainException.class, tx2Error.get());
            DomainException de = (DomainException) tx2Error.get();
            assertEquals(Reason.ROOM_INACTIVE, de.getReason());

            // Перевіряємо в БД: кімната неактивна, бронювання немає
            txTemplate.execute(status -> {
                Room r = roomRepository.findById(roomId).orElseThrow();
                assertFalse(r.isActive(), "Кімната має бути неактивною");

                Long bookingsCount = (Long) entityManager.createQuery("SELECT COUNT(b) FROM Booking b WHERE b.roomId = :rId")
                        .setParameter("rId", roomId).getSingleResult();
                assertEquals(0L, bookingsCount, "Жодного бронювання не мало створитися");
                return null;
            });

            assertInv09AuditClean();
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // CS-07: Тест тримає блокування Room, deactivateRoom чекає. Тест вставляє ACTIVE на завтра й комітить
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-07")
    @DisplayName("CS-07: Тест тримає блокування Room, вставляє бронювання на завтра і комітить -> deactivateRoom повертає ROOM_HAS_UNFINISHED_BOOKINGS (INV-09, A-23)")
    void cs07_concurrentDeactivationAndCreateBooking() throws Exception {
        Long roomId = createRoom("CS07 Room", 1, 10);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(2);
        LocalDateTime startAt = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
        LocalDateTime endAt = LocalDateTime.of(targetDate, LocalTime.of(11, 0));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch tx1Locked = new CountDownLatch(1);
        CountDownLatch tx2Started = new CountDownLatch(1);
        AtomicReference<Throwable> tx2Error = new AtomicReference<>();
        AtomicReference<RoomView> tx2Result = new AtomicReference<>();

        try {
            // Транзакція 1: блокує кімнату, вставляє активне бронювання на завтра і комітить
            Future<?> tx1Future = executor.submit(() -> {
                txTemplate.execute(status -> {
                    Room room = roomRepository.lockById(roomId).orElseThrow();
                    tx1Locked.countDown();

                    try {
                        tx2Started.await(5, TimeUnit.SECONDS);
                        LockSupport.parkNanos(100_000_000L); // 100 мс чекаємо поки deactivateRoom застрягне на lockById
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    Booking b = new Booking(roomId, employeeActor.userId(), "Бронювання на завтра", new BookingInterval(startAt, endAt), officeClock.snapshot().instant());
                    bookingRepository.saveAndFlush(b);
                    return null;
                });
            });

            assertTrue(tx1Locked.await(5, TimeUnit.SECONDS));

            // Транзакція 2: намагається деактивувати кімнату
            Future<?> tx2Future = executor.submit(() -> {
                tx2Started.countDown();
                try {
                    RoomView rv = roomService.deactivateRoom(adminActor, roomId);
                    tx2Result.set(rv);
                } catch (Throwable t) {
                    tx2Error.set(t);
                }
            });

            tx1Future.get(5, TimeUnit.SECONDS);
            tx2Future.get(5, TimeUnit.SECONDS);

            assertNotNull(tx2Error.get(), "deactivateRoom мав впасти через незавершені бронювання");
            assertInstanceOf(DomainException.class, tx2Error.get());
            DomainException de = (DomainException) tx2Error.get();
            assertEquals(Reason.ROOM_HAS_UNFINISHED_BOOKINGS, de.getReason());

            // Кімната лишається активною
            txTemplate.execute(status -> {
                Room r = roomRepository.findById(roomId).orElseThrow();
                assertTrue(r.isActive(), "Кімната мала залишитися активною");
                return null;
            });

            assertInv09AuditClean();
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // CS-08: 100 повторів: createBooking і deactivateRoom стартують разом
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-08")
    @DisplayName("CS-08: 100 повторів конкурентних createBooking і deactivateRoom -> рівно один успіх (або 201 і відмова деактивації, або 200 і ROOM_INACTIVE), INV-09 чистий")
    void cs08_concurrentBookingAndDeactivate100Iterations() throws Exception {
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(2);

        try {
            for (int i = 0; i < iterations; i++) {
                final int iter = i;
                final Long roomId = createRoom("CS08 Room " + i, 1, 8);
                final LocalDateTime startAt = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
                final LocalDateTime endAt = LocalDateTime.of(targetDate, LocalTime.of(11, 0));

                CyclicBarrier barrier = new CyclicBarrier(2);
                AtomicReference<BookingView> bookingResult = new AtomicReference<>();
                AtomicReference<Throwable> bookingError = new AtomicReference<>();
                AtomicReference<RoomView> deactivateResult = new AtomicReference<>();
                AtomicReference<Throwable> deactivateError = new AtomicReference<>();

                Callable<Void> taskCreate = () -> {
                    try {
                        barrier.await();
                        BookingView v = bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, "Booking " + iter, startAt, endAt));
                        bookingResult.set(v);
                    } catch (Throwable t) {
                        bookingError.set(t);
                    }
                    return null;
                };

                Callable<Void> taskDeactivate = () -> {
                    try {
                        barrier.await();
                        RoomView rv = roomService.deactivateRoom(adminActor, roomId);
                        deactivateResult.set(rv);
                    } catch (Throwable t) {
                        deactivateError.set(t);
                    }
                    return null;
                };

                Future<Void> f1 = executor.submit(taskCreate);
                Future<Void> f2 = executor.submit(taskDeactivate);
                f1.get(5, TimeUnit.SECONDS);
                f2.get(5, TimeUnit.SECONDS);

                boolean createSuccess = bookingResult.get() != null;
                boolean deactivateSuccess = deactivateResult.get() != null;

                // Перевіряємо допустимі результати:
                // Варіант A: createBooking успішний -> deactivateRoom має повернути ROOM_HAS_UNFINISHED_BOOKINGS
                // Варіант B: deactivateRoom успішний -> createBooking має повернути ROOM_INACTIVE
                if (createSuccess) {
                    assertFalse(deactivateSuccess, "Якщо створення успішне, деактивація не може бути успішною в ітерації " + iter);
                    assertNotNull(deactivateError.get());
                    assertInstanceOf(DomainException.class, deactivateError.get());
                    assertEquals(Reason.ROOM_HAS_UNFINISHED_BOOKINGS, ((DomainException) deactivateError.get()).getReason());
                } else if (deactivateSuccess) {
                    assertFalse(createSuccess, "Якщо деактивація успішна, створення не може бути успішним в ітерації " + iter);
                    assertNotNull(bookingError.get());
                    assertInstanceOf(DomainException.class, bookingError.get());
                    assertEquals(Reason.ROOM_INACTIVE, ((DomainException) bookingError.get()).getReason());
                } else {
                    fail("Обидві операції завершилися помилкою в ітерації " + iter + ": createErr=" + bookingError.get() + ", deactErr=" + deactivateError.get());
                }

                assertInv09AuditClean();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // CS-11: updateRoom і deactivateRoom конкурентно: lost update відсутній
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-11")
    @DisplayName("CS-11: Конкурентні updateRoom і deactivateRoom: блокування рядка усуває lost update (active лишається false)")
    void cs11_updateRoomAndDeactivateRoomNoLostUpdate() throws Exception {
        Long roomId = createRoom("CS11 Initial Room", 1, 10);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch tx1Locked = new CountDownLatch(1);
        CountDownLatch tx2Started = new CountDownLatch(1);

        try {
            // Транзакція 1: блокує кімнату, деактивує її й комітить
            Future<?> tx1Future = executor.submit(() -> {
                txTemplate.execute(status -> {
                    Room room = roomRepository.lockById(roomId).orElseThrow();
                    tx1Locked.countDown();

                    try {
                        tx2Started.await(5, TimeUnit.SECONDS);
                        LockSupport.parkNanos(100_000_000L); // 100 мс чекаємо поки updateRoom застрягне на lockById
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    room.deactivate();
                    roomRepository.saveAndFlush(room);
                    return null;
                });
            });

            assertTrue(tx1Locked.await(5, TimeUnit.SECONDS));

            // Транзакція 2: викликає updateRoom
            Future<?> tx2Future = executor.submit(() -> {
                tx2Started.countDown();
                return roomService.updateRoom(adminActor, roomId, new UpdateRoomCommand("CS11 Updated Room", 2, 12, "Дошка"));
            });

            tx1Future.get(5, TimeUnit.SECONDS);
            tx2Future.get(5, TimeUnit.SECONDS);

            // Перевіряємо в БД: назву оновлено, але active залишається false (деактивацію не перетерто)
            txTemplate.execute(status -> {
                Room room = roomRepository.findById(roomId).orElseThrow();
                assertEquals("CS11 Updated Room", room.getName());
                assertFalse(room.isActive(), "active має залишатися false (деактивацію не перетерто)");
                return null;
            });

            assertInv09AuditClean();
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // CS-12: Два скасування одного бронювання одночасно (два ADMIN)
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-12")
    @DisplayName("CS-12: Два одночасні скасування одного бронювання двома ADMIN -> обидва успішні (A-17), cancelledById першого зберігається")
    void cs12_concurrentCancelBookingByTwoAdmins() throws Exception {
        Long roomId = createRoom("CS12 Room", 1, 10);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(2);
        LocalDateTime startAt = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
        LocalDateTime endAt = LocalDateTime.of(targetDate, LocalTime.of(11, 0));

        BookingView booking = bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, "Зустріч для скасування", startAt, endAt));
        final long bookingId = booking.id();

        // Створюємо двох окремих ADMIN у БД
        Long admin1Id = txTemplate.execute(status -> {
            AppUser a1 = new AppUser("admin.first", "Admin First", "$2a$12$hash1111111111111111111111111111111111111111111111111111111", Role.ADMIN);
            return appUserRepository.saveAndFlush(a1).getId();
        });
        Long admin2Id = txTemplate.execute(status -> {
            AppUser a2 = new AppUser("admin.second", "Admin Second", "$2a$12$hash1111111111111111111111111111111111111111111111111111111", Role.ADMIN);
            return appUserRepository.saveAndFlush(a2).getId();
        });

        final Actor admin1 = new Actor(admin1Id, Role.ADMIN);
        final Actor admin2 = new Actor(admin2Id, Role.ADMIN);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<BookingView> res1 = new AtomicReference<>();
            AtomicReference<BookingView> res2 = new AtomicReference<>();
            AtomicReference<Throwable> err = new AtomicReference<>();

            Callable<Void> task1 = () -> {
                try {
                    barrier.await();
                    res1.set(bookingService.cancelBooking(admin1, bookingId));
                } catch (Throwable t) {
                    err.set(t);
                }
                return null;
            };

            Callable<Void> task2 = () -> {
                try {
                    barrier.await();
                    res2.set(bookingService.cancelBooking(admin2, bookingId));
                } catch (Throwable t) {
                    err.set(t);
                }
                return null;
            };

            Future<Void> f1 = executor.submit(task1);
            Future<Void> f2 = executor.submit(task2);
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            assertNull(err.get(), "Обидва виклики скасування мали пройти успішно");
            assertNotNull(res1.get());
            assertNotNull(res2.get());

            // Перевіряємо в БД: статус CANCELLED, а cancelledById належить одному з адмінів (ініціатору першого)
            txTemplate.execute(status -> {
                Booking b = bookingRepository.findById(bookingId).orElseThrow();
                assertEquals(BookingStatus.CANCELLED, b.getStatus());
                assertNotNull(b.getCancelledById());
                assertTrue(b.getCancelledById().equals(admin1Id) || b.getCancelledById().equals(admin2Id));
                return null;
            });
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // CS-13: cancelBooking + createBooking на той самий слот; cancelBooking + deactivateRoom
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-13")
    @DisplayName("CS-13: cancelBooking і createBooking на той самий слот одночасно -> жодних 5xx, INV-08 чистий")
    void cs13_cancelBookingAndCreateBookingConcurrently() throws Exception {
        Long roomId = createRoom("CS13 Room", 1, 10);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(2);
        LocalDateTime startAt = LocalDateTime.of(targetDate, LocalTime.of(12, 0));
        LocalDateTime endAt = LocalDateTime.of(targetDate, LocalTime.of(13, 0));

        BookingView existing = bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, "Існуюче", startAt, endAt));
        final long bookingId = existing.id();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<BookingView> cancelResult = new AtomicReference<>();
            AtomicReference<BookingView> createResult = new AtomicReference<>();
            AtomicReference<Throwable> cancelError = new AtomicReference<>();
            AtomicReference<Throwable> createError = new AtomicReference<>();

            Callable<Void> taskCancel = () -> {
                try {
                    barrier.await();
                    cancelResult.set(bookingService.cancelBooking(employeeActor, bookingId));
                } catch (Throwable t) {
                    cancelError.set(t);
                }
                return null;
            };

            Callable<Void> taskCreate = () -> {
                try {
                    barrier.await();
                    createResult.set(bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, "Нове", startAt, endAt)));
                } catch (Throwable t) {
                    createError.set(t);
                }
                return null;
            };

            Future<Void> f1 = executor.submit(taskCancel);
            Future<Void> f2 = executor.submit(taskCreate);
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            // cancelBooking завжди має бути успішним
            assertNull(cancelError.get());
            assertNotNull(cancelResult.get());

            // createBooking або успішний (якщо скасування встигло раніше), або SLOT_TAKEN (якщо створення йшло раніше)
            if (createError.get() != null) {
                assertInstanceOf(DomainException.class, createError.get());
                assertEquals(Reason.SLOT_TAKEN, ((DomainException) createError.get()).getReason());
            } else {
                assertNotNull(createResult.get());
            }

            assertInv08AuditClean();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("CS-13")
    @DisplayName("CS-13: cancelBooking і deactivateRoom одночасно -> допустимі обидва порядки, 5xx немає, INV-09 чистий")
    void cs13_cancelBookingAndDeactivateRoomConcurrently() throws Exception {
        Long roomId = createRoom("CS13 Room B", 1, 10);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(2);
        LocalDateTime startAt = LocalDateTime.of(targetDate, LocalTime.of(12, 0));
        LocalDateTime endAt = LocalDateTime.of(targetDate, LocalTime.of(13, 0));

        BookingView existing = bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, "Бронювання", startAt, endAt));
        final long bookingId = existing.id();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<BookingView> cancelResult = new AtomicReference<>();
            AtomicReference<RoomView> deactivateResult = new AtomicReference<>();
            AtomicReference<Throwable> cancelError = new AtomicReference<>();
            AtomicReference<Throwable> deactivateError = new AtomicReference<>();

            Callable<Void> taskCancel = () -> {
                try {
                    barrier.await();
                    cancelResult.set(bookingService.cancelBooking(employeeActor, bookingId));
                } catch (Throwable t) {
                    cancelError.set(t);
                }
                return null;
            };

            Callable<Void> taskDeactivate = () -> {
                try {
                    barrier.await();
                    deactivateResult.set(roomService.deactivateRoom(adminActor, roomId));
                } catch (Throwable t) {
                    deactivateError.set(t);
                }
                return null;
            };

            Future<Void> f1 = executor.submit(taskCancel);
            Future<Void> f2 = executor.submit(taskDeactivate);
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            assertNull(cancelError.get());
            assertNotNull(cancelResult.get());

            // Деактивація або успішна (якщо скасування пройшло раніше), або ROOM_HAS_UNFINISHED_BOOKINGS
            if (deactivateError.get() != null) {
                assertInstanceOf(DomainException.class, deactivateError.get());
                assertEquals(Reason.ROOM_HAS_UNFINISHED_BOOKINGS, ((DomainException) deactivateError.get()).getReason());
            } else {
                assertNotNull(deactivateResult.get());
            }

            assertInv09AuditClean();
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // CS-14: Таймаут блокування lock_timeout на рівні сервісу (500 мс)
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("CS-14")
    @DisplayName("CS-14: Тест тримає блокування Room довше за lock_timeout (500 мс): операція отримує помилку блокування, з'єднання повертається в пул, наступний запит успішний (OPS-01, D-04)")
    void cs14_lockTimeoutServiceLevelConnectionReused() throws Exception {
        Long roomId = createRoom("CS14 Room", 1, 10);
        LocalDate targetDate = officeClock.snapshot().today().plusDays(2);
        LocalDateTime startAt = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
        LocalDateTime endAt = LocalDateTime.of(targetDate, LocalTime.of(11, 0));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch tx1Locked = new CountDownLatch(1);
        CountDownLatch tx2Finished = new CountDownLatch(1);
        AtomicReference<Throwable> tx2Error = new AtomicReference<>();

        try {
            // Транзакція 1: бере блокування Room і тримає його 1 секунду
            Future<?> tx1Future = executor.submit(() -> {
                txTemplate.execute(status -> {
                    Room room = roomRepository.lockById(roomId).orElseThrow();
                    tx1Locked.countDown();

                    try {
                        tx2Finished.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            });

            assertTrue(tx1Locked.await(5, TimeUnit.SECONDS));

            // Транзакція 2: налаштовує короткий lock_timeout (500 мс) для своєї сесії та намагається забронювати
            Future<?> tx2Future = executor.submit(() -> {
                try {
                    txTemplate.execute(status -> {
                        entityManager.createNativeQuery("SET LOCAL lock_timeout = '500ms'").executeUpdate();
                        bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, "Timeout test", startAt, endAt));
                        return null;
                    });
                } catch (Throwable t) {
                    tx2Error.set(t);
                } finally {
                    tx2Finished.countDown();
                }
            });

            tx2Future.get(5, TimeUnit.SECONDS);
            tx1Future.get(5, TimeUnit.SECONDS);

            // Перевіряємо, що транзакція 2 завершилася помилкою через таймаут блокування (SQLState 55P03 або помилка блокування)
            assertNotNull(tx2Error.get(), "Операція під блокуванням мала зазнати таймауту");
            String errStr = tx2Error.get().toString();
            assertTrue(errStr.contains("55P03") || errStr.contains("lock") || errStr.contains("Lock") || errStr.contains("timeout"),
                    "Помилка мала бути пов'язана з таймаутом блокування: " + errStr);

            // Перевіряємо, що з'єднання повернулося в пул і наступна дія є успішною
            BookingView successView = bookingService.createBooking(employeeActor, new CreateBookingCommand(roomId, "Успіх після таймауту", startAt, endAt));
            assertNotNull(successView);
            assertEquals("Успіх після таймауту", successView.title());
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // TS-05, випадок 1: 25 жовтня 2026, 00:30 UTC і 01:30 UTC (03:30 за Києвом)
    // -------------------------------------------------------------------------------------------------
    @Test
    @Tag("TS-05")
    @DisplayName("TS-05 (випадок 1): 25 жовтня 2026, 00:30 UTC і 01:30 UTC (03:30 за Києвом) -> бронювання 08:00-09:00 приймається, UPCOMING, блокує деактивацію кімнати")
    void ts05_case1_autumnTransitionAcceptsBookingUpcomingAndBlocksDeactivation() {
        Long roomId = createRoom("TS05 Room", 1, 10);
        LocalDateTime slotStart = LocalDateTime.of(2026, 10, 25, 8, 0);
        LocalDateTime slotEnd = LocalDateTime.of(2026, 10, 25, 9, 0);

        // Перевіряємо для обох моментів: 00:30 UTC і 01:30 UTC (обидва дають 03:30 у Києві)
        List<Instant> testInstants = List.of(
                Instant.parse("2026-10-25T00:30:00Z"),
                Instant.parse("2026-10-25T01:30:00Z")
        );

        for (Instant instant : testInstants) {
            OfficeClock customClock = new OfficeClock(Clock.fixed(instant, kyivZone), kyivZone);
            BookingService customBookingService = new BookingService(
                    roomRepository,
                    bookingRepository,
                    bookingTimePolicy,
                    customClock,
                    new BookingQueries(bookingRepository, roomRepository, userDirectory, customClock)
            );
            RoomService customRoomService = new RoomService(roomRepository, bookingRepository, customClock);

            // 1. Бронювання 08:00-09:00 приймається
            BookingView bookingView = txTemplate.execute(status -> customBookingService.createBooking(
                    employeeActor,
                    new CreateBookingCommand(roomId, "Осіння зустріч " + instant, slotStart, slotEnd)
            ));
            assertNotNull(bookingView);

            // 2. Фаза UPCOMING
            assertEquals(BookingPhase.UPCOMING, bookingView.phase());

            // 3. Блокує деактивацію кімнати
            DomainException deactEx = assertThrows(DomainException.class, () ->
                    txTemplate.execute(status -> customRoomService.deactivateRoom(adminActor, roomId))
            );
            assertEquals(Reason.ROOM_HAS_UNFINISHED_BOOKINGS, deactEx.getReason());

            // Скасовуємо для наступної ітерації
            txTemplate.execute(status -> customBookingService.cancelBooking(adminActor, bookingView.id()));
        }
    }
}
