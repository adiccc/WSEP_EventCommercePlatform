package UI.Presenters;

import application.EventService;
import application.Response;
import domain.dto.EventDetailsDTO;

public class EventDetailsPresenter {

    private final EventService eventService;

    public EventDetailsPresenter(EventService eventService) {
        this.eventService = eventService;
    }

    public Response<EventDetailsDTO> getDetails(
            String token,
            int companyId,
            int eventId) {
        return eventService.ViewEventDetails(
                token,
                companyId,
                eventId
        );
    }
}