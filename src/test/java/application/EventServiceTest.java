package application;

import infrastructure.LotteryRepoImpl;
import org.junit.jupiter.api.Test;
import domain.company.Company;
import domain.event.Event;
import infrastructure.CompanyRepoImpl;
import infrastructure.EventRepoImpl;
import org.junit.jupiter.api.BeforeEach;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EventServiceTest {
    private final int company1 = 1;
    private final int company2 = 2;
    private final int userId = 123;

    private TokenService tokenService;
    private EventRepoImpl eventRepo;
    private EventService service;

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
                true
        );
        activeEvent.setActive(true);
        eventRepo.store(activeEvent);

        // Inactive event (company1)
        inactiveEvent = new Event(
                company1,
                userId,
                LocalDateTime.now().plusDays(10),
                "inactive event",
                LocalDateTime.now().plusDays(5),
                false
        );
        eventRepo.store(inactiveEvent);

        // Event for company2
        eventCompany2 = new Event(
                company2,
                234,
                LocalDateTime.now().plusDays(10),
                "other company event",
                LocalDateTime.now().plusDays(5),
                true
        );
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
}