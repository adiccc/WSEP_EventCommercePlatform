package application;

import domain.dataType.*;
import domain.event.EventMap;
import org.junit.jupiter.api.Test;
import domain.event.Event;
import infrastructure.EventRepoImpl;
import org.junit.jupiter.api.BeforeEach;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventServiceTest {
    private final int company1 = 1;
    private final int company2 = 2;
    private final int userId = 123;

    private TokenService tokenService;
    private EventRepoImpl eventRepo;
    private EventService service;
    private EventSearchFilter filter;

    private String validToken;
    private Event activeEvent;
    private Event inactiveEvent;
    private Event eventCompany2;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        validToken = tokenService.generateToken("user");
        eventRepo = new EventRepoImpl();


        // Active event (company1)
        activeEvent = new Event(
                company1,
                456,
                LocalDateTime.now().plusDays(10),
                "active event",
                LocalDateTime.now().plusDays(5),
                true,
                GeographicalArea.JERUSALEM,
                CategoryEvent.LiveMusic
        );
        Zone expensive = new StandingZone("VIP", 300.0, 50, new ElementPosition(1,1));
        Zone cheap = new StandingZone("Regular", 100.0, 23, new ElementPosition(2,2));

        EventMap map = new EventMap(
                new ElementPosition(0,0),
                List.of(new ElementPosition(5,5)),
                List.of(expensive, cheap)
        );

        activeEvent.setMap(map);
        activeEvent.setActive(true);
        eventRepo.store(activeEvent);

        // Inactive event (company1)
        inactiveEvent = new Event(
                company1,
                userId,
                LocalDateTime.now().plusDays(10),
                "inactive event",
                LocalDateTime.now().plusDays(5),
                false,
                GeographicalArea.SOUTH,
                CategoryEvent.CONFERENCE
        );
        eventRepo.store(inactiveEvent);

        // Event for company2
        eventCompany2 = new Event(
                company2,
                234,
                LocalDateTime.now().plusDays(10),
                "other company event",
                LocalDateTime.now().plusDays(5),
                true,
                GeographicalArea.NORTH,
                CategoryEvent.FESTIVAL
        );

        eventCompany2.setMap(map);
        eventCompany2.setActive(true);
        eventRepo.store(eventCompany2);

        service = new EventService(tokenService, eventRepo);
    }

    @Test
    void GivenValidEvent_WhenViewEventDetails_ThenEventDetailsAreReturned() {
        Response<Event> response = service.ViewEventDetails(
                validToken,
                company1,
                activeEvent.getId()
        );

        assertNotNull(response.getValue());
        assertEquals(activeEvent.getId(), response.getValue().getId());
        assertEquals("Event details retrieved successfully", response.getMessage());
    }

    @Test
    void GivenNonExistingEvent_WhenViewEventDetails_ThenEventNotFoundErrorIsReturned() {
        Response<Event> response = service.ViewEventDetails(
                validToken,
                company1,
                "invalid-id"
        );

        assertNull(response.getValue());
        assertEquals("Event not found", response.getMessage());
    }

    @Test
    void GivenEventFromDifferentCompany_WhenViewEventDetails_ThenCompanyMismatchErrorIsReturned() {

        Response<Event> response = service.ViewEventDetails(
                validToken,
                company1,
                eventCompany2.getId()
        );

        assertNull(response.getValue());
        assertEquals("The selected event does not belong to the company", response.getMessage());
    }


    @Test
    void GivenInactiveEvent_WhenViewEventDetails_ThenInactiveEventErrorIsReturned() {
        Response<Event> response = service.ViewEventDetails(
                validToken,
                company1,
                inactiveEvent.getId()
        );

        assertNull(response.getValue());
        assertEquals("The selected event is not active", response.getMessage());
    }

    @Test
    void GivenInvalidToken_WhenViewEventDetails_ThenInvalidTokenErrorIsReturned() {
        Response<Event> response = service.ViewEventDetails(
                "",
                company1,
                activeEvent.getId()
        );

        assertNull(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenNullEventId_WhenViewEventDetails_ThenErrorIsReturned() {
        Response<Event> response = service.ViewEventDetails(
                validToken,
                company1,
                null
        );

        assertNull(response.getValue());
    }

    @Test
    void GivenEmptyEventId_WhenViewEventDetails_ThenErrorIsReturned() {
        Response<Event> response = service.ViewEventDetails(
                validToken,
                company1,
                ""
        );

        assertNull(response.getValue());
    }

    @Test
    void GivenKeywordSearch_WhenSearchEvents_ThenMatchingEventsReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("active");

        Response<List<Event>> response = service.searchEvents(validToken, filter);

        assertNotNull(response.getValue());
        assertFalse(response.getValue().isEmpty());
        assertEquals("Events retrieved successfully", response.getMessage());
    }

    @Test
    void GivenNoMatchingEvents_WhenSearchEvents_ThenNoResultsMessageReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("non-existing-event");

        Response<List<Event>> response = service.searchEvents(validToken, filter);

        assertNull(response.getValue());
        assertEquals("No matching events found", response.getMessage());
    }

//        @Test
//    void GivenPriceFilter_WhenSearchEvents_ThenFilterWorksCorrectly() {
//
//        EventSearchFilter filter = new EventSearchFilter();
//        filter.setMinPrice(150.0);
//
//        Response<List<Event>> response = service.searchEvents(validToken, filter);
//
//        assertNotNull(response.getValue());
//        assertTrue(response.getValue().stream()
//                .allMatch(e -> {
//                    double minPrice = e.getMap().getZones().stream()
//                            .mapToDouble(Zone::getPrice)
//                            .min().orElse(0);
//                    return minPrice >= 150.0;
//                }));
//    }

    @Test
    void GivenCategoryFilter_WhenSearchEvents_ThenOnlyMatchingCategoryReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setCategory(CategoryEvent.LiveMusic);

        Response<List<Event>> response = service.searchEvents(validToken, filter);

        assertNotNull(response.getValue());
        assertTrue(
                response.getValue().stream()
                        .allMatch(e -> e.getCategoryEvent() == CategoryEvent.LiveMusic)
        );
    }

    @Test
    void GivenLocationFilter_WhenSearchEvents_ThenOnlyMatchingLocationReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setLocation(GeographicalArea.JERUSALEM);

        Response<List<Event>> response = service.searchEvents(validToken, filter);

        assertNotNull(response.getValue());
        assertTrue(
                response.getValue().stream()
                        .allMatch(e -> e.getLocation() == GeographicalArea.JERUSALEM)
        );
    }

    @Test
    void GivenNullFilter_WhenSearchEvents_ThenInvalidInputReturned() {
        Response<List<Event>> response = service.searchEvents(validToken, null);

        assertNull(response.getValue());
        assertEquals("Invalid search input", response.getMessage());
    }

    @Test
    void GivenInvalidToken_WhenSearchEvents_ThenInvalidTokenErrorReturned() {
        EventSearchFilter filter = new EventSearchFilter();

        Response<List<Event>> response = service.searchEvents("", filter);

        assertNull(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenEmptyRepository_WhenSearchEvents_ThenNoResultsReturned() {
        EventRepoImpl emptyRepo = new EventRepoImpl();
        EventService emptyService = new EventService(tokenService, emptyRepo);

        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("anything");

        Response<List<Event>> response = emptyService.searchEvents(validToken, filter);

        assertNull(response.getValue());
        assertEquals("No matching events found", response.getMessage());
    }
}