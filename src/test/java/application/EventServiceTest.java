package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.company.Company;
import domain.dataType.*;
import domain.dto.EventDTO;
import domain.dto.EventDetailsDTO;
import domain.dto.UserDTO;
import domain.event.EventMap;
import domain.user.IUserRepo;
import domain.user.Member;
import infrastructure.*;
import org.junit.jupiter.api.Test;
import domain.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class EventServiceTest {
    private final int company1 = 1;
    private final int company2 = 2;

    private IAuth auth;
    private  TokenService tokenService;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private EventRepoImpl eventRepo;
    private EventService service;

    private String validToken;
    private String activeEvent1Id;
    private String inactiveEventId;
    private EventCompanyManageService eventCompanyManageService;

    private ElementPositionDTO stage;
    private List<ElementPositionDTO> entries;
    private List<StandingZoneDTO> standingZones;
    private List<SeatingZoneDTO> seatingZones;

    @BeforeEach
    void setUp() {
        eventRepo = new EventRepoImpl();
        tokenService = new TokenService();
        userRepo = new UserRepo();
        passwordEncoder = new PasswordEncoderUtil();
        auth = new Auth(tokenService,userRepo,passwordEncoder);
        CompanyRepoImpl companyRepo = new CompanyRepoImpl();
        IPaymentSystem paymentSystem = Mockito.mock(IPaymentSystem.class);
        eventCompanyManageService = new EventCompanyManageService(companyRepo, eventRepo, auth, paymentSystem);
        service = new EventService(auth, eventRepo);

        UserService userService=new UserService(tokenService,auth,userRepo,passwordEncoder);
        UserDTO userDTO = new UserDTO("user1@test.com","test1","t","mytest",1,1,2016,"user test address","054-555-6677");
        userService.registerUser(validToken,userDTO);
        validToken=userService.login("user1@test.com","mytest").getValue();

        CompanyService companyService=new CompanyService(auth,companyRepo,userRepo);
        Response<Company> c1=companyService.createProductionCompany(validToken,company1,"test-company","testC@company.com","054-5556677","leumi");

        // Active event (company1)
        activeEvent1Id = eventCompanyManageService.createEvent(validToken, company1 ,LocalDateTime.now().plusDays(10),"active event",LocalDateTime.now().plusDays(5),false, GeographicalArea.JERUSALEM, CategoryEvent.SPORTS).getValue();
        stage = new ElementPositionDTO(10, 20);
        entries = List.of(new ElementPositionDTO(0, 0), new ElementPositionDTO(50, 10));
        standingZones = List.of(new StandingZoneDTO(50, "VIP", 300.0, new ElementPositionDTO(1, 1)));
        seatingZones = List.of(new SeatingZoneDTO(10, 20, "Regular", 100.0, new ElementPositionDTO(5, 5)));
        eventCompanyManageService.DefineVenueAndSeatingMap(validToken, activeEvent1Id, stage, entries, standingZones, seatingZones);

        // Inactive event (company1) - since we havent define map for this event, it will be inactive
        inactiveEventId = eventCompanyManageService.createEvent(validToken, company1 ,LocalDateTime.now().plusDays(10),"inactive event",LocalDateTime.now().plusDays(5),false, GeographicalArea.SOUTH, CategoryEvent.CONFERENCE).getValue();
       }

    @Test
    void GivenValidEvent_WhenViewEventDetails_ThenEventDetailsAreReturned() {
        Response<EventDetailsDTO> response = service.ViewEventDetails(
                validToken,
                company1,
                activeEvent1Id
        );

        assertNotNull(response.getValue());
        assertEquals(activeEvent1Id, response.getValue().getId());
        assertEquals("Event details retrieved successfully", response.getMessage());
    }

    @Test
    void GivenNonExistingEvent_WhenViewEventDetails_ThenEventNotFoundErrorIsReturned() {
        Response<EventDetailsDTO> response = service.ViewEventDetails(
                validToken,
                company1,
                "invalid-id"
        );

        assertNull(response.getValue());
        assertEquals("Event not found", response.getMessage());
    }

    @Test
    void GivenEventFromDifferentCompany_WhenViewEventDetails_ThenCompanyMismatchErrorIsReturned() {

        Response<EventDetailsDTO> response = service.ViewEventDetails(
                validToken,
                company2,
                activeEvent1Id
        );

        assertNull(response.getValue());
        assertEquals("The selected event does not belong to the company", response.getMessage());
    }


    @Test
    void GivenInactiveEvent_WhenViewEventDetails_ThenInactiveEventErrorIsReturned() {
        Response<EventDetailsDTO> response = service.ViewEventDetails(
                validToken,
                company1,
                inactiveEventId
        );

        assertNull(response.getValue());
        assertEquals("The selected event is not active", response.getMessage());
    }

    @Test
    void GivenInvalidToken_WhenViewEventDetails_ThenInvalidTokenErrorIsReturned() {
        Response<EventDetailsDTO> response = service.ViewEventDetails(
                "",
                company1,
                activeEvent1Id
        );

        assertNull(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenNullEventId_WhenViewEventDetails_ThenErrorIsReturned() {
        Response<EventDetailsDTO> response = service.ViewEventDetails(
                validToken,
                company1,
                null
        );

        assertNull(response.getValue());
    }

    @Test
    void GivenEmptyEventId_WhenViewEventDetails_ThenErrorIsReturned() {
        Response<EventDetailsDTO> response = service.ViewEventDetails(
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

        Response<List<EventDTO>> response = service.searchEvents(validToken, filter);

        assertNotNull(response.getValue());
        assertFalse(response.getValue().isEmpty());
        assertEquals("Events retrieved successfully", response.getMessage());
    }

    @Test
    void GivenCancelledEvent_WhenSearchEvents_ThenNoResultsMessageReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("active");
        eventCompanyManageService.DeleteEvent(validToken, activeEvent1Id);

        Response<List<EventDTO>> response = service.searchEvents(validToken, filter);

        assertNull(response.getValue());
        assertEquals("No matching events found", response.getMessage());
    }



    @Test
    void GivenNoMatchingEvents_WhenSearchEvents_ThenNoResultsMessageReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("non-existing-event");

        Response<List<EventDTO>> response = service.searchEvents(validToken, filter);

        assertNull(response.getValue());
        assertEquals("No matching events found", response.getMessage());
    }

    @Test
    void GivenCategoryFilter_WhenSearchEvents_ThenOnlyMatchingCategoryReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setCategory(CategoryEvent.SPORTS);

        Response<List<EventDTO>> response = service.searchEvents(validToken, filter);

        assertNotNull(response.getValue());
        assertTrue(
                response.getValue().stream()
                        .allMatch(e -> e.getCategoryEvent().equals(CategoryEvent.SPORTS.name()))
        );
    }

    @Test
    void GivenLocationFilter_WhenSearchEvents_ThenOnlyMatchingLocationReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setLocation(GeographicalArea.JERUSALEM);

        Response<List<EventDTO>> response = service.searchEvents(validToken, filter);

        assertNotNull(response.getValue());
        assertTrue(
                response.getValue().stream()
                        .allMatch(e -> e.getLocation().equals(GeographicalArea.JERUSALEM.name()))
        );
    }

    @Test
    void GivenNullFilter_WhenSearchEvents_ThenInvalidInputReturned() {
        Response<List<EventDTO>> response = service.searchEvents(validToken, null);

        assertNull(response.getValue());
        assertEquals("Invalid search input", response.getMessage());
    }

    @Test
    void GivenInvalidToken_WhenSearchEvents_ThenInvalidTokenErrorReturned() {
        EventSearchFilter filter = new EventSearchFilter();

        Response<List<EventDTO>> response = service.searchEvents("mnhvfd", filter);

        assertNull(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenNoCompanyData_WhenSearchEvents_ThenNoResultsReturned() {
        EventRepoImpl emptyRepo = new EventRepoImpl();
        EventService emptyService = new EventService(auth, emptyRepo);

        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("anything");

        Response<List<EventDTO>> response = emptyService.searchEvents(validToken, filter);

        assertNull(response.getValue());
        assertEquals("No matching events found", response.getMessage());
    }

    @Test
    void GivenMultipleFilters_WhenSearchEvents_ThenOnlyMatchingEventsReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("active");
        filter.setCategory(CategoryEvent.SPORTS);
        filter.setLocation(GeographicalArea.JERUSALEM);
        filter.setMinPrice(200.0);

        Response<List<EventDTO>> response = service.searchEvents(validToken, filter);

        assertNotNull(response.getValue());
        assertTrue(response.getValue().stream().allMatch(e ->
                e.getName().contains("active") &&
                        e.getCategoryEvent().equals(CategoryEvent.SPORTS.name()) &&
                        e.getLocation().equals(GeographicalArea.JERUSALEM.name())
        ));
    }

    @Test
    void GivenInactiveEvents_WhenSearchEvents_ThenTheyAreFilteredOut() {
        EventSearchFilter filter = new EventSearchFilter();

        Response<List<EventDTO>> response = service.searchEvents(validToken, filter);

        assertTrue(response.getValue().stream()
                .noneMatch(e -> e.getName().equals("inactive event")));
        assertTrue(response.getValue().stream().anyMatch(e -> e.getName().equals("active event")));
    }

    @Test
    void GivenPriceExactlyOnBoundary_WhenSearchEvents_ThenEventIncluded() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setMinPrice(300.0); // VIP

        Response<List<EventDTO>> response = service.searchEvents(validToken, filter);

        assertNotNull(response.getValue());
    }

    @Test
    void GivenUpperCaseKeyword_WhenSearchEvents_ThenStillMatches() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("TIVE");

        Response<List<EventDTO>> response = service.searchEvents(validToken, filter);

        assertNotNull(response.getValue());
    }

    @Test
    void GivenInvalidDateRange_WhenSearchEvents_ThenNoResultsReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setStartDate(LocalDateTime.now().plusDays(10));
        filter.setEndDate(LocalDateTime.now().plusDays(5));

        Response<List<EventDTO>> response = service.searchEvents(validToken, filter);

        assertNull(response.getValue());
    }
    @Test
    void GivenValidCompanyAndKeyword_WhenSearchCompanyEvents_ThenMatchingEventsReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("active");

        Response<List<EventDTO>> response = service.searchCompanyEvents(validToken, company1, filter);

        assertNotNull(response.getValue());
        assertFalse(response.getValue().isEmpty());
        assertEquals("Events retrieved successfully", response.getMessage());
        assertTrue(response.getValue().stream().allMatch(e -> e.getName().contains("active")));
    }

    @Test
    void GivenInvalidToken_WhenSearchCompanyEvents_ThenInvalidTokenReturned() {
        EventSearchFilter filter = new EventSearchFilter();

        Response<List<EventDTO>> response = service.searchCompanyEvents("invalid-token", company1, filter);

        assertNull(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenNullFilter_WhenSearchCompanyEvents_ThenInvalidInputReturned() {
        Response<List<EventDTO>> response = service.searchCompanyEvents(validToken, company1, null);

        assertNull(response.getValue());
        assertEquals("Invalid search input", response.getMessage());
    }

    @Test
    void GivenNoMatchingKeyword_WhenSearchCompanyEvents_ThenNoResultsReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("does-not-exist");

        Response<List<EventDTO>> response = service.searchCompanyEvents(validToken, company1, filter);

        assertNull(response.getValue());
        assertEquals("No matching events found in the company", response.getMessage());
    }

    @Test
    void GivenDifferentCompany_WhenSearchCompanyEvents_ThenNoResultsReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("active");

        Response<List<EventDTO>> response = service.searchCompanyEvents(validToken, company2, filter);

        assertNull(response.getValue());
        assertEquals("No matching events found in the company", response.getMessage());
    }

    @Test
    void GivenPriceRange_WhenSearchCompanyEvents_ThenOnlyMatchingEventsReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setMinPrice(250.0);
        filter.setMaxPrice(350.0);

        Response<List<EventDTO>> response = service.searchCompanyEvents(validToken, company1, filter);

        assertNotNull(response.getValue());
        assertEquals("Events retrieved successfully", response.getMessage());
    }

    @Test
    void GivenInactiveEventInCompany_WhenSearchCompanyEvents_ThenInactiveFilteredOut() {
        EventSearchFilter filter = new EventSearchFilter();

        Response<List<EventDTO>> response = service.searchCompanyEvents(validToken, company1, filter);

        assertNotNull(response.getValue());
        assertTrue(response.getValue().stream()
                .noneMatch(e -> e.getName().equals("inactive event")));
        assertTrue(response.getValue().stream()
                .anyMatch(e -> e.getName().equals("active event")));
    }

    @Test
    void GivenMultipleFilters_WhenSearchCompanyEvents_ThenOnlyMatchingEventsReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("active");
        filter.setCategory(CategoryEvent.SPORTS);
        filter.setLocation(GeographicalArea.JERUSALEM);
        filter.setMinPrice(200.0);

        Response<List<EventDTO>> response = service.searchCompanyEvents(validToken, company1, filter);

        assertNotNull(response.getValue());
        assertTrue(response.getValue().stream().allMatch(e ->
                e.getName().contains("active") &&
                        e.getCategoryEvent().equals(CategoryEvent.SPORTS.name()) &&
                        e.getLocation().equals(GeographicalArea.JERUSALEM.name())
        ));
    }

    @Test
    void GivenUpperCaseKeyword_WhenSearchCompanyEvents_ThenStillMatches() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("TIVE");

        Response<List<EventDTO>> response = service.searchCompanyEvents(validToken, company1, filter);

        assertNotNull(response.getValue());
        assertFalse(response.getValue().isEmpty());
    }
}