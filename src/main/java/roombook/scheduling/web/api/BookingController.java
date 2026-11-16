package roombook.scheduling.web.api;

import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import roombook.kernel.Actor;
import roombook.scheduling.application.*;
import roombook.scheduling.domain.BookingStatus;

@RestController
@RequestMapping("/api/v1")
public class BookingController {
    private final BookingService service;
    private final BookingQueries queries;
    public BookingController(BookingService service, BookingQueries queries) { this.service = service; this.queries = queries; }
    @PostMapping(value = "/bookings", consumes = "application/json")
    public ResponseEntity<BookingView> create(Actor actor, @RequestBody BookingInput input) {
        var booking = service.createBooking(actor, input.command());
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + booking.id())).body(booking);
    }
    @GetMapping("/bookings/{id}")
    public BookingView get(Actor actor, @PathVariable long id) { return queries.get(actor, id); }
    @PostMapping("/bookings/{id}/cancel")
    public BookingView cancel(Actor actor, @PathVariable long id) { return service.cancelBooking(actor, id); }
    @GetMapping("/me/bookings")
    public Page<BookingView> mine(Actor actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) BookingStatus status, @RequestParam(required = false) String order,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queries.myBookings(actor, new BookingFilterQuery(dateFrom, dateTo, status, null, null, order, page, size));
    }
    @GetMapping("/bookings")
    public Page<BookingView> all(Actor actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) BookingStatus status, @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long authorId, @RequestParam(required = false) String order,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queries.allBookings(actor, new BookingFilterQuery(dateFrom, dateTo, status, roomId, authorId, order, page, size));
    }
}
