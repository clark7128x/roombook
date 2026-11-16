package roombook.scheduling.application;

import roombook.kernel.DomainException;
import roombook.kernel.FieldRule;
import roombook.scheduling.domain.Room;
import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class QuerySupport {
    private QuerySupport() {}

    static void date(LocalDate date, String field) {
        if (date != null && (date.getYear() < 2000 || date.getYear() > 2100)) {
            throw DomainException.invalidInput(field, FieldRule.RANGE);
        }
    }

    static void capacity(Integer capacity) {
        if (capacity != null && capacity < 1) {
            throw DomainException.invalidInput("minCapacity", FieldRule.RANGE);
        }
    }

    static RoomView view(Room room) {
        return new RoomView(room.getId(), room.getName(), room.getFloor(), room.getCapacity(),
                room.getEquipment(), room.isActive());
    }

    static List<RoomView> rooms(List<Room> rooms) {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("uk-UA"));
        return rooms.stream().map(QuerySupport::view)
                .sorted(Comparator.comparingInt(RoomView::floor)
                        .thenComparing(RoomView::name, collator).thenComparingLong(RoomView::id))
                .toList();
    }
}
