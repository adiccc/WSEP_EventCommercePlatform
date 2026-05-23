package UI.Controllers;

import application.IAuth;
import application.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class SessionExitController {

    private final UserService userService;
    private final IAuth auth;

    public SessionExitController(UserService userService, IAuth auth) {
        this.userService = userService;
        this.auth = auth;
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