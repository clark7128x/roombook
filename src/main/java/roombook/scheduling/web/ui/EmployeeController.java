package roombook.scheduling.web.ui;

import jakarta.servlet.http.*;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import roombook.kernel.*;
import roombook.platform.UiForms;
import roombook.platform.UiFormat;
import roombook.scheduling.application.*;
import roombook.scheduling.domain.*;

@Controller
public class EmployeeController {
    private final OccupancyQueries occupancy;
    private final BookingQueries bookings;
    private final BookingService service;
    private final OfficeClock clock;
    private final BookingRules rules;
    private final UiForms forms;
    private final UiFormat format;
    public EmployeeController(OccupancyQueries occupancy, BookingQueries bookings, BookingService service,
            OfficeClock clock, BookingRules rules, UiForms forms, UiFormat format) {
        this.occupancy = occupancy; this.bookings = bookings; this.service = service;
        this.clock = clock; this.rules = rules; this.forms = forms; this.format = format;
    }
    private record Notice(String message, long bookingId) implements java.io.Serializable {}
    @GetMapping("/") public String home() { return "redirect:/occupancy"; }

    @GetMapping("/occupancy")
    public String occupancy(Actor actor, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer floor, @RequestParam(required = false) Integer minCapacity,
            Model model, HttpServletRequest request) {
        var snapshot = clock.snapshot();
        var view = occupancy.getOccupancy(actor, new OccupancyQuery(date, floor, minCapacity));
        var timeline = RoomTimeline.build(view, snapshot, rules, actor);
        model.addAttribute("timeline", timeline);
        model.addAttribute("horizontal", HorizontalTimeline.from(timeline));
        model.addAttribute("roomCount", view.rooms().size());
        model.addAttribute("workStart", rules.workDayStart()); model.addAttribute("workEnd", rules.workDayEnd());
        model.addAttribute("officeZone", clock.getOfficeZone().getId());
        model.addAttribute("date", view.date()); model.addAttribute("floor", floor); model.addAttribute("minCapacity", minCapacity);
        model.addAttribute("currentTime", snapshot.localNow());
        var session = request.getSession(false);
        if (session != null) synchronized (session) {
            if (session.getAttribute("bookingNotice") instanceof Notice notice) {
                model.addAttribute("successMessage", notice.message()); model.addAttribute("freshBookingId", notice.bookingId());
                session.removeAttribute("bookingNotice");
            }
        }
        return "scheduling/occupancy";
    }
    @GetMapping("/rooms")
    public String rooms(Actor actor, @RequestParam(required = false) Integer floor, @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) String date, @RequestParam(required = false) String start,
            @RequestParam(required = false) String end, Model model) {
        boolean interval = present(date) || present(start) || present(end);
        LocalDate day = interval ? date(date) : null;
        var result = occupancy.findRooms(actor, new RoomFilterQuery(floor, minCapacity,
                interval ? day.atTime(time(start, "start")) : null, interval ? day.atTime(time(end, "end")) : null, false));
        model.addAttribute("rooms", result); model.addAttribute("floor", floor); model.addAttribute("minCapacity", minCapacity);
        model.addAttribute("searchDate", date); model.addAttribute("searchStart", start); model.addAttribute("searchEnd", end);
        model.addAttribute("starts", times(false)); model.addAttribute("ends", times(true));
        return "scheduling/rooms";
    }
    @GetMapping("/bookings/new")
    public String form(Actor actor, @RequestParam(required = false) String roomId, @RequestParam(required = false) String date,
            @RequestParam(required = false) String start, @RequestParam(required = false) String end, Model model) {
        var snapshot = clock.snapshot(); LocalDate day = present(date) ? date(date) : snapshot.today();
        var view = occupancy.getOccupancy(actor, new OccupancyQuery(day, null, null));
        String selected = view.rooms().stream().map(item -> Long.toString(item.room().id())).filter(id -> id.equals(roomId))
                .findFirst().orElse(view.rooms().isEmpty() ? "" : Long.toString(view.rooms().getFirst().room().id()));
        var starts = times(false); var ends = times(true);
        LocalTime suggested = rules.workDayStart();
        if (day.equals(snapshot.today())) {
            var next = BookingTimePolicy.computeCurrentSlotStart(snapshot.localNow(), rules.slotGrid().toMinutes()).plus(rules.slotGrid());
            suggested = next.toLocalTime().isBefore(rules.workDayStart()) ? rules.workDayStart() : next.toLocalTime();
            if (!next.toLocalDate().equals(day)) suggested = rules.workDayEnd();
        }
        String chosenStart = starts.contains(start) ? start : starts.contains(suggested.toString()) ? suggested.toString() : "";
        String chosenEnd = "";
        if (ends.contains(end)) chosenEnd = end;
        else if (present(chosenStart)) {
            LocalTime proposed = LocalTime.parse(chosenStart).plusHours(1);
            chosenEnd = (proposed.isAfter(rules.workDayEnd()) ? rules.workDayEnd() : proposed).toString();
        }
        prepare(actor, view, new BookingForm(selected, day.toString(), chosenStart, chosenEnd, ""), model);
        model.addAttribute("afterHours", chosenStart.isEmpty());
        return "scheduling/booking-new";
    }
    @GetMapping("/fragments/room-day")
    public String roomDay(Actor actor, @RequestParam long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        var view = occupancy.getOccupancy(actor, new OccupancyQuery(date, null, null));
        if (view.rooms().stream().noneMatch(item -> item.room().id() == roomId)) throw new DomainException(Reason.ROOM_NOT_FOUND);
        prepare(actor, view, new BookingForm(Long.toString(roomId), date.toString(), "", "", ""), model);
        return "components/room-day :: content";
    }
    @PostMapping(value = "/bookings", consumes = "application/x-www-form-urlencoded")
    public ModelAndView create(Actor actor, @ModelAttribute BookingForm form, Model model,
            HttpServletRequest request, HttpServletResponse response) {
        try {
            LocalDate day = date(form.date());
            Long roomId;
            try { roomId = present(form.roomId()) ? Long.valueOf(form.roomId()) : null; }
            catch (NumberFormatException exception) { throw DomainException.invalidInput("roomId", FieldRule.FORMAT); }
            var booking = service.createBooking(actor, new CreateBookingCommand(roomId, form.title(),
                    day.atTime(time(form.start(), "start")), day.atTime(time(form.end(), "end"))));
            String message = "Бронювання створено: " + booking.room().name() + ", " + format.date(day) + ", "
                    + format.time(booking.startAt()) + "–" + format.time(booking.endAt()) + ".";
            request.getSession().setAttribute("bookingNotice", new Notice(message, booking.id()));
            String target = "/occupancy?date=" + day;
            if (htmx(request)) { response.setHeader("HX-Redirect", target); response.setStatus(200); return null; }
            return new ModelAndView("redirect:" + target);
        } catch (DomainException exception) {
            LocalDate day;
            try { day = date(form.date()); } catch (DomainException invalid) { day = clock.today(); }
            prepare(actor, occupancy.getOccupancy(actor, new OccupancyQuery(day, null, null)), form, model);
            model.addAttribute("refreshRoomDay", exception.getReason() == Reason.SLOT_TAKEN || exception.getReason() == Reason.ROOM_INACTIVE);
            return forms.failure(htmx(request) ? "scheduling/booking-response" : "scheduling/booking-new", model, exception);
        }
    }
    @GetMapping("/bookings/mine")
    public String mine(Actor actor, @RequestParam(defaultValue = "upcoming") String tab,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Model model) {
        if (!Set.of("upcoming", "past").contains(tab)) throw DomainException.invalidInput("tab", FieldRule.FORMAT);
        LocalDate today = clock.today(); boolean past = tab.equals("past");
        var result = bookings.myBookings(actor, new BookingFilterQuery(past ? null : today, past ? today.minusDays(1) : null,
                null, null, null, past ? "desc" : "asc", page, size));
        model.addAttribute("page", result); model.addAttribute("tab", tab); model.addAttribute("showAuthor", false);
        return "scheduling/my-bookings";
    }
    @PostMapping("/bookings/{id}/cancel")
    public String cancel(Actor actor, @PathVariable long id, @RequestParam(defaultValue = "false") boolean adminView,
            Model model, HttpServletRequest request) {
        model.addAttribute("booking", service.cancelBooking(actor, id));
        model.addAttribute("showAuthor", adminView && actor.isAdmin());
        return htmx(request) ? "components/booking-row :: row" : "redirect:" + (adminView && actor.isAdmin() ? "/admin/bookings" : "/bookings/mine");
    }
    private void prepare(Actor actor, OccupancyView view, BookingForm form, Model model) {
        model.addAttribute("form", form); model.addAttribute("rooms", view.rooms().stream().map(OccupancyRoom::room).toList());
        model.addAttribute("starts", times(false)); model.addAttribute("ends", times(true));
        model.addAttribute("workStart", rules.workDayStart().toString()); model.addAttribute("workEnd", rules.workDayEnd().toString());
        model.addAttribute("minimumMinutes", rules.minDuration().toMinutes()); model.addAttribute("maximumMinutes", rules.maxDuration().toMinutes());
        var selected = view.rooms().stream().filter(item -> Long.toString(item.room().id()).equals(form.roomId())).findFirst();
        model.addAttribute("selectedRoom", selected.map(OccupancyRoom::room).orElse(null));
        model.addAttribute("roomTimeline", selected.map(item -> RoomTimeline.build(new OccupancyView(view.date(), List.of(item)), clock.snapshot(), rules, actor)).orElse(null));
        model.addAttribute("refreshRoomDay", false);
        model.addAttribute("afterHours", false);
    }
    private List<String> times(boolean end) {
        var values = new ArrayList<String>();
        long start = rules.workDayStart().toSecondOfDay() / 60, finish = rules.workDayEnd().toSecondOfDay() / 60, step = rules.slotGrid().toMinutes();
        for (long minute = start + (end ? step : 0); minute <= finish - (end ? 0 : step); minute += step)
            values.add(LocalTime.ofSecondOfDay(minute * 60).toString());
        return values;
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static boolean htmx(HttpServletRequest request) { return "true".equalsIgnoreCase(request.getHeader("HX-Request")); }
    private static LocalDate date(String value) {
        if (!present(value)) throw DomainException.invalidInput("date", FieldRule.REQUIRED);
        LocalDate date;
        try { date = LocalDate.parse(value); } catch (java.time.format.DateTimeParseException exception) { throw DomainException.invalidInput("date", FieldRule.FORMAT); }
        if (date.getYear() < 2000 || date.getYear() > 2100) throw DomainException.invalidInput("date", FieldRule.RANGE);
        return date;
    }
    private static LocalTime time(String value, String field) {
        if (!present(value)) throw DomainException.invalidInput(field, FieldRule.REQUIRED);
        try { return LocalTime.parse(value); } catch (java.time.format.DateTimeParseException exception) { throw DomainException.invalidInput(field, FieldRule.FORMAT); }
    }
}
