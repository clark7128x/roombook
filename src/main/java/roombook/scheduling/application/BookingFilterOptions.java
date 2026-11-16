package roombook.scheduling.application;

import java.util.List;
import roombook.accounts.api.UserDirectory.SelectableUser;

public record BookingFilterOptions(List<RoomView> rooms, List<SelectableUser> users) {
}
