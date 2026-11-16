package roombook.platform;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import roombook.kernel.DomainException;
import roombook.kernel.FieldRule;

/** Transport parsing never interprets an offset as office-local time. */
public final class WebDates {
    private WebDates() {}
    public static LocalDateTime localDateTime(String value, String field) {
        if (value == null) return null;
        try { return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
        catch (DateTimeParseException exception) { throw DomainException.invalidInput(field, FieldRule.FORMAT); }
    }
}
