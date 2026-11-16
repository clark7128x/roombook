package roombook.scheduling.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roombook.accounts.api.UserDirectory;
import roombook.kernel.*;
import roombook.scheduling.domain.Booking;
import roombook.scheduling.domain.BookingTimePolicy;
import roombook.scheduling.persistence.BookingRepository;
import roombook.scheduling.persistence.RoomRepository;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OccupancyQueries {
    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final UserDirectory users;
    private final BookingTimePolicy timePolicy;
    private final OfficeClock clock;

    public OccupancyQueries(RoomRepository rooms, BookingRepository bookings, UserDirectory users,
            BookingTimePolicy timePolicy, OfficeClock clock) {
        this.rooms = Objects.requireNonNull(rooms);
        this.bookings = Objects.requireNonNull(bookings);
        this.users = Objects.requireNonNull(users);
        this.timePolicy = Objects.requireNonNull(timePolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    public OccupancyView getOccupancy(Actor actor, OccupancyQuery query) {
        Objects.requireNonNull(actor);
        if (query == null) throw new DomainException(Reason.INVALID_INPUT);
        LocalDate date = query.date() == null ? clock.snapshot().today() : query.date();
        QuerySupport.date(date, "date");
        QuerySupport.capacity(query.minCapacity());
        List<RoomView> roomViews = QuerySupport.rooms(rooms.findMatching(query.floor(), query.minCapacity(),
                false, false, null, null));
        List<Booking> daily = bookings.findOccupancy(date.atStartOfDay(), date.plusDays(1).atStartOfDay(),
                query.floor(), query.minCapacity());
        Map<Long, String> names = users.displayNames(daily.stream().map(Booking::getAuthorId).collect(Collectors.toSet()));
        Map<Long, List<OccupancyBooking>> byRoom = new HashMap<>();
        for (Booking booking : daily) {
            byRoom.computeIfAbsent(booking.getRoomId(), ignored -> new ArrayList<>()).add(new OccupancyBooking(
                    booking.getId(), booking.getStartAt(), booking.getEndAt(), booking.getTitle(),
                    new UserRef(booking.getAuthorId(), names.getOrDefault(booking.getAuthorId(), "User #" + booking.getAuthorId()))));
        }
        return new OccupancyView(date, roomViews.stream().map(room -> new OccupancyRoom(room,
                List.copyOf(byRoom.getOrDefault(room.id(), List.of())))).toList());
    }

    public List<RoomView> findRooms(Actor actor, RoomFilterQuery query) {
        Objects.requireNonNull(actor);
        if (query == null) throw new DomainException(Reason.INVALID_INPUT);
        boolean includeInactive = Boolean.TRUE.equals(query.includeInactive());
        if (includeInactive) actor.requireAdmin();
        QuerySupport.capacity(query.minCapacity());
        if (query.freeFrom() == null && query.freeTo() != null) {
            throw DomainException.invalidInput("freeFrom", FieldRule.REQUIRED);
        }
        if (query.freeFrom() != null && query.freeTo() == null) {
            throw DomainException.invalidInput("freeTo", FieldRule.REQUIRED);
        }
        boolean free = query.freeFrom() != null;
        if (free) {
            if (includeInactive) throw DomainException.invalidInput("includeInactive", FieldRule.ILLEGAL_STATE);
            QuerySupport.date(query.freeFrom().toLocalDate(), "freeFrom");
            QuerySupport.date(query.freeTo().toLocalDate(), "freeTo");
            timePolicy.validateForSearch(query.freeFrom(), query.freeTo());
        }
        return QuerySupport.rooms(rooms.findMatching(query.floor(), query.minCapacity(), includeInactive,
                free, query.freeFrom(), query.freeTo()));
    }

    public RoomView getRoom(Actor actor, long roomId) {
        Objects.requireNonNull(actor);
        return QuerySupport.view(rooms.findById(roomId).orElseThrow(() -> new DomainException(Reason.ROOM_NOT_FOUND)));
    }
}
