package roombook.accounts.web.api;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import roombook.accounts.application.*;
import roombook.kernel.Actor;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final AccountService service;
    private final AccountQueries queries;
    public UserController(AccountService service, AccountQueries queries) { this.service = service; this.queries = queries; }
    @GetMapping public List<UserView> list(Actor actor) { return queries.listUsers(actor); }
    @GetMapping("/{id}") public UserView get(Actor actor, @PathVariable long id) { return queries.getUser(actor, id); }
    @PostMapping(consumes = "application/json")
    public ResponseEntity<UserView> create(Actor actor, @RequestBody UserInput input) {
        var user = service.createUser(actor, input.command());
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }
    @PutMapping(value = "/{id}/role", consumes = "application/json")
    public UserView role(Actor actor, @PathVariable long id, @RequestBody RoleInput input) {
        return service.changeUserRole(actor, id, input.command());
    }
    @PostMapping("/{id}/disable") public UserView disable(Actor actor, @PathVariable long id) { return service.disableUser(actor, id); }
    @PostMapping("/{id}/enable") public UserView enable(Actor actor, @PathVariable long id) { return service.enableUser(actor, id); }
    @PutMapping(value = "/{id}/password", consumes = "application/json")
    public ResponseEntity<Void> password(Actor actor, @PathVariable long id, @RequestBody PasswordInput input) {
        service.resetPassword(actor, id, input.command());
        return ResponseEntity.noContent().build();
    }
}
