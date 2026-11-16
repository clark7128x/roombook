package roombook.scheduling.application;

import java.util.List;

public record OccupancyRoom(RoomView room, List<OccupancyBooking> bookings) {
}
