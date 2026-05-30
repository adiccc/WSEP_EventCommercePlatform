package UI.Presenters;

import application.EventService;
import application.LotteryService;
import application.Response;
import application.IAuth;
import domain.dto.EventDetailsDTO;

public class EventDetailsPresenter {

    private final EventService eventService;
    private final LotteryService lotteryService;
    private final IAuth auth;


    public EventDetailsPresenter(
            EventService eventService,
            LotteryService lotteryService,
            IAuth auth
    ) {
        this.eventService = eventService;
        this.lotteryService = lotteryService;
        this.auth = auth;
    }

    public Response<EventDetailsDTO> getDetails(
            String token,
            int companyId,
            int eventId
    ) {
        return eventService.ViewEventDetails(
                token,
                companyId,
                eventId
        );
    }

    public Response<Boolean> registerUserToLottery(
            String token,
            int eventId
    ) {
        return lotteryService.registerUserToLottery(
                token,
                eventId
        );
    }

    public Response<Boolean> canRegisterToLottery(
            String token,
            int eventId
    ) {
        return lotteryService.canRegisterToLottery(
                token,
                eventId
        );
    }

    public Response<String> getRole(String token) {
        return auth.getRole(token);
    }
}