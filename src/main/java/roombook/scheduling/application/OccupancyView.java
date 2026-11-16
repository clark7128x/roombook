package roombook.scheduling.application;

import java.time.LocalDate;
import java.util.List;

public record OccupancyView(LocalDate date, List<OccupancyRoom> rooms) {
}
