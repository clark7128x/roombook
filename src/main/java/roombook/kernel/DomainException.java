package roombook.kernel;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Неперевірений виняток доменної логіки (A-56, AR-15).
 * Не містить текстів користувача (вони формуються в platform).
 */
public class DomainException extends RuntimeException {

    private final Reason reason;
    private final String rule;
    private final List<FieldError> errors;
    private final Map<String, Object> messageParams;

    public DomainException(Reason reason) {
        this(reason, (String) null, List.of(), Map.of());
    }

    public DomainException(Reason reason, String rule) {
        this(reason, rule, List.of(), Map.of());
    }

    public DomainException(Reason reason, TimeRule rule) {
        this(reason, rule != null ? rule.name() : null, List.of(), Map.of());
    }

    public DomainException(Reason reason, TimeRule rule, Map<String, Object> messageParams) {
        this(reason, rule != null ? rule.name() : null, List.of(), messageParams);
    }

    public DomainException(Reason reason, List<FieldError> errors) {
        this(reason, (String) null, errors, Map.of());
    }

    public DomainException(Reason reason, String rule, List<FieldError> errors, Map<String, Object> messageParams) {
        super(buildMessage(reason, rule, errors, messageParams));
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.rule = rule;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.messageParams = messageParams == null ? Map.of() : Map.copyOf(messageParams);
    }

    private static String buildMessage(Reason reason, String rule, List<FieldError> errors, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append(reason != null ? reason.name() : "UNKNOWN");
        if (rule != null && !rule.isBlank()) {
            sb.append(": ").append(rule);
        }
        if (errors != null && !errors.isEmpty()) {
            sb.append(" ").append(errors);
        }
        if (params != null && !params.isEmpty()) {
            sb.append(" params=").append(params);
        }
        return sb.toString();
    }

    public Reason getReason() {
        return reason;
    }

    public String getRule() {
        return rule;
    }

    public TimeRule getTimeRule() {
        if (rule == null) {
            return null;
        }
        try {
            return TimeRule.valueOf(rule);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public List<FieldError> getFieldErrors() {
        return errors;
    }

    public Map<String, Object> getMessageParams() {
        return messageParams;
    }

    public static DomainException invalidTime(TimeRule rule, Map<String, Object> params) {
        return new DomainException(Reason.INVALID_TIME, rule, params);
    }

    public static DomainException invalidInput(List<FieldError> errors) {
        return new DomainException(Reason.INVALID_INPUT, errors);
    }

    public static DomainException invalidInput(String field, FieldRule rule) {
        return new DomainException(Reason.INVALID_INPUT, List.of(new FieldError(field, rule)));
    }

    public static DomainException invalidInput(String field, FieldRule rule, Map<String, Object> params) {
        return new DomainException(Reason.INVALID_INPUT, List.of(new FieldError(field, rule, params)));
    }
}
