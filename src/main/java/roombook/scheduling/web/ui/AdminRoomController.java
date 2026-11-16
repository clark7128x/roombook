package roombook.scheduling.web.ui;

import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import roombook.kernel.*;
import roombook.platform.UiForms;
import roombook.scheduling.application.*;

@Controller
@RequestMapping("/admin/rooms")
public class AdminRoomController {
    private final OccupancyQueries queries;
    private final RoomService service;
    private final UiForms forms;
    public AdminRoomController(OccupancyQueries queries, RoomService service, UiForms forms) {
        this.queries = queries; this.service = service; this.forms = forms;
    }
    @GetMapping public String list(Actor actor, Model model) {
        model.addAttribute("rooms", queries.findRooms(actor, new RoomFilterQuery(null, null, null, null, true)));
        return "scheduling/admin-rooms";
    }
    @GetMapping("/new") public String newForm(Model model) {
        model.addAttribute("room", new RoomForm("", "", "", ""));
        return "scheduling/admin-room-form";
    }
    @GetMapping("/{id}/edit") public String edit(Actor actor, @PathVariable long id, Model model) {
        var room = queries.getRoom(actor, id);
        model.addAttribute("room", new RoomForm(room.name(), Integer.toString(room.floor()), Integer.toString(room.capacity()), room.equipment()));
        model.addAttribute("roomId", id);
        return "scheduling/admin-room-form";
    }
    @PostMapping public ModelAndView create(Actor actor, @ModelAttribute RoomForm form, Model model,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        try {
            service.createRoom(actor, new CreateRoomCommand(form.name(), UiForms.integer(form.floor(), "floor"),
                    UiForms.integer(form.capacity(), "capacity"), form.equipment()));
            redirect.addFlashAttribute("successMessage", "Кімнату створено.");
            return UiForms.redirect(request, response, "/admin/rooms");
        } catch (DomainException exception) { return failure(form, model, request, exception); }
    }
    @PostMapping("/{id}") public ModelAndView update(Actor actor, @PathVariable long id, @ModelAttribute RoomForm form,
            Model model, HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        model.addAttribute("roomId", id);
        try {
            service.updateRoom(actor, id, new UpdateRoomCommand(form.name(), UiForms.integer(form.floor(), "floor"),
                    UiForms.integer(form.capacity(), "capacity"), form.equipment()));
            redirect.addFlashAttribute("successMessage", "Зміни збережено.");
            return UiForms.redirect(request, response, "/admin/rooms");
        } catch (DomainException exception) { return failure(form, model, request, exception); }
    }
    private ModelAndView failure(RoomForm form, Model model, HttpServletRequest request, DomainException exception) {
        model.addAttribute("room", form);
        return forms.failure("scheduling/admin-room-form" + (UiForms.htmx(request) ? " :: form" : ""), model, exception);
    }
    @PostMapping("/{id}/deactivate") public String deactivate(Actor actor, @PathVariable long id, Model model, HttpServletRequest request) {
        model.addAttribute("room", service.deactivateRoom(actor, id));
        return UiForms.htmx(request) ? "components/room-row :: row" : "redirect:/admin/rooms";
    }
    @PostMapping("/{id}/activate") public String activate(Actor actor, @PathVariable long id, Model model, HttpServletRequest request) {
        model.addAttribute("room", service.activateRoom(actor, id));
        return UiForms.htmx(request) ? "components/room-row :: row" : "redirect:/admin/rooms";
    }
    public record RoomForm(String name, String floor, String capacity, String equipment) {}
}
