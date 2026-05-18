package UI.Presenters;

import application.EventService;
import application.Response;
import domain.dto.EventDetailsDTO;

/**
 * Presenter for EventView.
 * Holds EventService only — returns data, never touches UI.
 */
public class EventPresenter {

    private final EventService eventService;

    public EventPresenter(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Returns full event details for the given company + event IDs.
     * Returns null value on failure — check getMessage() for the reason.
     */
    public Response<EventDetailsDTO> getEvent(String token, int companyId, int eventId) {
        return eventService.ViewEventDetails(token != null ? token : "", companyId, eventId);
    }
}
