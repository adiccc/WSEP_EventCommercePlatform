package UI.Controllers;

import application.ActiveOrderService;
import application.IAuth;
import application.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class SessionExitController {

    private final UserService userService;
    private final IAuth auth;
    private final ActiveOrderService activeOrderService;

    public SessionExitController(UserService userService, IAuth auth, ActiveOrderService activeOrderService) {
        this.userService = userService;
        this.auth = auth;
        this.activeOrderService = activeOrderService;
    }

    @PostMapping(value = "/cancel-event-queue", consumes = "text/plain")
    public void cancelEventQueue(@RequestBody String body) {
        if (body == null || body.isBlank()) {
            return;
        }

        String[] parts = body.trim().split(":");

        if (parts.length != 2) {
            return;
        }

        String token = parts[0];

        try {
            int eventId = Integer.parseInt(parts[1]);
            activeOrderService.cancelEventQueueEntry(token, eventId);
        } catch (NumberFormatException ignored) {
        }
    }

    @PostMapping(value = "/close-tab", consumes = "text/plain")
    public void closeTab(@RequestBody String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        token = token.replace("\"", "").trim();

        String role = auth.getRole(token).getValue();

        if ("GUEST".equals(role)) {
            userService.leaveStore(token);
            return;
        }

        if ("MEMBER".equals(role)) {
            userService.logout(token);
        }
    }
}