package roombook.accounts.persistence;

import roombook.kernel.DomainException;
import roombook.kernel.Reason;
import roombook.platform.ConstraintNames;

import java.util.function.Supplier;

/**
 * Відображення винятків іменованих обмежень СУБД на доменні винятки модуля accounts (D-04, AR-12, INV-13).
 */
public final class AccountsConstraints {

    public static final String UQ_APP_USER_USERNAME_KEY = "uq_app_user_username_key";

    private AccountsConstraints() {
    }

    /**
     * Відображає виняток СУБД на DomainException (AR-12).
     * Якщо виняток спричинений uq_app_user_username_key -> кидає DomainException(Reason.USERNAME_TAKEN).
     * Інакше повертає вихідний RuntimeException або обгортає виняток у RuntimeException.
     */
    public static RuntimeException translate(Throwable throwable) {
        String constraint = ConstraintNames.getConstraintName(throwable);
        if (UQ_APP_USER_USERNAME_KEY.equalsIgnoreCase(constraint)) {
            return new DomainException(Reason.USERNAME_TAKEN);
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
