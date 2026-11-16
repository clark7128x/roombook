package roombook.kernel;

import java.util.Map;
import java.util.Objects;

/**
 * Опис помилки конкретного поля запиту (A-56, RFC 9457 errors).
 */
public record FieldError(String field, FieldRule rule, Map<String, Object> params) {

    public FieldError {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(rule, "rule must not be null");
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public FieldError(String field, FieldRule rule) {
        this(field, rule, Map.of());
    }
}
