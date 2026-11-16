package roombook.scheduling.web.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import roombook.kernel.Actor;
import roombook.scheduling.application.BookingFilterQuery;
import roombook.scheduling.application.BookingQueries;
import org.springframework.format.annotation.DateTimeFormat;
import roombook.scheduling.domain.BookingStatus;

import java.time.LocalDate;

/**
 * Контролер адміністратора для бронювань: PG-06, HX-03.
 */
@Controller
@RequestMapping("/admin/bookings")
public class AdminBookingController {
    private final BookingQueries bookingQueries;

    public AdminBookingController(BookingQueries bookingQueries) {
        this.bookingQueries = bookingQueries;
    }

    /**
     * PG-06: усі бронювання з фільтрами.
     */
    @GetMapping
    public String listBookings(
            Actor actor,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        var query = new BookingFilterQuery(dateFrom, dateTo, status, roomId, authorId, order, page, size);
        var result = bookingQueries.allBookings(actor, query);
        model.addAttribute("page", result);
        model.addAttribute("roomId", roomId);
        model.addAttribute("authorId", authorId);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("status", status == null ? "" : status.name());
        model.addAttribute("showAuthor", true);
        model.addAttribute("order", order);

        // Варіанти для фільтрів (A-34)
        var options = bookingQueries.filterOptions(actor);
        model.addAttribute("rooms", options.rooms());
        model.addAttribute("users", options.users());

        return "scheduling/admin-bookings";
    }

}
