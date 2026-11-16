package roombook.scheduling.web.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import roombook.kernel.DomainException;
import roombook.kernel.FieldRule;
import roombook.scheduling.application.CreateBookingCommand;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingInput(Long roomId, String title, LocalDateTime startAt, LocalDateTime endAt) {
    public CreateBookingCommand command() {
        year(startAt, "startAt"); year(endAt, "endAt");
        return new CreateBookingCommand(roomId, title, startAt, endAt);
    }
    private static void year(LocalDateTime value, String field) {
        if (value != null && (value.getYear() < 2000 || value.getYear() > 2100))
            throw DomainException.invalidInput(field, FieldRule.RANGE);
    }
}
