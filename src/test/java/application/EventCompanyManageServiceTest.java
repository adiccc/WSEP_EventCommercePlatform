package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.company.Company;
import domain.company.ContactInfo;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dto.CompanyDetailsDTO;
import domain.dto.EventDetailsDTO;
import domain.dto.OrderDTO;
import domain.dto.SalesReportDTO;
import domain.dto.UserDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.event.OrderStatus;
import domain.event.IOrderRepo;
import domain.event.Order;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import domain.user.IUserRepo;
import domain.user.Member;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class EventCompanyManageServiceTest {

    private final int companyId = 900;

    private TokenService tokenService;
    private CompanyRepoImpl companyRepo;
    private ElementPositionDTO stage;
    private List<ElementPositionDTO> entries;
    private List<StandingZoneDTO> standingZones;
    private List<SeatingZoneDTO> seatingZones;
    private String validToken1;
    private IAuth auth;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private IEventRepo eventRepo;

    private LocalDateTime eventDate;
    private String eventId;
    private String pastEventId;


    private UserService userService;
    private EventCompanyManageService eventCompanyManageService;
    private CompanyService companyService;
    private String validToken2;
    private String invalidToken;
    private EventService eventService;
    private IPaymentSystem paymentSystem;

    @BeforeEach
    void setUp() {
        userRepo=new UserRepo();
        passwordEncoder=new PasswordEncoderUtil();
        tokenService = new TokenService();
        auth=new Auth(tokenService,userRepo,passwordEncoder);
        companyRepo=new CompanyRepoImpl();
        eventRepo = new EventRepoImpl();

        paymentSystem = Mockito.mock(IPaymentSystem.class);

        userService=new UserService(tokenService,auth,userRepo,passwordEncoder);
        eventService=new EventService(auth,eventRepo);

        //should delete oreder repo from company service construture
        companyService=new CompanyService(auth,companyRepo,userRepo);
        eventCompanyManageService = new EventCompanyManageService(
                companyRepo,
                eventRepo,
                auth,
                paymentSystem
        );

        validToken1=null; // user with all permissions
        UserDTO user1DTO = new UserDTO("user1@test.com","test1","t","mytest",1,1,2016,"user test address","054-555-6677");
        userService.registerUser(validToken1,user1DTO);
        validToken1=userService.login("user1@test.com","mytest").getValue();
        String creatorId=validToken1;

        validToken2=null; //user without permissions
        UserDTO user2DTO = new UserDTO("user2@test.com","test2","t","mytest",1,1,2016,"user test address","054-555-6677");
        userService.registerUser(validToken2,user2DTO);
        validToken2=userService.login("user2@test.com","mytest").getValue();

        invalidToken=null; // loged out user
        UserDTO user3DTO = new UserDTO("user3@test.com","test3","t","mytest",1,1,2016,"user test address","054-555-6677");
        userService.registerUser(invalidToken ,user3DTO);

        Response<Company> c=companyService.createProductionCompany(validToken1,companyId,"test-company","testC@company.com","054-5556677","leumi");

        eventId=eventCompanyManageService.createEvent(validToken1,companyId,LocalDateTime.now().plusDays(10),"test-event",LocalDateTime.now().plusDays(5),false, GeographicalArea.CENTER, CategoryEvent.FESTIVAL).getValue();
        eventDate=LocalDateTime.now().plusDays(10);
        stage = new ElementPositionDTO(10, 20);
        entries = List.of(new ElementPositionDTO(0, 0), new ElementPositionDTO(50, 10));
        standingZones = List.of(new StandingZoneDTO(200, "floor", 100.0, new ElementPositionDTO(1, 1)));
        seatingZones = List.of(new SeatingZoneDTO(10, 20, "tribune", 150.0, new ElementPositionDTO(5, 5)));

    }

    @Test
    void GivenValidAreaSetupScenario_WhenDefineVenueAndSeatingMap_ThenHallIsCreatedAndAssignedToEvent() throws Exception {

        Response<Boolean> response =eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        assertEquals("map saved successfully", response.getMessage());
        assertTrue(response.getValue());
        EventDetailsDTO event=eventService.ViewEventDetails(validToken1,companyId,eventId).getValue();
        assertNotNull(event);
        assertNotNull(event.getMap());
    }

    @Test
    void GivenUnauthorizedUserScenario_WhenDefineVenueAndSeatingMap_ThenPermissionErrorIsShown() throws Exception {
        Response<Boolean> response =eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken2,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        assertFalse(response.getValue());
        assertEquals("Permission required", response.getMessage());
        EventDetailsDTO event=eventService.ViewEventDetails(validToken2,companyId,eventId).getValue();
        assertNull(event);
    }

    @Test
    void GivenLoggedOutUserScenario_WhenDefineVenueAndSeatingMap_ThenInvalidTokenErrorIsShown() {

        Response<Boolean> response =eventCompanyManageService.DefineVenueAndSeatingMap(
                null,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        assertFalse(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenMissingEventScenario_WhenDefineVenueAndSeatingMap_ThenEventNotFoundErrorIsShown() {
        Response<Boolean> response =eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                "non-existing-event-id",
                stage,
                entries,
                standingZones,
                seatingZones
        );

        assertFalse(response.getValue());
        assertEquals("Event not found", response.getMessage());
    }

    @Test
    void GivenWrongMandatoryFieldsScenario_WhenDefineVenueAndSeatingMap_ThenValidationErrorIsShown() throws Exception {

        Response<Boolean> response =eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                null,
                entries,
                standingZones,
                seatingZones
        );

        assertFalse(response.getValue());
        assertEquals("map element null", response.getMessage());
    }

    @Test
    void GivenValidInput_WhenCreateEvent_ThenEventIsSuccessfullyStored() {
        // Arrange: Event date in the future, sale start date in the future (but before the event)
        LocalDateTime eventDate = LocalDateTime.now().plusDays(30);
        LocalDateTime saleStartDate = LocalDateTime.now().plusDays(1);

        // Act: Standard sale (hasLottery = false)
        Response<String> response =eventCompanyManageService.createEvent(
                validToken1, companyId, eventDate, "Standard Event", saleStartDate, false,GeographicalArea.CENTER, CategoryEvent.FESTIVAL
        );

        // Assert result
        assertNotNull(response.getValue());
        assertEquals("Event created successfully", response.getMessage());
    }

    @Test
    void GivenLotteryOptionSelected_WhenCreateEvent_ThenEventWithLotteryIsCreated() {
        // Arrange: Event date in the future, sale start date in the future (but before the event)
        LocalDateTime eventDate = LocalDateTime.now().plusDays(30);
        LocalDateTime saleStartDate = LocalDateTime.now().plusDays(1);

        // Act: Lottery sale (hasLottery = true)
        Response<String> response =eventCompanyManageService.createEvent(
                validToken1, companyId, eventDate, "Lottery Event", saleStartDate, false, GeographicalArea.CENTER, CategoryEvent.FESTIVAL
        );

        // Assert result
        assertNotNull(response.getValue());
        assertEquals("Event created successfully", response.getMessage());
    }

    @Test
    void GivenUnauthorizedUser_WhenCreateEvent_ThenPermissionErrorIsReturned() {
        // Arrange: Setup dates and an unauthorized user ID
        LocalDateTime eventDate = LocalDateTime.now().plusDays(30);
        LocalDateTime saleStartDate = LocalDateTime.now().plusDays(1);

        // Act
        Response<String> response =eventCompanyManageService.createEvent(
                validToken2, companyId, eventDate, "Unauthorized Event", saleStartDate, false,GeographicalArea.CENTER, CategoryEvent.FESTIVAL
        );

        // Assert: System should reject the request due to lack of permissions
        assertNull(response.getValue());
        assertEquals("Permission required", response.getMessage());
    }

    @Test
    void GivenPastEventDate_WhenCreateEvent_ThenDateValidationErrorIsReturned() {
        // Arrange: Event date is one hour before the current time
        LocalDateTime pastEventDate = LocalDateTime.now().minusHours(1);
        LocalDateTime saleStartDate = pastEventDate.minusDays(1);

        // Act
        Response<String> response =eventCompanyManageService.createEvent(
                validToken1, companyId, pastEventDate, "Past Event", saleStartDate, false,GeographicalArea.CENTER, CategoryEvent.FESTIVAL
        );

        // Assert: System identifies that the date is invalid
        assertNull(response.getValue());
        assertEquals("Event date must be in the future", response.getMessage());
    }

    @Test
    void GivenInvalidOrMissingToken_WhenCreateEvent_ThenInvalidTokenErrorIsReturned() {
        // Arrange: Setup dates and an invalid token
        LocalDateTime eventDate = LocalDateTime.now().plusDays(30);
        LocalDateTime saleStartDate = LocalDateTime.now().plusDays(1);

        // Act
        Response<String> response =eventCompanyManageService.createEvent(
                null, companyId, eventDate, "No Token Event", saleStartDate, false,GeographicalArea.CENTER, CategoryEvent.FESTIVAL
        );

        // Assert: System blocks and alerts about invalid token
        assertNull(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenValidManagerAndFutureDate_WhenUpdateEventDate_ThenEventDateIsUpdatedSuccessfully() {
        // Given
        LocalDateTime originalDate = eventDate;
        LocalDateTime requestedDate = originalDate.plusDays(7);
        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        // When
        Response<Boolean> response =eventCompanyManageService.UpdateEventDate(
                validToken1,
                eventId,
                requestedDate
        );

        // Then
        assertTrue(response.getValue());
        assertEquals("Event updated successfully", response.getMessage());

        EventDetailsDTO updatedEvent = eventService.ViewEventDetails(validToken1,companyId,eventId).getValue();
        assertEquals(requestedDate.toString(), updatedEvent.getDate());
    }


    @Test
    void GivenPastRequestedDate_WhenUpdateEventDate_ThenInvalidNewDateErrorIsReturned() {
        // Given
        LocalDateTime originalDate = eventDate;
        LocalDateTime requestedDate = LocalDateTime.now().minusDays(1);
        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        // When
        Response<Boolean> response =eventCompanyManageService.UpdateEventDate(
                validToken1,
                eventId,
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertEquals("Event date can only be after the original date", response.getMessage());

        EventDetailsDTO updatedEvent = eventService.ViewEventDetails(validToken1,companyId,eventId).getValue();
        assertEquals(
                originalDate.withSecond(0).withNano(0),
                LocalDateTime.parse(updatedEvent.getDate()).withSecond(0).withNano(0)
        );
    }

    @Test
    void GivenEarlierThenOriginalDate_WhenUpdateEventDate_ThenInvalidNewDateErrorIsReturned() {
        // Given
        LocalDateTime originalDate = eventDate;
        LocalDateTime requestedDate = originalDate.minusDays(1);eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        // When
        Response<Boolean> response =eventCompanyManageService.UpdateEventDate(
                validToken1,
                eventId,
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertEquals("Event date can only be after the original date", response.getMessage());

        EventDetailsDTO updatedEvent = eventService.ViewEventDetails(validToken1,companyId,eventId).getValue();
        assertEquals(
                originalDate.withSecond(0).withNano(0),
                LocalDateTime.parse(updatedEvent.getDate()).withSecond(0).withNano(0)
        );
    }

    @Test
    void GivenUnauthorizedUser_WhenUpdateEventDate_ThenPermissionErrorIsReturned() {
        // Given
        LocalDateTime originalDate =eventDate;
        LocalDateTime requestedDate = originalDate.plusDays(10);
        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        // When
        Response<Boolean> response =eventCompanyManageService.UpdateEventDate(
                validToken2,
                eventId,
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertEquals("User id mismatch to the creator of the event", response.getMessage());

        Response<EventDetailsDTO> r= eventService.ViewEventDetails(validToken2,companyId,eventId);
        EventDetailsDTO updatedEvent =r.getValue();
        assertEquals(
                originalDate.withSecond(0).withNano(0),
                LocalDateTime.parse(updatedEvent.getDate()).withSecond(0).withNano(0)
        );
    }

    @Test
    void GivenInvalidToken_WhenUpdateEventDate_ThenInvalidTokenErrorIsReturned() {
        // Given
        LocalDateTime requestedDate = eventDate.plusDays(5);

        // When
        Response<Boolean> response =eventCompanyManageService.UpdateEventDate(
                null,
                eventId,
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenEventDoesNotExist_WhenUpdateEventDate_ThenEventNotFoundErrorIsReturned() {
        // Given
        LocalDateTime requestedDate = LocalDateTime.now().plusDays(10);

        // When
        Response<Boolean> response =eventCompanyManageService.UpdateEventDate(
                validToken1,
                "non-existing-event-id",
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertTrue(response.getMessage().startsWith("failed to create event : "));
    }

    // TODO to implement when add order function in service is exist
//    @Test
//    void GivenCompanyExistsAndUserHasPermissionAndOrdersExist_WhenGetOrdersByCompany_ThenOrdersHistoryIsReturned() {
//        // Given
//        eventCompanyManageService.DefineVenueAndSeatingMap(
//                validToken1,
//                eventId,
//                stage,
//                entries,
//                standingZones,
//                seatingZones
//        );
//        Order order1 = new Order(0, 1, "1", new ArrayList<>() );
//        Order order2 = new Order(1, 1, "1", new ArrayList<>());
//        Event event=eventService.ViewEventDetails(validToken1,companyId,eventId).getValue();
//        event.getOrders().add(order1);
//        event.getOrders().add(order2);
//
//        // When
//        Response<List<OrderDTO>> response =eventCompanyManageService.getOrdersByCompany(validToken1, companyId);
//
//        // Then
//        assertNotNull(response.getValue());
//        assertEquals("orders found", response.getMessage());
//        assertEquals(2, response.getValue().size());
//        assertTrue(response.getValue().contains(order1));
//        assertTrue(response.getValue().contains(order2));
//    }

    @Test
    void GivenUnauthorizedUser_WhenGetOrdersByCompany_ThenPermissionErrorIsReturned() {
        // Given
        // invalidToken2 belongs to user2, who is not the company owner

        // When
        Response<List<OrderDTO>> response =eventCompanyManageService.getOrdersByCompany(validToken2, companyId);

        // Then
        assertNull(response.getValue());
        assertEquals("Permission required", response.getMessage());
    }

    @Test
    void GivenCompanyDoesNotExist_WhenGetOrdersByCompany_ThenCompanyNotFoundErrorIsReturned() {
        // Given
        int nonExistingCompanyId = 999999;

        // When
        Response<List<OrderDTO>> response =eventCompanyManageService.getOrdersByCompany(validToken1, nonExistingCompanyId);

        // Then
        assertNull(response.getValue());
        assertEquals("company not found", response.getMessage());
    }

    @Test
    void GivenLoggedOutUser_WhenGetOrdersByCompany_ThenInvalidTokenErrorIsReturned() {
        // Given
        String loggedOutToken = null;

        // When
        Response<List<OrderDTO>> response =eventCompanyManageService.getOrdersByCompany(loggedOutToken, companyId);

        // Then
        assertNull(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenCompanyExistsAndUserHasPermissionButNoOrdersExist_WhenGetOrdersByCompany_ThenNoPurchaseDataErrorIsReturned() {
        // Given
        // event exists, but no orders were added to it

        // When
        Response<List<OrderDTO>> response =eventCompanyManageService.getOrdersByCompany(validToken1, companyId);

        // Then
        assertNull(response.getValue());
        assertEquals("No orders found for company " + companyId, response.getMessage());
    }

    @Test
    void GivenValidInputs_WhenAddZonesToEventMap_ThenZonesAreAddedSuccessfully() {
        // define a map for the event so it becomes active
        eventCompanyManageService.DefineVenueAndSeatingMap(validToken1, eventId, stage, entries, standingZones, seatingZones);

        // Create new zones to add
        List<StandingZoneDTO> newStandingZones = List.of(new StandingZoneDTO(500, "Golden Ring", 300.0, new ElementPositionDTO(2, 2)));
        List<SeatingZoneDTO> newSeatingZones = List.of(new SeatingZoneDTO(5, 10, "VIP", 500.0, new ElementPositionDTO(10, 10)));

        // Act
        Response<Boolean> response = eventCompanyManageService.AddZonesToEventMap(
                validToken1,
                eventId,
                newStandingZones,
                newSeatingZones
        );

        // Assert
        assertTrue(response.getValue());
        assertEquals("Zones added to event map successfully", response.getMessage());

        // Verify the zones were actually added
        EventDetailsDTO updatedEvent = eventService.ViewEventDetails(validToken1, companyId, eventId).getValue();
        int totalZones = updatedEvent.getMap().getSeatingZones().size() + updatedEvent.getMap().getStandingZones().size();
        assertEquals(4, totalZones); // 2 original + 2 new
    }

    @Test
    void GivenUnauthorizedUser_WhenAddZonesToEventMap_ThenPermissionErrorIsReturned() {
        // Arrange: Define the map with the authorized user
        eventCompanyManageService.DefineVenueAndSeatingMap(validToken1, eventId, stage, entries, standingZones, seatingZones);

        List<StandingZoneDTO> newStandingZones = List.of(new StandingZoneDTO(500, "Golden Ring", 300.0, new ElementPositionDTO(2, 2)));

        // Act: Try to add zones with an unauthorized user (validToken2)
        Response<Boolean> response = eventCompanyManageService.AddZonesToEventMap(
                validToken2,
                eventId,
                newStandingZones,
                null
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("Permission required", response.getMessage());
    }

    @Test
    void GivenLoggedOutUser_WhenAddZonesToEventMap_ThenInvalidTokenErrorIsReturned() {
        // Arrange: Define a valid map using an authorized user
        eventCompanyManageService.DefineVenueAndSeatingMap(validToken1, eventId, stage, entries, standingZones, seatingZones);

        // Prepare the new zones we want to add
        List<StandingZoneDTO> newStandingZones = List.of(new StandingZoneDTO(500, "Golden Ring", 300.0, new ElementPositionDTO(2, 2)));

        // Act: Attempt to add the new zones to the existing map without being logged in (using invalidToken)
        Response<Boolean> response = eventCompanyManageService.AddZonesToEventMap(
                invalidToken,
                eventId,
                newStandingZones,
                null
        );

        // Assert: The system must identify the lack of a valid token and block the action immediately
        assertFalse(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenNonExistingEvent_WhenAddZonesToEventMap_ThenEventNotFoundErrorIsReturned() {
        // Arrange
        List<StandingZoneDTO> newStandingZones = List.of(new StandingZoneDTO(500, "Golden Ring", 300.0, new ElementPositionDTO(2, 2)));

        // Act
        Response<Boolean> response = eventCompanyManageService.AddZonesToEventMap(
                validToken1,
                "non-existing-event-id",
                newStandingZones,
                null
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("Event not found", response.getMessage());
    }

    @Test
    void GivenNoZonesProvided_WhenAddZonesToEventMap_ThenValidationErrorIsReturned() {
        // Arrange: Define the map first
        eventCompanyManageService.DefineVenueAndSeatingMap(validToken1, eventId, stage, entries, standingZones, seatingZones);

        // Act: Pass null for both lists
        Response<Boolean> response = eventCompanyManageService.AddZonesToEventMap(
                validToken1,
                eventId,
                null,
                null
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("No zones provided to add", response.getMessage());
    }

    @Test
    void GivenEventWithoutMap_WhenAddZonesToEventMap_ThenNoMapDefinedErrorIsReturned() {
        // Arrange: Use the event from setUp() that doesn't have a map yet
        List<StandingZoneDTO> newStandingZones = List.of(new StandingZoneDTO(500, "Golden Ring", 300.0, new ElementPositionDTO(2, 2)));

        // Act
        Response<Boolean> response = eventCompanyManageService.AddZonesToEventMap(
                validToken1,
                eventId,
                newStandingZones,
                null
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("Event map not defined yet", response.getMessage());
    }

    @Test
    void GivenInactiveEvent_WhenAddZonesToEventMap_ThenEventNotActiveErrorIsReturned() {
        // Arrange: Create a Lottery Event. A lottery event does not become active immediately when a map is defined.
        String lotteryEventId = eventCompanyManageService.createEvent(
                validToken1, companyId, LocalDateTime.now().plusDays(10), "Lottery Event",
                LocalDateTime.now().plusDays(5), true, GeographicalArea.CENTER, CategoryEvent.FESTIVAL
        ).getValue();

        // Define map for the lottery event
        eventCompanyManageService.DefineVenueAndSeatingMap(validToken1, lotteryEventId, stage, entries, standingZones, seatingZones);

        List<StandingZoneDTO> newStandingZones = List.of(new StandingZoneDTO(500, "Golden Ring", 300.0, new ElementPositionDTO(2, 2)));

        // Act
        Response<Boolean> response = eventCompanyManageService.AddZonesToEventMap(
                validToken1,
                lotteryEventId,
                newStandingZones,
                null
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("Event is not active yet, cannot add zones", response.getMessage());
    }

    @Test
    void GivenGuest_WhenViewActiveCompanyWithEvents_ThenReturnCompanyAndEvents() {
        // Arrange
        eventCompanyManageService.DefineVenueAndSeatingMap(validToken1, eventId, stage, entries, standingZones, seatingZones);

        // Act
        Response<CompanyDetailsDTO> response = eventCompanyManageService.getCompanyDetails(invalidToken, companyId);

        // Assert
        assertNotNull(response.getValue());
        assertEquals("Company details found", response.getMessage());
        assertEquals(companyId, response.getValue().getCompanyId());
        assertEquals(1, response.getValue().getFutureEvents().size());
        assertEquals(eventId, response.getValue().getFutureEvents().get(0).getEventID());
    }

    @Test
    void GivenMember_WhenViewActiveCompanyWithEvents_ThenReturnCompanyAndEvents() {
        // Arrange
        eventCompanyManageService.DefineVenueAndSeatingMap(validToken1, eventId, stage, entries, standingZones, seatingZones);

        // Act
        Response<CompanyDetailsDTO> response = eventCompanyManageService.getCompanyDetails(validToken2, companyId);

        // Assert
        assertNotNull(response.getValue());
        assertEquals("Company details found", response.getMessage());
        assertEquals(1, response.getValue().getFutureEvents().size());
    }

    @Test
    void GivenGuest_WhenViewClosedCompany_ThenErrorNotPermitted() {
        // Arrange
        int closedCompanyId = 901;
        companyService.createProductionCompany(validToken1, closedCompanyId, "Closed Co", "a@b.com", "050-1234567", "bank");
        companyService.deactivateCompany(validToken1, closedCompanyId);

        // Act
        Response<CompanyDetailsDTO> response = eventCompanyManageService.getCompanyDetails(invalidToken, closedCompanyId);

        // Assert
        assertNull(response.getValue());
        assertEquals("User is not permitted to view closed companies", response.getMessage());
    }

    @Test
    void GivenOwner_WhenViewClosedCompany_ThenReturnCompanyDetails() {
        // Arrange
        int closedCompanyId = 902;
        companyService.createProductionCompany(validToken1, closedCompanyId, "Closed Co", "a@b.com", "050-1234567", "bank");
        companyService.deactivateCompany(validToken1, closedCompanyId);

        // Act
        Response<CompanyDetailsDTO> response = eventCompanyManageService.getCompanyDetails(validToken1, closedCompanyId);

        // Assert
        assertNotNull(response.getValue());
        assertEquals(closedCompanyId, response.getValue().getCompanyId());
    }

    @Test
    void GivenCompanyWithNoActiveEvents_WhenGetCompanyDetails_ThenReturnCompanyAndMessage() {

        // Act
        Response<CompanyDetailsDTO> response = eventCompanyManageService.getCompanyDetails(validToken2, companyId);

        // Assert
        assertNotNull(response.getValue());
        assertEquals("No future events found for company " + companyId, response.getMessage());
        assertTrue(response.getValue().getFutureEvents().isEmpty());
    }

    @Test
    void GivenInvalidCompanyId_WhenGetCompanyDetails_ThenErrorNotFound() {
        // Act
        Response<CompanyDetailsDTO> response = eventCompanyManageService.getCompanyDetails(validToken1, 9999);

        // Assert
        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("failed getCompanyDetails"));    }
         // ===================== Generate Sales Reports Tests =====================
    @Test
    void GivenOwnerWithSalesData_WhenGenerateSalesReports_ThenReturnReportWithData() {
        // Arrange
        //TODO: make sure that when order is completed change the change to use only repo's and services!!!!
        String event = eventCompanyManageService.createEvent(validToken1,companyId,eventDate,"event1",eventDate.minusDays(1), false,GeographicalArea.NORTH,CategoryEvent.SPORTS).getValue();
        List<Integer> purchasedTickets = new ArrayList<>();
        purchasedTickets.add(101);
        Event e =eventRepo.findById(event);
        Order order = new Order(1, 1, eventId, purchasedTickets);
        e.getOrders().add(order);
        eventRepo.store(e);
        Response<SalesReportDTO> response = eventCompanyManageService.generateSalesReports(companyId, validToken1);

        assertNotNull(response.getValue());
        assertEquals("Sales Report generated successfully", response.getMessage());
        assertEquals(companyId, response.getValue().getCompanyId());
        assertFalse(response.getValue().getEventRecords().isEmpty());
        assertEquals(1, response.getValue().getEventRecords().size());
        assertTrue(response.getValue().getTotalTicketsSold() > 0);
    }

    @Test
    void GivenCompanyWithNoSales_WhenGenerateSalesReports_ThenReturnEmptyReport() {
        // Arrange

        // Act
        Response<SalesReportDTO> response = eventCompanyManageService.generateSalesReports(companyId, validToken1);

        // Assert
        assertNotNull(response.getValue());
        assertEquals("No future events found for company " + companyId, response.getMessage()); // match the string in the code

        assertTrue(response.getValue().getEventRecords().isEmpty());
        assertEquals(0, response.getValue().getTotalTicketsSold());
        assertEquals(0.0, response.getValue().getTotalRevenue());
    }

    @Test
    void GivenUnauthorizedUser_WhenGenerateSalesReports_ThenErrorNotPermitted() {
        // Act
        Response<SalesReportDTO> response = eventCompanyManageService.generateSalesReports(companyId, validToken2);
        // Assert
        assertNull(response.getValue());
        assertEquals("User is not permitted generate sales report", response.getMessage());
    }

    @Test
    void GivenGuest_WhenGenerateSalesReports_ThenErrorNotPermitted() {
        // Act
        Response<SalesReportDTO> response = eventCompanyManageService.generateSalesReports(companyId, invalidToken);

        // Assert
        assertNull(response.getValue());
        assertEquals("User is not permitted generate sales report", response.getMessage());
    }

    @Test
    void GivenInvalidCompanyId_WhenGenerateSalesReports_ThenErrorNotFound() {
        // Act
        Response<SalesReportDTO> response = eventCompanyManageService.generateSalesReports(9999, validToken1);

        // Assert
        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("not found"));
    }
        @Test
    void SuccessfulRefund() {
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble()))
                .thenReturn(true);

        Event event = eventRepo.findById(eventId);

        Order order = new Order(1, 1, eventId, List.of(1, 2), 100.0, "pay123");
        order.markRefundRequired();
        event.getOrders().add(order);
        eventRepo.store(event);

        Response<Boolean> response = eventCompanyManageService.processRefund(
                validToken1,
                eventId,
                1
        );

        assertTrue(response.getValue());
        assertEquals("Refund completed successfully", response.getMessage());
        Order updatedOrder = eventRepo.findById(eventId).findOrderById(1);
        assertEquals(OrderStatus.REFUNDED, updatedOrder.getStatus());

        Mockito.verify(paymentSystem).refund("pay123", 100.0);
    }

    @Test
    void RefundTransactionNotFound() {
        Response<Boolean> response = eventCompanyManageService.processRefund(
                validToken1,
                eventId,
                999
        );

        assertFalse(response.getValue());
        assertEquals("No matching order found for refund", response.getMessage());

        Mockito.verify(paymentSystem, Mockito.never())
                .refund(Mockito.anyString(), Mockito.anyDouble());
    }

    @Test
    void RefundRejected() {
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble()))
                .thenReturn(false);

        Event event = eventRepo.findById(eventId);

        Order order = new Order(123, 900, eventId, List.of(1, 2), 100.0, "pay123");
        order.markRefundRequired();
        event.getOrders().add(order);
        eventRepo.store(event);

        Response<Boolean> response = eventCompanyManageService.processRefund(
                validToken1,
                eventId,
                123
        );

        assertFalse(response.getValue());
        assertEquals("Refund rejected by external payment service", response.getMessage());
        assertEquals(OrderStatus.REFUND_REQUIRED, order.getStatus());

        Mockito.verify(paymentSystem).refund("pay123", 100.0);
    }

    @Test
    void RefundWithoutValidReason() {
        Event event = eventRepo.findById(eventId);

        Order order = new Order(123, 900, eventId, List.of(1, 2), 100.0, "pay123");
        // markRefundRequired not called

        event.getOrders().add(order);
        eventRepo.store(event);

        Response<Boolean> response = eventCompanyManageService.processRefund(
                validToken1,
                eventId,
                123
        );

        assertFalse(response.getValue());
        assertEquals("Order cannot be refunded", response.getMessage());
        assertEquals(OrderStatus.APPROVED, order.getStatus());

        Mockito.verify(paymentSystem, Mockito.never())
                .refund(Mockito.anyString(), Mockito.anyDouble());
    }
    @Test
    void RefundServiceUnavailable() {
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble()))
                .thenThrow(new RuntimeException("Payment service unavailable"));

        Event event = eventRepo.findById(eventId);

        Order order = new Order(123, 900, eventId, List.of(1, 2), 100.0, "pay123");
        order.markRefundRequired();
        event.getOrders().add(order);
        eventRepo.store(event);
        Response<Boolean> response = eventCompanyManageService.processRefund(
                validToken1,
                eventId,
                123
        );

        assertFalse(response.getValue());
        assertTrue(response.getMessage().contains("Failed to process refund"));
        assertEquals(OrderStatus.REFUND_REQUIRED, order.getStatus());

        Mockito.verify(paymentSystem).refund("pay123", 100.0);
    }

    //TODO : update after there is order function in event service
    @Test
    void GivenValidOwnerAndFutureEventWithOrders_WhenDeleteEvent_ThenEventMarkedInactiveAndRefundProcessed() {
        // Given
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble()))
                .thenReturn(true);

        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );
        Event event = eventRepo.findById(eventId);

        Order order = new Order(1, 2, eventId, List.of(1, 2), 100.0, "pay123");
        event.getOrders().add(order);
        eventRepo.store(event);

        // When
        Response<Boolean> response = eventCompanyManageService.DeleteEvent(validToken1, eventId);

        // Then
        assertTrue(response.getValue());
        assertEquals("Orders deleted successfully", response.getMessage());

        Event updatedEvent = eventRepo.findById(eventId);
        assertFalse(updatedEvent.isActive());

        Order updatedOrder = updatedEvent.findOrderById(1);
        assertEquals(OrderStatus.REFUNDED, updatedOrder.getStatus());

        Mockito.verify(paymentSystem).refund("pay123", 100.0);
    }

    @Test
    void GivenUserWithoutPermission_WhenDeleteEvent_ThenPermissionErrorReturned() {
        // Given
        // validToken2 - without permissions
        eventCompanyManageService.DefineVenueAndSeatingMap(validToken1, eventId, stage, entries, standingZones, seatingZones);

        // When
        Response<Boolean> response = eventCompanyManageService.DeleteEvent(validToken2, eventId);

        // Then
        assertFalse(response.getValue());
        assertEquals("User does not have permission to delete event", response.getMessage());

        Event event = eventRepo.findById(eventId);
        assertTrue(event.isActive());

        Mockito.verify(paymentSystem, Mockito.never())
                .refund(Mockito.anyString(), Mockito.anyDouble());
    }

    @Test
    void GivenNonExistingEvent_WhenDeleteEvent_ThenEventNotFoundErrorReturned() {
        // Given
        String nonExistingEventId = "333";

        // When
        Response<Boolean> response = eventCompanyManageService.DeleteEvent(validToken1, nonExistingEventId);

        // Then
        assertFalse(response.getValue());
        assertTrue(response.getMessage().startsWith("failed to detele event : "));
    }

}
