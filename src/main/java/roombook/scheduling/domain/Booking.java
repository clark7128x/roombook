package roombook.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.FieldError;
import roombook.kernel.FieldRule;
import roombook.kernel.OfficeClock;
import roombook.kernel.Reason;
import roombook.kernel.TextRules;
import roombook.kernel.TimeRule;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сутність бронювання кімнати (INV-01...INV-07, FR-09...FR-18, AR-04).
 * Містить поля-ідентифікатори замість зв'язків з іншими сутностями.
 * Публічних сеттерів немає. Зміна стану відбувається виключно через доменні методи.
 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_id")
    private Long cancelledById;

    /**
     * Конструктор для JPA.
     */
    protected Booking() {
    }

    /**
     * Створення нового бронювання (INV-01, INV-02, FR-09, FR-10).
     * Валідує обов'язкові поля, накопичує FieldError та повторно перевіряє форму інтервалу (INV-02).
     */
    public Booking(Long roomId, Long authorId, String rawTitle, LocalDateTime startAt, LocalDateTime endAt, Instant createdAt) {
        List<FieldError> errors = new ArrayList<>();

        if (roomId == null || roomId <= 0) {
            errors.add(new FieldError("roomId", FieldRule.REQUIRED));
        }
        if (authorId == null || authorId <= 0) {
            errors.add(new FieldError("authorId", FieldRule.REQUIRED));
        }

        String strippedTitle = null;
        if (rawTitle == null) {
            errors.add(new FieldError("title", FieldRule.REQUIRED));
        } else {
            strippedTitle = TextRules.strip(rawTitle);
            if (strippedTitle.isEmpty()) {
                errors.add(new FieldError("title", FieldRule.REQUIRED));
            } else if (TextRules.codePointLength(strippedTitle) > 200) {
                errors.add(new FieldError("title", FieldRule.LENGTH));
            } else if (TextRules.hasControlCharacters(strippedTitle)) {
                errors.add(new FieldError("title", FieldRule.ILLEGAL_CHARACTERS));
            }
        }

        if (startAt == null) {
            errors.add(new FieldError("startAt", FieldRule.REQUIRED));
        }
        if (endAt == null) {
            errors.add(new FieldError("endAt", FieldRule.REQUIRED));
        }
        if (createdAt == null) {
            errors.add(new FieldError("createdAt", FieldRule.REQUIRED));
        }

        if (!errors.isEmpty()) {
            throw new DomainException(Reason.INVALID_INPUT, errors);
        }

        // Повторна перевірка INV-02 (початок раніше кінця в межах однієї дати)
        if (!startAt.isBefore(endAt) || !startAt.toLocalDate().equals(endAt.toLocalDate())) {
            throw new DomainException(Reason.INVALID_TIME, TimeRule.INTERVAL_SHAPE);
        }

        this.roomId = roomId;
        this.authorId = authorId;
        this.title = strippedTitle;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = BookingStatus.ACTIVE;
        this.createdAt = createdAt;
        this.cancelledAt = null;
        this.cancelledById = null;
    }

    public Booking(Long roomId, Long authorId, String rawTitle, BookingInterval interval, Instant createdAt) {
        this(
                roomId,
                authorId,
                rawTitle,
                interval != null ? interval.startAt() : null,
                interval != null ? interval.endAt() : null,
                createdAt
        );
    }

    /**
     * Скасування бронювання (FR-17, FR-18, TR-05, INV-06, INV-07, A-17).
     * Порядок перевірок за специфікацією:
     * 1. Автор чи ADMIN (NOT_PERMITTED).
     * 2. Повтор для вже скасованого (безпечний повтор A-17 без змін і без помилки).
     * 3. Завершене бронювання (BOOKING_FINISHED).
     * 4. Переведення в CANCELLED зі збереженням моменту та ініціатора скасування.
     */
    public void cancel(Actor actor, OfficeClock.Snapshot snapshot) {
        Objects.requireNonNull(actor, "Actor must not be null");
        Objects.requireNonNull(snapshot, "OfficeClock.Snapshot must not be null");

        // 1. Перевірка прав
        if (!actor.isAdmin() && !this.authorId.equals(actor.userId())) {
            throw new DomainException(Reason.NOT_PERMITTED);
        }

        // 2. Безпечний повтор для вже скасованого
        if (this.status == BookingStatus.CANCELLED) {
            return;
        }

        // 3. Завершене бронювання
        if (isFinished(snapshot.localNow())) {
            throw new DomainException(Reason.BOOKING_FINISHED);
        }

        // 4. Фіксація скасування
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = snapshot.instant();
        this.cancelledById = actor.userId();
    }

    /**
     * Фаза ACTIVE-бронювання за поточним місцевим часом офісу (TR-05).
     * Для CANCELLED повертає null.
     */
    public BookingPhase phase(LocalDateTime localNow) {
        Objects.requireNonNull(localNow, "localNow must not be null");
        if (this.status == BookingStatus.CANCELLED) {
            return null;
        }
        if (localNow.isBefore(startAt)) {
            return BookingPhase.UPCOMING;
        }
        if (localNow.isBefore(endAt)) {
            return BookingPhase.IN_PROGRESS;
        }
        return BookingPhase.FINISHED;
    }

    /**
     * Чи завершилося бронювання (мить настання endAt включно означає завершення, TR-05).
     */
    public boolean isFinished(LocalDateTime localNow) {
        Objects.requireNonNull(localNow, "localNow must not be null");
        return !localNow.isBefore(endAt);
    }

    /**
     * Чи є бронювання незавершеним ACTIVE-бронюванням (INV-09, FR-17).
     */
    public boolean isUnfinished(LocalDateTime localNow) {
        Objects.requireNonNull(localNow, "localNow must not be null");
        return this.status == BookingStatus.ACTIVE && localNow.isBefore(endAt);
    }

    public BookingInterval interval() {
        return new BookingInterval(startAt, endAt);
    }

    // --- Геттери (лише читання) ---

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Long getCancelledById() {
        return cancelledById;
    }
}
