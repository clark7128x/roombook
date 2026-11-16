package roombook.scheduling.application;

import java.time.LocalDateTime;

public record RoomFilterQuery(Integer floor, Integer minCapacity, LocalDateTime freeFrom, LocalDateTime freeTo, Boolean includeInactive) {
}
