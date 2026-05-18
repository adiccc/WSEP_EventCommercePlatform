package UI.Presenters;

import application.UserService;
import application.Response;

public class LogoutPresenter {

    private final UserService userService;

    public LogoutPresenter(UserService userService) {
        this.userService = userService;
    }

    /**
     * Logs out the current user.
     * - MEMBER -> calls UserService.logout(token)
     * - GUEST  -> calls UserService.leaveStore(token)
     */
    public Response<Boolean> logout(String token) {
        if (token == null || token.isBlank()) {
            return Response.error("No active session");
        }

        Response<Boolean> response = userService.logout(token);

        // If the service says this is a guest, use leaveStore instead.
        if (response.getValue() == null &&
                "User is in guest state".equals(response.getMessage())) {
            return userService.leaveStore(token);
        }

        return response;
    }
}