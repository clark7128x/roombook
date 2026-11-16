package roombook.scheduling.web.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import roombook.kernel.Actor;
import roombook.platform.WebDates;
import roombook.scheduling.application.*;

@RestController
@RequestMapping("/api/v1")
public class RoomController {
    private final OccupancyQueries queries;
    private final RoomService service;
    public RoomController(OccupancyQueries queries, RoomService service) { this.queries = queries; this.service = service; }

    @GetMapping("/rooms")
    public List<RoomView> rooms(Actor actor, @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) Integer minCapacity, @RequestParam(required = false) String freeFrom,
            @RequestParam(required = false) String freeTo, @RequestParam(defaultValue = "false") Boolean includeInactive) {
        return queries.findRooms(actor, new RoomFilterQuery(floor, minCapacity,
                WebDates.localDateTime(freeFrom, "freeFrom"), WebDates.localDateTime(freeTo, "freeTo"), includeInactive));
    }
    @GetMapping("/rooms/{id}")
    public RoomView room(Actor actor, @PathVariable long id) { return queries.getRoom(actor, id); }

    @PostMapping(value = "/rooms", consumes = "application/json")
    public ResponseEntity<RoomView> create(Actor actor, @RequestBody RoomInput input) {
        var room = service.createRoom(actor, input.createCommand());
        return ResponseEntity.created(URI.create("/api/v1/rooms/" + room.id())).body(room);
    }
    @PutMapping(value = "/rooms/{id}", consumes = "application/json")
    public RoomView update(Actor actor, @PathVariable long id, @RequestBody RoomInput input) {
        return service.updateRoom(actor, id, input.updateCommand());
    }
    @PostMapping("/rooms/{id}/activate")
    public RoomView activate(Actor actor, @PathVariable long id) { return service.activateRoom(actor, id); }
    @PostMapping("/rooms/{id}/deactivate")
    public RoomView deactivate(Actor actor, @PathVariable long id) { return service.deactivateRoom(actor, id); }
    @GetMapping("/occupancy")
    public OccupancyView occupancy(Actor actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer floor, @RequestParam(required = false) Integer minCapacity) {
        return queries.getOccupancy(actor, new OccupancyQuery(date, floor, minCapacity));
    }
}
