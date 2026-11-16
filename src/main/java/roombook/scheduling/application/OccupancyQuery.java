package roombook.scheduling.application;

import java.time.LocalDate;

public record OccupancyQuery(LocalDate date, Integer floor, Integer minCapacity) {
}
