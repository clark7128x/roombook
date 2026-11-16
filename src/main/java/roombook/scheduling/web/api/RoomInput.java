package roombook.scheduling.web.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import roombook.scheduling.application.CreateRoomCommand;
import roombook.scheduling.application.UpdateRoomCommand;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomInput(String name, Integer floor, Integer capacity, String equipment) {
    public CreateRoomCommand createCommand() { return new CreateRoomCommand(name, floor, capacity, equipment); }
    public UpdateRoomCommand updateCommand() { return new UpdateRoomCommand(name, floor, capacity, equipment); }
}
