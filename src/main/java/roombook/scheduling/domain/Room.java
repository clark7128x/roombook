package roombook.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import roombook.kernel.ComparisonKey;
import roombook.kernel.DomainException;
import roombook.kernel.FieldError;
import roombook.kernel.FieldRule;
import roombook.kernel.Reason;
import roombook.kernel.TextRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Сутність кімнати (INV-11, INV-12, FR-19...FR-22, A-06, A-14, A-15, A-17, A-25, A-36, AR-04).
 * Без публічних сеттерів. Зміна атрибутів відбувається через бізнес-методи.
 */
@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "name_key", nullable = false, length = 200)
    private String nameKey;

    @Column(name = "floor", nullable = false)
    private int floor;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "equipment", length = 500)
    private String equipment;

    @Column(name = "active", nullable = false)
    private boolean active;

    /**
     * Конструктор для JPA.
     */
    protected Room() {
    }

    /**
     * Створення нової кімнати. За замовчуванням active = true (FR-19).
     */
    public Room(String rawName, Integer floor, Integer capacity, String rawEquipment) {
        ValidatedRoomFields validated = validateFields(rawName, floor, capacity, rawEquipment);
        this.name = validated.name();
        this.nameKey = validated.nameKey();
        this.floor = validated.floor();
        this.capacity = validated.capacity();
        this.equipment = validated.equipment();
        this.active = true;
    }

    /**
     * Оновлення атрибутів кімнати (FR-20). Поле active цим методом не змінюється.
     * Ключ назви nameKey автоматично перераховується при кожній зміні name (A-15).
     */
    public void update(String rawName, Integer floor, Integer capacity, String rawEquipment) {
        ValidatedRoomFields validated = validateFields(rawName, floor, capacity, rawEquipment);
        this.name = validated.name();
        this.nameKey = validated.nameKey();
        this.floor = validated.floor();
        this.capacity = validated.capacity();
        this.equipment = validated.equipment();
    }

    /**
     * Активація кімнати (FR-22). Безпечний повтор (ідемпотентна дія, A-17).
     */
    public void activate() {
        this.active = true;
    }

    /**
     * Деактивація кімнати (FR-21). Безпечний повтор (ідемпотентна дія, A-17).
     * Рішення про можливість деактивації (відсутність незавершених бронювань за INV-09) приймає сервіс.
     */
    public void deactivate() {
        this.active = false;
    }

    private static ValidatedRoomFields validateFields(String rawName, Integer floor, Integer capacity, String rawEquipment) {
        List<FieldError> errors = new ArrayList<>();

        // 1. name: від 1 до 100 кодових точок після strip, без керувальних символів (A-14, A-36, INV-11)
        String strippedName = null;
        if (rawName == null) {
            errors.add(new FieldError("name", FieldRule.REQUIRED));
        } else {
            strippedName = TextRules.strip(rawName);
            if (strippedName.isEmpty()) {
                errors.add(new FieldError("name", FieldRule.REQUIRED));
            } else if (TextRules.codePointLength(strippedName) > 100) {
                errors.add(new FieldError("name", FieldRule.LENGTH));
            } else if (TextRules.hasControlCharacters(strippedName, false)) {
                errors.add(new FieldError("name", FieldRule.ILLEGAL_CHARACTERS));
            }
        }

        // 2. floor: від -5 до 100 включно (A-14)
        if (floor == null) {
            errors.add(new FieldError("floor", FieldRule.REQUIRED));
        } else if (floor < -5 || floor > 100) {
            errors.add(new FieldError("floor", FieldRule.RANGE));
        }

        // 3. capacity: від 1 до 1000 включно (A-14, INV-12)
        if (capacity == null) {
            errors.add(new FieldError("capacity", FieldRule.REQUIRED));
        } else if (capacity < 1 || capacity > 1000) {
            errors.add(new FieldError("capacity", FieldRule.RANGE));
        }

        // 4. equipment: до 500 кодових точок, порожнє після strip стає null (A-14, A-25, A-36), переноси рядків дозволені
        String cleanedEquipment = null;
        if (rawEquipment != null) {
            String strippedEq = TextRules.strip(rawEquipment);
            if (!strippedEq.isEmpty()) {
                if (TextRules.codePointLength(strippedEq) > 500) {
                    errors.add(new FieldError("equipment", FieldRule.LENGTH));
                } else if (TextRules.hasControlCharacters(strippedEq, true)) {
                    errors.add(new FieldError("equipment", FieldRule.ILLEGAL_CHARACTERS));
                } else {
                    cleanedEquipment = strippedEq;
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new DomainException(Reason.INVALID_INPUT, errors);
        }

        String nameKey = ComparisonKey.calculate(strippedName);
        return new ValidatedRoomFields(strippedName, nameKey, floor, capacity, cleanedEquipment);
    }

    private record ValidatedRoomFields(String name, String nameKey, int floor, int capacity, String equipment) {
    }

    // --- Геттери (лише читання) ---

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public int getFloor() {
        return floor;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getEquipment() {
        return equipment;
    }

    public boolean isActive() {
        return active;
    }
}
