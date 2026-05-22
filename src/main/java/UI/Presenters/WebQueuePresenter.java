package UI.Presenters;

import DTO.QueueEntryResultDTO;
import application.Response;
import application.UserService;

public class WebQueuePresenter {

    private final UserService userService;

    public WebQueuePresenter(UserService userService) {
        this.userService = userService;
    }

    public Response<QueueEntryResultDTO> enterQueue() {
        return userService.enter();
    }

    public Response<QueueEntryResultDTO> getQueueStatus(String queueToken) {
        if (queueToken == null || queueToken.isBlank()) {
            return Response.error("Invalid queue token");
        }

        return userService.getQueueStatus(queueToken);
    }
}