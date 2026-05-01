package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import Log.LoggerSetup;
import domain.company.Company;
import domain.dataType.*;
import domain.dto.EventDTO;
import domain.dto.EventDetailsDTO;
import domain.dto.UserDTO;
import domain.event.EventMap;
import domain.event.StandingZone;
import domain.event.Zone;
import domain.user.IUserRepo;
import domain.user.Member;
import infrastructure.*;
import org.junit.jupiter.api.Test;
import domain.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

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
    private Integer activeEvent1Id;
    private Integer inactiveEventId;
    private EventCompanyManageService eventCompanyManageService;

    private ElementPositionDTO stage;
    private List<ElementPositionDTO> entries;
    private List<StandingZoneDTO> standingZones;
    private List<SeatingZoneDTO> seatingZones;
    private String GUEST_TOKEN;
    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
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
        GUEST_TOKEN = userService.continueAsGuest().getValue();
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
                -1
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
                -1
        );

        assertNull(response.getValue());
    }
    //Verifies that viewing event details during concurrent deletion returns either a valid full event snapshot or a  "not found" response,
    @Test
    void GivenConcurrentReadAndDelete_WhenViewEventDetails_ThenReturnsConsistentEventState() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<EventDetailsDTO>> detailsFuture = executor.submit(() -> {
            start.await();
            return service.ViewEventDetails(validToken, company1, activeEvent1Id);
        });

        Future<Response<Boolean>> deleteFuture = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.DeleteEvent(validToken, activeEvent1Id);
        });

        start.countDown();

        Response<EventDetailsDTO> response = detailsFuture.get();
        deleteFuture.get();

        assertNotNull(response);

        if (response.getValue() == null) {
            assertEquals("The selected event is not active", response.getMessage());
        } else {
            assertEquals(activeEvent1Id, response.getValue().getId());
            assertNotNull(response.getValue().getName());
        }

        executor.shutdown();
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

    //Verifies that concurrent global searches return logically consistent results: all threads should observe only matching category events and identical result sizes
    @Test
    void GivenManyConcurrentSearches_WhenSearchEvents_ThenAllThreadsSeeConsistentResults() throws Exception {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setCategory(CategoryEvent.SPORTS);

        int threads = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Response<List<EventDTO>>>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();

                return service.searchEvents(validToken, filter);
            }));
        }

        ready.await();
        start.countDown();

        int expectedSize = -1;

        for (Future<Response<List<EventDTO>>> future : futures) {
            Response<List<EventDTO>> response = future.get();

            assertNotNull(response);
            assertNotNull(response.getValue());
            assertEquals("Events retrieved successfully", response.getMessage());
            assertTrue(response.getValue().stream()
                    .allMatch(e -> e.getCategoryEvent().equals(CategoryEvent.SPORTS.name())));

            if (expectedSize == -1) {
                expectedSize = response.getValue().size();
            } else {
                assertEquals(expectedSize, response.getValue().size());
            }
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    // Verifies logical consistency when global search and delete run concurrently:
    // returned events must be valid and never partially corrupted.
    @Test
    void GivenConcurrentSearchAndDelete_WhenSearchEvents_ThenReturnedEventsRemainConsistent() throws Exception {
        EventSearchFilter filter = new EventSearchFilter();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<List<EventDTO>>> searchFuture = executor.submit(() -> {
            start.await();
            return service.searchEvents(validToken, filter);
        });

        Future<Response<Boolean>> deleteFuture = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.DeleteEvent(validToken, activeEvent1Id);
        });

        start.countDown();

        Response<List<EventDTO>> response = searchFuture.get();
        deleteFuture.get();

        assertNotNull(response);

        if (response.getValue() != null) {
            response.getValue().forEach(e -> {
                assertNotNull(e.getEventID());
                assertNotNull(e.getName());
            });
        }

        executor.shutdown();
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

    @Test
    void GivenDeleteBetweenTwoSearchCompanyEvents_WhenSearchCompanyEvents_ThenDeletedEventMustDisappear() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("active");

        Response<List<EventDTO>> beforeDelete = service.searchCompanyEvents(validToken, company1, filter);
        assertNotNull(beforeDelete.getValue());
        assertTrue(beforeDelete.getValue().stream().anyMatch(e -> e.getEventID().equals(activeEvent1Id)));

        eventCompanyManageService.DeleteEvent(validToken, activeEvent1Id);

        Response<List<EventDTO>> afterDelete = service.searchCompanyEvents(validToken, company1, filter);

        assertTrue(
                afterDelete.getValue() == null ||
                        afterDelete.getValue().stream().noneMatch(e -> e.getEventID().equals(activeEvent1Id))
        );
    }

    // Verifies logical consistency when search and delete run concurrently:
    // search must return either a valid snapshot containing the event, or a "not found" result after deletion.
    @Test
    void GivenConcurrentSearchAndDelete_WhenSearchCompanyEvents_ThenReturnsConsistentSnapshot() throws Exception {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("active");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<List<EventDTO>>> searchFuture = executor.submit(() -> {
            start.await();
            return service.searchCompanyEvents(validToken, company1, filter);
        });

        Future<Response<Boolean>> deleteFuture = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.DeleteEvent(validToken, activeEvent1Id);
        });

        start.countDown();

        Response<List<EventDTO>> searchResponse = searchFuture.get();
        deleteFuture.get();

        assertNotNull(searchResponse);

        if (searchResponse.getValue() == null) {
            assertEquals("No matching events found in the company", searchResponse.getMessage());
        } else {
            boolean found = searchResponse.getValue().stream()
                    .anyMatch(e -> e.getEventID().equals(activeEvent1Id));

            assertTrue(found);
            assertTrue(searchResponse.getValue().stream()
                    .noneMatch(e -> e.getName().equals("inactive event")));
        }

        executor.shutdown();
    }

    // Verifies that many concurrent company-level searches can run in parallel and all return successful, non-empty, consistent results.
    @Test
    void GivenManyConcurrentSearches_WhenSearchCompanyEvents_ThenAllThreadsReturnValidResults() throws Exception {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("active");

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Response<List<EventDTO>>>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();

                return service.searchCompanyEvents(validToken, company1, filter);
            }));
        }

        ready.await();
        start.countDown();

        for (Future<Response<List<EventDTO>>> future : futures) {
            Response<List<EventDTO>> response = future.get();

            assertNotNull(response);
            assertNotNull(response.getValue());
            assertFalse(response.getValue().isEmpty());
            assertEquals("Events retrieved successfully", response.getMessage());
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
    @Test
    void GivenGuestToken_WhenSearchEvents_ThenMatchingEventsReturned() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("active");

        Response<List<EventDTO>> response = service.searchEvents(GUEST_TOKEN, filter);

        assertNotNull(response.getValue());
        assertFalse(response.getValue().isEmpty());
        assertEquals("Events retrieved successfully", response.getMessage());
    }

}