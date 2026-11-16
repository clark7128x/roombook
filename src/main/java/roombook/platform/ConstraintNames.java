package roombook.platform;

import org.hibernate.exception.ConstraintViolationException;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утиліта вилучення імені порушеного обмеження БД та SQLState (D-04, OPS-01, AR-12).
 */
public final class ConstraintNames {

    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile(
            "(?:constraint|обмеження)\\s+[\"']?([^\"'\\s.]+)[\"']?",
            Pattern.CASE_INSENSITIVE
    );

    private ConstraintNames() {
    }

    /**
     * Пошук імені порушеного обмеження в ланцюгу причин винятку.
     */
    public static Optional<String> findConstraintName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            // 1. PostgreSQL JDBC ServerErrorMessage (через рефлексію, оскільки postgresql у scope runtime)
            String pgConstraint = extractPgConstraint(current);
            if (pgConstraint != null && !pgConstraint.isBlank()) {
                return Optional.of(pgConstraint);
            }

            // 2. Hibernate ConstraintViolationException
            if (current instanceof ConstraintViolationException cve) {
                if (cve.getConstraintName() != null && !cve.getConstraintName().isBlank()) {
                    return Optional.of(cve.getConstraintName());
                }
            }

            // 3. Fallback: парсинг повідомлення за регулярним виразом
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                Matcher matcher = CONSTRAINT_PATTERN.matcher(message);
                if (matcher.find()) {
                    return Optional.of(matcher.group(1));
                }
            }

            current = current.getCause();
        }
        return Optional.empty();
    }

    private static String extractPgConstraint(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        if (throwable.getClass().getName().endsWith("PSQLException")) {
            try {
                Method getSemMethod = throwable.getClass().getMethod("getServerErrorMessage");
                Object sem = getSemMethod.invoke(throwable);
                if (sem != null) {
                    Method getConstraintMethod = sem.getClass().getMethod("getConstraint");
                    Object constraint = getConstraintMethod.invoke(sem);
                    if (constraint instanceof String s && !s.isBlank()) {
                        return s;
                    }
                }
            } catch (Exception ignored) {
                // Fallback to message or cause
            }
        }
        return null;
    }

    /**
     * Отримання імені порушеного обмеження або null.
     */
    public static String getConstraintName(Throwable throwable) {
        return findConstraintName(throwable).orElse(null);
    }

    /**
     * Пошук SQLState у ланцюгу причин винятку.
     */
    public static Optional<String> findSqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlEx && sqlEx.getSQLState() != null) {
                return Optional.of(sqlEx.getSQLState());
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    /**
     * Отримання SQLState або null.
     */
    public static String getSqlState(Throwable throwable) {
        return findSqlState(throwable).orElse(null);
    }
}
