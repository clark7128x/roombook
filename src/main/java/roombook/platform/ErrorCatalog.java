package roombook.platform;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import roombook.kernel.DomainException;
import roombook.kernel.FieldRule;
import roombook.kernel.Reason;
import roombook.kernel.TimeRule;

import java.util.Map;
import java.util.Objects;
import java.net.URI;

/** Single source for public error codes, statuses and Ukrainian messages (D-11, A-56). */
@Component
public class ErrorCatalog {
    public record Entry(HttpStatus status, String code, String template) {}
    public record InputError(String field, String message) {}

    private static final Map<Enum<?>, Entry> ENTRIES = Map.ofEntries(
            entry(Reason.INVALID_INPUT, 400, "Некоректні дані запиту"),
            entry(Reason.INVALID_TIME, 422, "Час бронювання не відповідає правилам"),
            entry(Reason.ROOM_NOT_FOUND, 404, "Кімнату не знайдено"),
            entry(Reason.ROOM_INACTIVE, 409, "Кімната неактивна, бронювання в ній неможливе"),
            entry(Reason.SLOT_TAKEN, 409, "Кімната вже зайнята на цей час"),
            entry(Reason.BOOKING_NOT_FOUND, 404, "Бронювання не знайдено"),
            entry(Reason.NOT_PERMITTED, 403, "Недостатньо прав для цієї дії"),
            entry(Reason.BOOKING_FINISHED, 409, "Бронювання вже завершилось, скасувати його не можна"),
            entry(Reason.ROOM_NAME_TAKEN, 409, "Кімната з такою назвою вже існує"),
            entry(Reason.ROOM_HAS_UNFINISHED_BOOKINGS, 409, "У кімнаті є незавершені бронювання, деактивувати її не можна"),
            entry(Reason.USERNAME_TAKEN, 409, "Користувач із таким логіном вже існує"),
            entry(Reason.USER_NOT_FOUND, 404, "Користувача не знайдено"),
            entry(Reason.LAST_ADMIN, 409, "Має лишитися хоча б один активний адміністратор"),
            entry(Reason.INVITATION_INVALID, 400, "Запрошення недійсне, використане або строк його дії минув. Зверніться до адміністратора"),
            entry(TransportCode.UNAUTHENTICATED, 401, "Потрібно увійти в систему"),
            entry(TransportCode.CSRF_INVALID, 403, "Недійсний токен захисту, оновіть сторінку"),
            entry(TransportCode.UNSUPPORTED_REQUEST, 404, "Запит не підтримується"),
            entry(TransportCode.INTERNAL_ERROR, 500, "Внутрішня помилка, спробуйте пізніше"),
            entry(TimeRule.INTERVAL_SHAPE, 422, "Початок має бути раніше кінця в межах однієї дати"),
            entry(TimeRule.OUTSIDE_WORK_HOURS, 422, "Бронювання можливе лише з {workDayStart} до {workDayEnd}"),
            entry(TimeRule.NOT_ON_GRID, 422, "Початок і кінець мають бути кратні {slotGrid} хвилинам"),
            entry(TimeRule.BAD_DURATION, 422, "Тривалість має бути від {minDuration} до {maxDuration} хвилин"),
            entry(TimeRule.IN_THE_PAST, 422, "Початок не може бути раніше поточного слота"),
            entry(TimeRule.TOO_FAR, 422, "Бронювання можливе не далі ніж на {horizonDays} днів уперед"),
            entry(FieldRule.REQUIRED, 400, "Поле є обов’язковим"),
            entry(FieldRule.LENGTH, 400, "Неприпустима довжина значення"),
            entry(FieldRule.RANGE, 400, "Значення поза допустимими межами"),
            entry(FieldRule.FORMAT, 400, "Некоректний формат значення"),
            entry(FieldRule.ILLEGAL_CHARACTERS, 400, "Значення містить недозволені символи"),
            entry(FieldRule.ILLEGAL_STATE, 400, "Несумісна комбінація параметрів")
    );

    private static Map.Entry<Enum<?>, Entry> entry(Enum<?> key, int status, String template) {
        String code = key instanceof TimeRule ? Reason.INVALID_TIME.name()
                : key instanceof FieldRule ? Reason.INVALID_INPUT.name() : key.name();
        return Map.entry(key, new Entry(HttpStatus.valueOf(status), code, template));
    }

    public Entry lookup(Enum<?> key) {
        return Objects.requireNonNull(ENTRIES.get(key), () -> "Missing error catalog entry: " + key);
    }

    public String message(Enum<?> key, Map<String, Object> parameters) {
        String result = lookup(key).template();
        for (var parameter : parameters.entrySet()) {
            result = result.replace("{" + parameter.getKey() + "}", String.valueOf(parameter.getValue()));
        }
        // Legacy callers may omit parameters. Never expose unresolved template tokens.
        return result.contains("{") ? lookup(Reason.INVALID_TIME).template() : result;
    }

    public ProblemDetail problem(Enum<?> key) {
        Entry entry = lookup(key);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(entry.status(), message(key, Map.of()));
        problem.setType(URI.create("about:blank"));
        problem.setTitle(entry.status().getReasonPhrase());
        problem.setProperty("code", entry.code());
        return problem;
    }

    public ProblemDetail problem(DomainException exception) {
        ProblemDetail problem = problem(exception.getReason());
        if (exception.getReason() == Reason.INVALID_TIME && exception.getTimeRule() != null) {
            problem.setDetail(message(exception.getTimeRule(), exception.getMessageParams()));
            problem.setProperty("rule", exception.getTimeRule().name());
        }
        if (exception.getReason() == Reason.INVALID_INPUT && !exception.getErrors().isEmpty()) {
            problem.setProperty("errors", exception.getErrors().stream()
                    .map(error -> new InputError(error.field(), message(error.rule(), error.params()))).toList());
        }
        return problem;
    }

    public ProblemDetail unsupported(int status) {
        if (status != 404 && status != 405 && status != 406 && status != 415) {
            throw new IllegalArgumentException("Unsupported transport status");
        }
        ProblemDetail problem = problem(TransportCode.UNSUPPORTED_REQUEST);
        problem.setStatus(status);
        problem.setTitle(HttpStatus.valueOf(status).getReasonPhrase());
        return problem;
    }
}
