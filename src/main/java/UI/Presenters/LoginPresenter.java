package UI.Presenters;

import application.Response;
import application.UserService;
import domain.dto.UserDTO;

/**
 * Presenter for LoginView — follows the lecture pattern:
 *
 *   Presenter holds: services only (no reference to the View)
 *   Presenter methods: return data/Response so the View decides what to display
 *
 *   View calls presenter → gets a Response back → updates itself
 */
public class LoginPresenter {

    private final UserService userService;

    public LoginPresenter(UserService userService) {
        this.userService = userService;
    }

    /**
     * Attempts login. Returns the service Response so the view can
     * inspect success/failure and update its own components.
     */
    public Response<String> login(String email, String password) {
        return userService.login(email, password);
    }
}
