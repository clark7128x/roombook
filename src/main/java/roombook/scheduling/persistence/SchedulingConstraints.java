package roombook.scheduling.persistence;

import roombook.kernel.DomainException;
import roombook.kernel.Reason;
import roombook.platform.ConstraintNames;

import java.util.function.Supplier;

/**
 * Відображення винятків іменованих обмежень СУБД на доменні винятки модуля scheduling (D-04, AR-12).
 */
public final class SchedulingConstraints {

    public static final String EX_BOOKING_NO_OVERLAP = "ex_booking_no_overlap";
    public static final String UQ_ROOM_NAME_KEY = "uq_room_name_key";

    private SchedulingConstraints() {
    }

    /**
     * Відображає виняток СУБД на DomainException (AR-12).
     * Якщо виняток спричинений ex_booking_no_overlap -> кидає DomainException(Reason.SLOT_TAKEN).
     * Якщо виняток спричинений uq_room_name_key -> кидає DomainException(Reason.ROOM_NAME_TAKEN).
     * Інакше повертає вихідний RuntimeException або обгортає виняток у RuntimeException.
     */
    public static RuntimeException translate(Throwable throwable) {
        String constraint = ConstraintNames.getConstraintName(throwable);
        if (EX_BOOKING_NO_OVERLAP.equalsIgnoreCase(constraint)) {
            return new DomainException(Reason.SLOT_TAKEN);
        }
        if (UQ_ROOM_NAME_KEY.equalsIgnoreCase(constraint)) {
            return new DomainException(Reason.ROOM_NAME_TAKEN);
        }

        // Запасна перевірка за кодом SQLState 23P01 (exclusion_violation в PostgreSQL)
        String sqlState = ConstraintNames.findSqlState(throwable).orElse(null);
        if ("23P01".equals(sqlState)) {
            return new DomainException(Reason.SLOT_TAKEN);
        }

        if (throwable instanceof RuntimeException re) {
            return re;
        }
        return new RuntimeException(throwable);
    }

    /**
     * Перетворює та відразу викидає DomainException (AR-12).
     */
    public static void translateAndThrow(Throwable throwable) {
        throw translate(throwable);
    }

    /**
     * Виконує дію із перехопленням винятків обмежень СУБД та їх перетворенням на DomainException (AR-12).
     */
    public static <T> T execute(Supplier<T> action) {
        try {
            return action.get();
        } catch (Throwable t) {
            throw translate(t);
        }
    }

    /**
     * Виконує дію без повернення значення із перехопленням винятків обмежень СУБД (AR-12).
     */
    public static void execute(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            throw translate(t);
        }
    }
}
