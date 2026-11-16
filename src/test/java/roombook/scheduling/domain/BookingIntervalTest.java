package roombook.scheduling.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тести методів BookingInterval (D-01, D-03, TR-05).
 */
@Tag("D-01")
@Tag("TR-05")
@Tag("FR-12")
class BookingIntervalTest {

    @Test
    @DisplayName("Обчислення дати та тривалості напіввідкритого інтервалу")
    void dateAndDurationCalculated() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 2, 11, 30);
        BookingInterval interval = new BookingInterval(start, end);

        assertEquals(LocalDate.of(2026, 11, 2), interval.date());
        assertEquals(Duration.ofMinutes(90), interval.duration());
    }

    @Test
    @DisplayName("Перевірка перетину: суміжні інтервали НЕ перетинаються, частково перетинні — перетинаються")
    void overlapsCheck() {
        BookingInterval i1 = new BookingInterval(
                LocalDateTime.of(2026, 11, 2, 10, 0),
                LocalDateTime.of(2026, 11, 2, 11, 0)
        );

        // Суміжний інтервал праворуч (11:00-12:00)
        BookingInterval iAdjacentRight = new BookingInterval(
                LocalDateTime.of(2026, 11, 2, 11, 0),
                LocalDateTime.of(2026, 11, 2, 12, 0)
        );
        assertFalse(i1.overlaps(iAdjacentRight), "Суміжні інтервали не перетинаються (10-11 та 11-12)");
        assertFalse(iAdjacentRight.overlaps(i1), "Симетрія суміжності");

        // Перетинний інтервал (10:30-11:30)
        BookingInterval iOverlap = new BookingInterval(
                LocalDateTime.of(2026, 11, 2, 10, 30),
                LocalDateTime.of(2026, 11, 2, 11, 30)
        );
        assertTrue(i1.overlaps(iOverlap), "10:00-11:00 та 10:30-11:30 мають перетинатися");
        assertTrue(iOverlap.overlaps(i1));

        // Вкладений інтервал (10:15-10:45)
        BookingInterval iInside = new BookingInterval(
                LocalDateTime.of(2026, 11, 2, 10, 15),
                LocalDateTime.of(2026, 11, 2, 10, 45)
        );
        assertTrue(i1.overlaps(iInside));
    }

    @Test
    @DisplayName("Обчислення фази інтервалу: UPCOMING, IN_PROGRESS, FINISHED")
    void phaseComputation() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 2, 11, 0);
        BookingInterval interval = new BookingInterval(start, end);

        // До початку
        assertEquals(BookingPhase.UPCOMING, interval.phase(LocalDateTime.of(2026, 11, 2, 9, 59, 59)));

        // Точно на момент початку
        assertEquals(BookingPhase.IN_PROGRESS, interval.phase(LocalDateTime.of(2026, 11, 2, 10, 0, 0)));

        // У середині
        assertEquals(BookingPhase.IN_PROGRESS, interval.phase(LocalDateTime.of(2026, 11, 2, 10, 30, 0)));

        // За мить до кінця
        assertEquals(BookingPhase.IN_PROGRESS, interval.phase(LocalDateTime.of(2026, 11, 2, 10, 59, 59, 999_000_000)));

        // Точно в момент endAt — вже завершено
        assertEquals(BookingPhase.FINISHED, interval.phase(LocalDateTime.of(2026, 11, 2, 11, 0, 0)));

        // Після закінчення
        assertEquals(BookingPhase.FINISHED, interval.phase(LocalDateTime.of(2026, 11, 2, 11, 0, 1)));
    }
}
