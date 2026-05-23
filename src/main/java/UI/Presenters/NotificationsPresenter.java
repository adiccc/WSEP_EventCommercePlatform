package UI.Presenters;

import DTO.NotifyDTO;
import application.Response;
import application.UserService;

import java.util.Collections;
import java.util.List;

public class NotificationsPresenter {

    private final UserService userService;

    public NotificationsPresenter(UserService userService) {
        this.userService = userService;
    }

    public List<NotifyDTO> getDelayedNotifications(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return Collections.emptyList();
        }

        Response<List<NotifyDTO>> response =
                userService.getDelayedNotifications(userEmail);

        if (response == null || response.getValue() == null) {
            return Collections.emptyList();
        }

        return response.getValue();
    }

    public Response<Boolean> cleanDelayedNotifications(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return Response.error("Invalid email address");
        }

        return userService.cleanDelayedNotifications(userEmail);
    }
}