package roombook.scheduling.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.FieldError;
import roombook.kernel.FieldRule;
import roombook.kernel.OfficeClock;
import roombook.kernel.Reason;
import roombook.kernel.Role;
import roombook.kernel.TimeRule;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тести сутності Booking (T-08, TS-01, TS-02, TS-05, INV-01, INV-02, INV-06, INV-07, FR-10, FR-17, FR-18, TR-05, A-17).
 */
@Tag("TS-01")
@Tag("TS-02")
@Tag("TS-05")
@Tag("INV-01")
@Tag("INV-02")
@Tag("INV-06")
@Tag("INV-07")
@Tag("FR-10")
@Tag("FR-17")
@Tag("FR-18")
@Tag("TR-05")
class BookingTest {

    private final ZoneId kyivZone = ZoneId.of("Europe/Kyiv");

    private OfficeClock.Snapshot createSnapshot(LocalDateTime localDateTime) {
        Instant instant = localDateTime.atZone(kyivZone).toInstant();
        Clock clock = Clock.fixed(instant, kyivZone);
        OfficeClock officeClock = new OfficeClock(clock, kyivZone);
        return officeClock.snapshot();
    }

    @Test
    @DisplayName("Приймання 1: Граничні значення FR-10 для назви зустрічі title (0, 1, 200, 201 символ, лише пробіли, trim)")
    void fr10TitleBoundaryValues() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 2, 11, 0);
        Instant now = Instant.now();

        // 0 символів / null -> REQUIRED
        DomainException exNull = assertThrows(DomainException.class, () ->
                new Booking(1L, 10L, null, start, end, now));
        assertEquals(Reason.INVALID_INPUT, exNull.getReason());
        assertTrue(exNull.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("title") && fe.rule() == FieldRule.REQUIRED));

        // Лише пробіли -> REQUIRED
        DomainException exBlank = assertThrows(DomainException.class, () ->
                new Booking(1L, 10L, "   \t  ", start, end, now));
        assertEquals(Reason.INVALID_INPUT, exBlank.getReason());
        assertTrue(exBlank.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("title") && fe.rule() == FieldRule.REQUIRED));

        // 1 символ -> валідно
        Booking b1 = new Booking(1L, 10L, "A", start, end, now);
        assertEquals("A", b1.getTitle());

        // 200 символів -> валідно
        String title200 = "a".repeat(200);
        Booking b200 = new Booking(1L, 10L, title200, start, end, now);
        assertEquals(200, b200.getTitle().length());

        // 201 символ -> LENGTH
        String title201 = "a".repeat(201);
        DomainException ex201 = assertThrows(DomainException.class, () ->
                new Booking(1L, 10L, title201, start, end, now));
        assertEquals(Reason.INVALID_INPUT, ex201.getReason());
        assertTrue(ex201.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("title") && fe.rule() == FieldRule.LENGTH));

        // Trim зберігає очищений рядок
        Booking bTrim = new Booking(1L, 10L, "   Team Standup   ", start, end, now);
        assertEquals("Team Standup", bTrim.getTitle());

        // Керувальні символи -> ILLEGAL_CHARACTERS
        DomainException exCtrl = assertThrows(DomainException.class, () ->
                new Booking(1L, 10L, "Standup\u0007Alert", start, end, now));
        assertEquals(Reason.INVALID_INPUT, exCtrl.getReason());
        assertTrue(exCtrl.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("title") && fe.rule() == FieldRule.ILLEGAL_CHARACTERS));
    }

    @Test
    @DisplayName("Помилки полів накопичуються й віддаються разом (FieldError)")
    void fieldErrorsAccumulatedTogether() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 2, 11, 0);

        DomainException ex = assertThrows(DomainException.class, () ->
                new Booking(null, 0L, "   ", start, end, null));

        assertEquals(Reason.INVALID_INPUT, ex.getReason());
        List<FieldError> errors = ex.getFieldErrors();
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("roomId")));
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("authorId")));
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("title")));
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("createdAt")));
    }

    @Test
    @DisplayName("Створення сутності повторно перевіряє INV-02 за формою інтервалу")
    void constructorValidatesInv02IntervalShape() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 12, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 2, 11, 0); // end < start
        Instant now = Instant.now();

        DomainException ex = assertThrows(DomainException.class, () ->
                new Booking(1L, 10L, "Meeting", start, end, now));
        assertEquals(Reason.INVALID_TIME, ex.getReason());
        assertEquals(TimeRule.INTERVAL_SHAPE, ex.getTimeRule());
    }

    @Test
    @DisplayName("Приймання 2: Скасування з UPCOMING та IN_PROGRESS, збереження моменту та ініціатора скасування")
    void cancelFromUpcomingAndInProgress() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 2, 11, 0);
        Instant createdAt = Instant.parse("2026-11-01T10:00:00Z");

        Actor author = new Actor(10L, Role.EMPLOYEE);

        // 1. Скасування з UPCOMING (о 09:30)
        Booking bUpcoming = new Booking(1L, 10L, "Upcoming Meeting", start, end, createdAt);
        assertEquals(BookingStatus.ACTIVE, bUpcoming.getStatus());
        assertNull(bUpcoming.getCancelledAt());
        assertNull(bUpcoming.getCancelledById());

        OfficeClock.Snapshot snapUpcoming = createSnapshot(LocalDateTime.of(2026, 11, 2, 9, 30));
        bUpcoming.cancel(author, snapUpcoming);

        assertEquals(BookingStatus.CANCELLED, bUpcoming.getStatus());
        assertEquals(snapUpcoming.instant(), bUpcoming.getCancelledAt());
        assertEquals(10L, bUpcoming.getCancelledById());
        assertNull(bUpcoming.phase(LocalDateTime.of(2026, 11, 2, 9, 30)), "Фаза скасованого є null");

        // 2. Скасування з IN_PROGRESS (о 10:15)
        Booking bInProgress = new Booking(1L, 10L, "Ongoing Meeting", start, end, createdAt);
        OfficeClock.Snapshot snapInProgress = createSnapshot(LocalDateTime.of(2026, 11, 2, 10, 15));
        bInProgress.cancel(author, snapInProgress);

        assertEquals(BookingStatus.CANCELLED, bInProgress.getStatus());
        assertEquals(snapInProgress.instant(), bInProgress.getCancelledAt());
        assertEquals(10L, bInProgress.getCancelledById());
    }

    @Test
    @DisplayName("Приймання 2: Відмова скасування для FINISHED, безпечний повтор для скасованого, заборона для чужого EMPLOYEE")
    void cancelFinishedRejectsRepeatedSafeOtherEmployeeForbidden() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 2, 11, 0);
        Instant createdAt = Instant.parse("2026-11-01T10:00:00Z");

        Actor author = new Actor(10L, Role.EMPLOYEE);
        Actor otherEmployee = new Actor(20L, Role.EMPLOYEE);
        Actor admin = new Actor(99L, Role.ADMIN);

        // 1. Чужий EMPLOYEE отримує NOT_PERMITTED
        Booking bActive = new Booking(1L, 10L, "Sync", start, end, createdAt);
        OfficeClock.Snapshot snapBefore = createSnapshot(LocalDateTime.of(2026, 11, 2, 9, 30));

        DomainException exPerm = assertThrows(DomainException.class, () ->
                bActive.cancel(otherEmployee, snapBefore));
        assertEquals(Reason.NOT_PERMITTED, exPerm.getReason());
        assertEquals(BookingStatus.ACTIVE, bActive.getStatus(), "Статус не змінився");

        // 2. Скасування адміністратором дозволене (A-17)
        bActive.cancel(admin, snapBefore);
        assertEquals(BookingStatus.CANCELLED, bActive.getStatus());
        Instant firstCancelledAt = bActive.getCancelledAt();
        Long firstCancelledById = bActive.getCancelledById();
        assertEquals(99L, firstCancelledById);

        // 3. Безпечний повтор для вже скасованого: стан не змінюється, старі поля зберігаються
        OfficeClock.Snapshot snapLater = createSnapshot(LocalDateTime.of(2026, 11, 2, 9, 45));
        assertDoesNotThrow(() -> bActive.cancel(admin, snapLater));
        assertDoesNotThrow(() -> bActive.cancel(author, snapLater));
        assertEquals(firstCancelledAt, bActive.getCancelledAt(), "Час першого скасування лишається незмінним");
        assertEquals(firstCancelledById, bActive.getCancelledById(), "Ініціатор першого скасування лишається незмінним");

        // 4. Спроба скасувати вже завершене бронювання (FINISHED) дає BOOKING_FINISHED
        Booking bFinished = new Booking(1L, 10L, "Finished Meeting", start, end, createdAt);
        OfficeClock.Snapshot snapFinished = createSnapshot(LocalDateTime.of(2026, 11, 2, 11, 0, 1));
        DomainException exFin = assertThrows(DomainException.class, () ->
                bFinished.cancel(author, snapFinished));
        assertEquals(Reason.BOOKING_FINISHED, exFin.getReason());
    }

    @Test
    @DisplayName("Приймання 3 і TS-05 Випадок 3: 10:59:59.999 (IN_PROGRESS, скасування дозволене) та 11:00:00.000 (FINISHED, BOOKING_FINISHED)")
    void ts05Case3BoundaryMomentsForCancellationAndAdjacentOngoing() {
        LocalDateTime start1 = LocalDateTime.of(2026, 11, 2, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 11, 2, 11, 0);

        LocalDateTime start2 = LocalDateTime.of(2026, 11, 2, 11, 0);
        LocalDateTime end2 = LocalDateTime.of(2026, 11, 2, 12, 0);

        Instant createdAt = Instant.parse("2026-11-01T10:00:00Z");
        Actor author = new Actor(10L, Role.EMPLOYEE);

        Booking b1 = new Booking(1L, 10L, "Meeting 1", start1, end1, createdAt);
        Booking b2 = new Booking(1L, 10L, "Meeting 2", start2, end2, createdAt);

        // Мить 1: 10:59:59.999
        LocalDateTime t1 = LocalDateTime.of(2026, 11, 2, 10, 59, 59, 999_000_000);
        OfficeClock.Snapshot snap1 = createSnapshot(t1);

        assertEquals(BookingPhase.IN_PROGRESS, b1.phase(t1));
        assertTrue(b1.isUnfinished(t1));
        assertFalse(b1.isFinished(t1));
        assertDoesNotThrow(() -> b1.cancel(author, snap1), "О 10:59:59.999 скасування першого бронювання дозволене");

        // Мить 2: 11:00:00.000 (створюємо свіже b1Finished)
        Booking b1Finished = new Booking(1L, 10L, "Meeting 1 Copy", start1, end1, createdAt);
        LocalDateTime t2 = LocalDateTime.of(2026, 11, 2, 11, 0, 0);
        OfficeClock.Snapshot snap2 = createSnapshot(t2);

        assertEquals(BookingPhase.FINISHED, b1Finished.phase(t2));
        assertFalse(b1Finished.isUnfinished(t2));
        assertTrue(b1Finished.isFinished(t2));

        DomainException ex = assertThrows(DomainException.class, () -> b1Finished.cancel(author, snap2));
        assertEquals(Reason.BOOKING_FINISHED, ex.getReason(), "О 11:00:00.000 перше бронювання завершене, відмова BOOKING_FINISHED");

        // У ту саму мить 11:00:00.000 суміжне бронювання b2 (11:00-12:00) триває (IN_PROGRESS)
        assertEquals(BookingPhase.IN_PROGRESS, b2.phase(t2));
        assertTrue(b2.isUnfinished(t2));
        assertFalse(b2.isFinished(t2));
    }
}
