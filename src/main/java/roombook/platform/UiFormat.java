package roombook.platform;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import org.springframework.stereotype.Component;
import roombook.kernel.OfficeClock;
@Component("uiFormat")
public class UiFormat {
    private static final Locale UK = Locale.forLanguageTag("uk");
    private final OfficeClock clock;
    public UiFormat(OfficeClock clock) { this.clock = clock; }
    public String date(LocalDate value) { return value == null ? "" : value.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")); }
    public String date(LocalDateTime value) { return value == null ? "" : date(value.toLocalDate()); }
    public String dateWithDay(LocalDate value) {
        if (value == null) return "";
        String day = value.getDayOfWeek().getDisplayName(TextStyle.FULL, UK);
        return day + ", " + date(value);
    }
    public String dateShortWithDay(LocalDateTime value) {
        if (value == null) return "";
        String day = value.getDayOfWeek().getDisplayName(TextStyle.SHORT, UK);
        return day + " " + value.format(DateTimeFormatter.ofPattern("dd.MM", UK));
    }
    public String time(LocalTime value) { return value == null ? "" : value.format(DateTimeFormatter.ofPattern("HH:mm")); }
    public String time(LocalDateTime value) { return value == null ? "" : time(value.toLocalTime()); }
    public String audit(Instant value) {
        return value == null ? "" : DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm").withZone(clock.getOfficeZone()).format(value);
    }
}
