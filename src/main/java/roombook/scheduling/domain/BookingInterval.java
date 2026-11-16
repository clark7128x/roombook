package roombook.scheduling.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Напіввідкритий інтервал бронювання [startAt, endAt) за місцевим часом офісу (D-01).
 * Знає свою дату, тривалість, фазу та перетин з іншим інтервалом (TR-05, D-03).
 */
public record BookingInterval(LocalDateTime startAt, LocalDateTime endAt) {

    public BookingInterval {
        Objects.requireNonNull(startAt, "startAt must not be null");
        Objects.requireNonNull(endAt, "endAt must not be null");
    }

    public LocalDate date() {
        return startAt.toLocalDate();
    }

    public Duration duration() {
        return Duration.between(startAt, endAt);
    }

    /**
     * Перевірка перетину двох напіввідкритих інтервалів [startAt, endAt) (D-03, FR-12).
     * Суміжні інтервали (наприклад 10:00-11:00 та 11:00-12:00) НЕ перетинаються.
     */
    public boolean overlaps(BookingInterval other) {
        Objects.requireNonNull(other, "other interval must not be null");
        return this.startAt.isBefore(other.endAt) && other.startAt.isBefore(this.endAt);
    }

    /**
     * Обчислення фази інтервалу відносно місцевого часу офісу (TR-05).
     */
    public BookingPhase phase(LocalDateTime localNow) {
        Objects.requireNonNull(localNow, "localNow must not be null");
        if (localNow.isBefore(startAt)) {
            return BookingPhase.UPCOMING;
        }
        if (localNow.isBefore(endAt)) {
            return BookingPhase.IN_PROGRESS;
        }
        return BookingPhase.FINISHED;
    }
}
