package UI.Presenters;

import application.EventService;
import application.LotteryService;
import application.Response;
import domain.dto.EventDetailsDTO;

public class EventDetailsPresenter {

    private final EventService eventService;
    private final LotteryService lotteryService;

    public EventDetailsPresenter(
            EventService eventService,
            LotteryService lotteryService
    ) {
        this.eventService = eventService;
        this.lotteryService = lotteryService;
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
}