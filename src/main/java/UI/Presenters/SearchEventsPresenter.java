package UI.Presenters;

import application.EventService;
import application.Response;
import domain.dataType.EventSearchFilter;
import domain.dto.EventDTO;

import java.util.List;

public class SearchEventsPresenter {

    private final EventService eventService;

    public SearchEventsPresenter(EventService eventService) {
        this.eventService = eventService;
    }

    public Response<List<EventDTO>> search(String token, EventSearchFilter filter) {
        return eventService.searchEvents(token, filter);
    }
}