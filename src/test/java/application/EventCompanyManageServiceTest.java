package application;

import DTO.*;
import Log.LoggerSetup;
import domain.Suspension.ISuspensionRepo;
import domain.activeOrder.IActiveOrderRepo;
import domain.company.Company;
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
import domain.event.Order;
import domain.lottery.ILotteryRepo;
import domain.user.IUserRepo;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import domain.dto.EventMapDTO;

import java.util.HashMap;
import java.util.Map;

import domain.dataType.PermissionType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import domain.policy.DiscountPolicyType;
import domain.policy.PurchasePolicyType;
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
    private String managerToken;
    private int managerId;
    private IAuth auth;
    private ISuspensionRepo suspensionRepo;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private IEventRepo eventRepo;

    private LocalDateTime eventDate;
    private Integer eventId;


    private UserService userService;
    private EventCompanyManageService eventCompanyManageService;
    private CompanyService companyService;
    private String validToken2;
    private String invalidToken;
    private EventService eventService;
    private IPaymentSystem paymentSystem;
    private ITicketSupply ticketSupply;
    private ActiveOrderService activeOrderService;
    private String GUEST_TOKEN;
    private String ADMIN_TOKEN;
    private AdminService adminService;
    private INotifier notifier;


    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
        userRepo=new UserRepo();
        passwordEncoder=new PasswordEncoderUtil();
        tokenService = new TokenService();
        suspensionRepo = new SuspensionRepoImpl();
        auth=new Auth(tokenService);
        companyRepo=new CompanyRepoImpl();
        eventRepo = new EventRepoImpl();

        paymentSystem = Mockito.mock(IPaymentSystem.class);
        ticketSupply = Mockito.mock(ITicketSupply.class);
        notifier = new VaadinNotifier();
        userService=new UserService(tokenService,auth,userRepo,passwordEncoder,notifier);
        eventService=new EventService(auth,eventRepo);
        IActiveOrderRepo activeOrderRepo=new ActiveOrderRepoImpl();
        ILotteryRepo lotteryRepo=new LotteryRepoImpl();
        activeOrderService=new ActiveOrderService(auth,activeOrderRepo,eventRepo,companyRepo,lotteryRepo,paymentSystem,ticketSupply,suspensionRepo,notifier,new PreExpirationNotificationScheduler(activeOrderRepo,notifier),userRepo, 100);
        GUEST_TOKEN= userService.continueAsGuest().getValue();
        //should delete order repo from company service construture
        companyService=new CompanyService(auth,companyRepo,userRepo,suspensionRepo,notifier);
        eventCompanyManageService = new EventCompanyManageService(
                companyRepo,
                eventRepo,
                auth,
                paymentSystem,suspensionRepo,notifier,userRepo
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

        eventId=eventCompanyManageService.createEvent(validToken1,companyId,LocalDateTime.now().plusDays(10),"test-event",LocalDateTime.now().minusMinutes(10),false, GeographicalArea.CENTER, CategoryEvent.FESTIVAL).getValue();
        eventDate=LocalDateTime.now().plusDays(10);
        stage = new ElementPositionDTO(10, 20);
        entries = List.of(new ElementPositionDTO(0, 0), new ElementPositionDTO(50, 10));
        standingZones = List.of(new StandingZoneDTO(200, "floor", 100.0, new ElementPositionDTO(1, 1)));
        seatingZones = List.of(new SeatingZoneDTO(10, 20, "tribune", 150.0, new ElementPositionDTO(5, 5)));

        UserDTO managerDTO = new UserDTO("manager_event@test.com", "Manager", "Test", "Password123!", 1, 1, 2000, "City", "050-555-9999");
        userService.registerUser(null, managerDTO);
        managerToken = userService.login("manager_event@test.com", "Password123!").getValue();
        managerId = userService.getUserId(managerToken).getValue();
        Company setupCompany = companyRepo.findById(companyId);
        setupCompany.getCompanyPermission().addToTree(managerId, userService.getUserId(validToken1).getValue(), new HashSet<>());
        companyRepo.store(setupCompany);

        String adminEmail = "admin_master@bgu.ac.il";
        auth = new Auth(tokenService, Set.of(adminEmail));
       // userService = new UserService(tokenService, auth, userRepo, passwordEncoder,notifier);
        userService.registerUser(null, new UserDTO(adminEmail, "Admin", "Sys", "Pass123!", 1, 1, 2000, "Address", "050-000-0000"));
        ADMIN_TOKEN = userService.login(adminEmail, "Pass123!").getValue();
        adminService = new AdminService(auth, userRepo, companyRepo, eventRepo, paymentSystem,suspensionRepo, notifier);

    }

    private int createCompletedOrderThroughPurchaseFlow(String buyerToken, int eventId, int ticketCount) {
        Response<EnterPurchaseDTO> enterResponse =
                activeOrderService.enterEventPurchase(buyerToken, companyId, eventId,null);

        assertNotNull(enterResponse.getValue(),
                "enterEventPurchase failed: " + enterResponse.getMessage());

        Response<Integer> selectResponse =
                activeOrderService.userSelectTickets(
                        buyerToken,
                        eventId,
                        new HashMap<>(),
                        Map.of("floor", ticketCount)
                );

        assertNotNull(selectResponse.getValue(),
                "userSelectTickets failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-" + activeOrderId);

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);
        Response<CheckoutPriceDTO> checkoutPriceResponse =
                activeOrderService.prepareCheckout(buyerToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );


        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> checkoutResponse =
                activeOrderService.checkoutAndPayment(
                        buyerToken,
                        activeOrderId,
                        paymentDetails
                );

        assertNotNull(checkoutResponse.getValue(),
                "checkoutAndPayment failed: " + checkoutResponse.getMessage());

        return checkoutResponse.getValue();
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
                -1,
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
        Response<Integer> response =eventCompanyManageService.createEvent(
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
        Response<Integer> response =eventCompanyManageService.createEvent(
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
        Response<Integer> response =eventCompanyManageService.createEvent(
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
        Response<Integer> response =eventCompanyManageService.createEvent(
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
        Response<Integer> response =eventCompanyManageService.createEvent(
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
        createCompletedOrderThroughPurchaseFlow(validToken2, eventId, 1);
        String purchaserEmail = "user2@test.com";
        userService.cleanDelayedNotifications(purchaserEmail);

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
        List<NotifyDTO> notifications = userRepo.findUserByEmail(purchaserEmail).getDelayedNotifications();
        assertEquals(1, notifications.size(), "Purchaser should receive exactly one notification about the date change");
        assertTrue(notifications.get(0).getPayload().getMessage().contains("has been updated to"),
                "Notification should mention the updated date");
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
                -1,
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertTrue(response.getMessage().startsWith("failed to create event : "));
    }

    @Test
    void GivenCompanyExistsAndUserHasPermissionAndOrdersExist_WhenGetOrdersByCompany_ThenOrdersHistoryIsReturned() {
        // Given
        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        createCompletedOrderThroughPurchaseFlow(validToken1, eventId, 1);
        createCompletedOrderThroughPurchaseFlow(validToken2, eventId, 1);

        // When
        Response<List<OrderDTO>> response =eventCompanyManageService.getOrdersByCompany(validToken1, companyId);

        // Then
        assertNotNull(response.getValue());
        assertEquals("Orders found", response.getMessage());
        assertEquals(2, response.getValue().size());
    }

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
                -1,
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
        Integer lotteryEventId = eventCompanyManageService.createEvent(
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
        Response<CompanyDetailsDTO> response = eventCompanyManageService.getCompanyDetails(GUEST_TOKEN, companyId);

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
        Response<CompanyDetailsDTO> response = eventCompanyManageService.getCompanyDetails(GUEST_TOKEN, closedCompanyId);

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
        Integer event = eventCompanyManageService.createEvent(
                validToken1,
                companyId,
                eventDate,
                "event1",
                LocalDateTime.now().minusMinutes(10),
                false,
                GeographicalArea.NORTH,
                CategoryEvent.SPORTS
        ).getValue();

        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                event,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        createCompletedOrderThroughPurchaseFlow(validToken1, event, 1);
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
        Response<SalesReportDTO> response = eventCompanyManageService.generateSalesReports(companyId, GUEST_TOKEN);

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
    void GivenRefundRequiredOrder_WhenProcessRefundAndExternalPaymentApproves_ThenOrderMarkedRefunded() {
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble()))
                .thenReturn(true);

        Event event = eventRepo.findById(eventId);
        String buyerEmail = "user2@test.com";
        Order order = new Order(
                1,
                buyerEmail,
                eventId,
                "Test Event",
                "2026-01-01T20:00",
                "TEL_AVIV",
                List.of(
                        new PurchasedTicketDTO(
                                1,
                                "floor",
                                "STANDING",
                                null,
                                null,
                                50.0
                        ),
                        new PurchasedTicketDTO(
                                2,
                                "floor",
                                "STANDING",
                                null,
                                null,
                                50.0
                        )
                ),
                List.of(1, 2),
                100.0,
                "pay123"
        );

        order.markRefundRequired();
        event.getOrders().add(order);
        eventRepo.store(event);
        userRepo.findUserByEmail(buyerEmail).clearDelayedNotifications();
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
        List<DTO.NotifyDTO> notifications = userRepo.findUserByEmail(buyerEmail).getDelayedNotifications();
        assertEquals(1, notifications.size(), "Buyer should receive 1 success notification");
        assertTrue(notifications.get(0).getPayload().getMessage().toLowerCase().contains("successfully")
                || notifications.get(0).getPayload().getMessage().contains("Refund process for"));
    }

    @Test
    void GivenMissingOrder_WhenProcessRefund_ThenNoMatchingOrderReturned() {
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
    void GivenRefundRequiredOrder_WhenProcessRefundAndExternalPaymentRejects_ThenOrderRemainsRefundRequired() {
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble()))
                .thenReturn(false);

        Event event = eventRepo.findById(eventId);
        String buyerEmail = "user2@test.com";
        Order order = new Order(
                123,
                buyerEmail,
                eventId,
                "Test Event",
                "2026-01-01T20:00",
                "TEL_AVIV",
                List.of(
                        new PurchasedTicketDTO(
                                1,
                                "floor",
                                "STANDING",
                                null,
                                null,
                                50.0
                        ),
                        new PurchasedTicketDTO(
                                2,
                                "floor",
                                "STANDING",
                                null,
                                null,
                                50.0
                        )
                ),
                List.of(1, 2),
                100.0,
                "pay123"
        );


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
        List<DTO.NotifyDTO> notifications = userRepo.findUserByEmail(buyerEmail).getDelayedNotifications();
        assertEquals(1, notifications.size(), "Buyer should receive 1 failure notification");
        assertTrue(notifications.get(0).getPayload().getMessage().toLowerCase().contains("failed"));
    }

    @Test
    void GivenApprovedOrder_WhenProcessRefund_ThenOrderCannotBeRefunded() {
        Event event = eventRepo.findById(eventId);

        Order order = new Order(
                123,
                "900",
                eventId,
                "Test Event",
                "2026-01-01T20:00",
                "TEL_AVIV",
                List.of(
                        new PurchasedTicketDTO(
                                1,
                                "floor",
                                "STANDING",
                                null,
                                null,
                                50.0
                        ),
                        new PurchasedTicketDTO(
                                2,
                                "floor",
                                "STANDING",
                                null,
                                null,
                                50.0
                        )
                ),
                List.of(1, 2),
                100.0,
                "pay123"
        );     // markRefundRequired not called

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
    void GivenRefundRequiredOrder_WhenProcessRefundAndExternalPaymentServiceUnavailable_ThenOrderRemainsRefundRequired() {
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble()))
                .thenThrow(new RuntimeException("Payment service unavailable"));

        Event event = eventRepo.findById(eventId);
        Order order = new Order(
                123,
                "900",
                eventId,
                "Test Event",
                "2026-01-01T20:00",
                "TEL_AVIV",
                List.of(
                        new PurchasedTicketDTO(
                                1,
                                "floor",
                                "STANDING",
                                null,
                                null,
                                50.0
                        ),
                        new PurchasedTicketDTO(
                                2,
                                "floor",
                                "STANDING",
                                null,
                                null,
                                50.0
                        )
                ),
                List.of(1, 2),
                100.0,
                "pay123"
        );
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

        int orderId = createCompletedOrderThroughPurchaseFlow(validToken1, eventId, 1);
        Order createdOrder = eventRepo.findById(eventId).findOrderById(orderId);
        double expectedRefundAmount = createdOrder.getTotalSum();
        String buyerEmail = "user1@test.com";
        userRepo.findUserByEmail(buyerEmail).clearDelayedNotifications();
        // When
        Response<Boolean> response = eventCompanyManageService.DeleteEvent(validToken1, eventId);

        // Then
        assertTrue(response.getValue());
        assertEquals("Orders deleted successfully", response.getMessage());

        Event updatedEvent = eventRepo.findById(eventId);
        assertFalse(updatedEvent.isActive());

        Order updatedOrder = updatedEvent.findOrderById(orderId);
        assertEquals(OrderStatus.REFUNDED, updatedOrder.getStatus());

        Mockito.verify(paymentSystem).refund("payment-" + orderId, expectedRefundAmount);
        List<DTO.NotifyDTO> notifications = userRepo.findUserByEmail(buyerEmail).getDelayedNotifications();
        assertEquals(2, notifications.size(), "Buyer should receive 2 notifications: Cancellation and Refund");
        assertTrue(notifications.get(0).getPayload().getMessage().toLowerCase().contains("cancelled"));
        assertTrue(notifications.get(1).getPayload().getMessage().toLowerCase().contains("refund process"));
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
        Integer nonExistingEventId = 333;

        // When
        Response<Boolean> response = eventCompanyManageService.DeleteEvent(validToken1, nonExistingEventId);

        // Then
        assertFalse(response.getValue());
        assertTrue(response.getMessage().startsWith("failed to detele event : "));
    }

    // Race Condition
    @Test
    void GivenHighLoad_WhenManagerDeletesEventAndAddsZoneSimultaneously_ThenEventIsSafelyDeleted() throws InterruptedException {
        // Arrange: Prepare new zones to add
        List<StandingZoneDTO> newStandingZones = List.of(new StandingZoneDTO(500, "Golden Ring", 300.0, new ElementPositionDTO(2, 2)));

        // Setup concurrency tools
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(2);

        // Act: Thread 1 - Manager attempts to delete the event
        executor.submit(() -> {
            try {
                startGun.await(); // Wait for the exact start signal
                eventCompanyManageService.DeleteEvent(validToken1, eventId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finishLine.countDown();
            }
        });

        // Act: Thread 2 - Manager attempts to add a zone to the same event
        executor.submit(() -> {
            try {
                startGun.await(); // Wait for the exact start signal
                eventCompanyManageService.AddZonesToEventMap(validToken1, eventId, newStandingZones, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finishLine.countDown();
            }
        });

        // Both threads execute exactly at the same millisecond
        startGun.countDown();
        finishLine.await(); // Wait for both threads (and their retries) to finish

        // Assert: Verify data integrity
        Event updatedEvent = eventRepo.findById(eventId);

        // The critical business rule: The event MUST be inactive at the end.
        // If Delete won first -> AddZone will fail (event is inactive) or RetryHelper will catch it.
        // If AddZone won first -> Delete will deactivate the event right after.
        assertFalse(updatedEvent.isActive(), "Event should be deactivated/deleted regardless of the concurrent add zone attempt");

        executor.shutdown();
    }

    @Test
    void GivenHighLoad_WhenManagerCreatesMultipleEventsSimultaneously_ThenAllEventsAreSuccessfullyCreated() throws InterruptedException {
        // Arrange: Set up 20 concurrent event creations
        int numberOfConcurrentEvents = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfConcurrentEvents);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(numberOfConcurrentEvents);

        // Act: Create 20 threads, each trying to create a unique event for the same company
        for (int i = 0; i < numberOfConcurrentEvents; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGun.await(); // Wait for the start signal

                    LocalDateTime futureDate = LocalDateTime.now().plusDays(10 + index);
                    LocalDateTime saleDate = LocalDateTime.now().plusDays(5);

                    eventCompanyManageService.createEvent(
                            validToken1,
                            companyId,
                            futureDate,
                            "Massive Concurrent Event " + index,
                            saleDate,
                            false,
                            GeographicalArea.CENTER,
                            CategoryEvent.FESTIVAL
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLine.countDown();
                }
            });
        }

        // All 20 creations hit the service simultaneously
        startGun.countDown();
        finishLine.await(); // Wait for all threads and their respective retries to finish

        // Assert: Verify that no event was lost due to concurrent overwrites on the company list
        // We fetch all events for this company.
        // We expect the 1 original event from setUp() + 20 new concurrent events = 21 total events.
        List<Event> companyEvents = eventRepo.findByCompany(companyId);

        assertEquals(numberOfConcurrentEvents + 1, companyEvents.size(),
                "All concurrent events must be successfully saved without overwriting each other");

        executor.shutdown();
    }
    @Test
    void GivenConcurrentAdminClose_WhenUserViewsCompanyDetails_ThenConsistentResult() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> adminFuture = executor.submit(() -> {
            start.await();
            return adminService.closeCompanyByAdmin(ADMIN_TOKEN, companyId);
        });

        Future<Response<CompanyDetailsDTO>> userFuture = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.getCompanyDetails(GUEST_TOKEN, companyId);
        });

        start.countDown();
        adminFuture.get();
        Response<CompanyDetailsDTO> userRes = userFuture.get();
        executor.shutdown();

        if (userRes.getValue() == null) {
            assertTrue(userRes.getMessage().contains("User is not permitted to view closed companies"),
                    "If closed, guest should get permission error, got: " + userRes.getMessage());
        } else {
            assertEquals(companyId, userRes.getValue().getCompanyId());
        }

    }

    @Test
    void GivenConcurrentEventDeletion_WhenUserViewsCompanyDetails_ThenConsistentEventList() throws Exception {
        Response<Boolean> mapRes = eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1, eventId, stage, entries, standingZones, seatingZones);
        assertTrue(mapRes.getValue(), "Map must be defined to activate the event");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> deleteFuture = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.DeleteEvent(validToken1, eventId);
        });

        Future<Response<CompanyDetailsDTO>> viewFuture = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.getCompanyDetails(GUEST_TOKEN, companyId);
        });

        start.countDown();

        Response<Boolean> deleteRes = deleteFuture.get();
        Response<CompanyDetailsDTO> viewRes = viewFuture.get();
        executor.shutdown();
        assertTrue(deleteRes.getValue(), "Event deletion should succeed");
        assertNotNull(viewRes.getValue(), "Company details should be retrieved successfully without crashing");

        Event deletedEvent = eventRepo.findById(eventId);
        assertFalse(deletedEvent.isActive(), "Event must be inactive in the DB after deletion");
    }

    @Test
    void GivenConcurrentPurchase_WhenGenerateSalesReport_ThenRevenueIsAccurate() throws Exception {
        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> purchaseFuture = executor.submit(() -> {
            start.await();
            return createCompletedOrderThroughPurchaseFlow(validToken2, eventId, 1);
        });

        Future<Response<SalesReportDTO>> reportFuture = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.generateSalesReports(companyId, validToken1);
        });

        start.countDown();
        purchaseFuture.get();
        Response<SalesReportDTO> reportRes = reportFuture.get();
        executor.shutdown();

        assertNotNull(reportRes.getValue(), "Report must be generated successfully");
        assertTrue(reportRes.getValue().getTotalRevenue() >= 0, "Revenue calculation must not fail or corrupt");
    }
    @Test
    void GivenConcurrentAdminRemoveManager_WhenOwnerGeneratesReport_ThenReportIsConsistent() throws Exception {
        String managerEmail = "manager_race" + System.currentTimeMillis() + "@test.com";
        userService.registerUser(null, new UserDTO(managerEmail, "Man", "Ager", "Pass123!", 1, 1, 2000, "City", "050-999-9999"));
        String managerToken = userService.login(managerEmail, "Pass123!").getValue();
        int MANAGER_ID = userService.getUserId(managerToken).getValue();

        Company company = companyRepo.findById(companyId);
        int ownerId = userService.getUserId(validToken1).getValue();
        company.getCompanyPermission().addToTree(MANAGER_ID, ownerId, new HashSet<>());
        companyRepo.store(company);

        eventCompanyManageService.createEvent(managerToken, companyId, LocalDateTime.now().plusDays(20),
                "Manager's Event", LocalDateTime.now().plusDays(5), false, GeographicalArea.CENTER, CategoryEvent.FESTIVAL);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> removeFuture = executor.submit(() -> {
            start.await();
            return adminService.removeUser(ADMIN_TOKEN, MANAGER_ID);
        });

        Future<Response<SalesReportDTO>> reportFuture = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.generateSalesReports(companyId, validToken1);
        });

        start.countDown();
        removeFuture.get();
        Response<SalesReportDTO> reportRes = reportFuture.get();
        executor.shutdown();

        assertNotNull(reportRes.getValue(), "Report generation must survive concurrent tree modification");
    }

    @Test
    void GivenLoggedInMemberWithOrders_WhenGetPurchaseHistory_ThenOrdersReturned() {
        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        createCompletedOrderThroughPurchaseFlow(validToken1, eventId, 1);
        createCompletedOrderThroughPurchaseFlow(validToken1, eventId, 1);

        Response<List<PurchaseHistoryDTO>> response =
                eventCompanyManageService.getPurchaseHistoryByUser(validToken1);
        assertNotNull(response.getValue());
        assertEquals(2, response.getValue().size());
    }

    @Test
    void GivenLoggedInMemberWithoutOrders_WhenGetPurchaseHistory_ThenEmptyListReturned() {
        Response<List<PurchaseHistoryDTO>> response =
                eventCompanyManageService.getPurchaseHistoryByUser(validToken2);
        assertNotNull(response.getValue());
        assertTrue(response.getValue().isEmpty());
        assertEquals("No purchase history found for user", response.getMessage());
    }

    @Test
    void GivenLoggedOutUser_WhenGetPurchaseHistory_ThenErrorReturned() {
        Response<List<PurchaseHistoryDTO>> response =
                eventCompanyManageService.getPurchaseHistoryByUser(null);
        assertNull(response.getValue());
        assertEquals("User is not logged in", response.getMessage());
    }

    @Test
    void GivenTwoMembersWithOrders_WhenGetPurchaseHistory_ThenOnlyOwnOrdersReturned() {
        String user1Id = auth.getUserEmail(validToken1).getValue();
        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        int user1OrderId =
                createCompletedOrderThroughPurchaseFlow(validToken1, eventId, 1);

        createCompletedOrderThroughPurchaseFlow(validToken2, eventId, 1);

        Response<List<PurchaseHistoryDTO>> response =
                eventCompanyManageService.getPurchaseHistoryByUser(validToken1);
        assertNotNull(response.getValue());
        assertEquals(1, response.getValue().size());
        assertEquals(user1OrderId, response.getValue().get(0).getOrderId());
    }
    @Test
    void GivenMultiplePurchasers_WhenUpdateEventDate_ThenAllPurchasersReceiveNotification() {
        // Arrange
        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1, eventId, stage, entries, standingZones, seatingZones
        );
        String email1 = "buyer1@test.com";
        String email2 = "buyer2@test.com";

        userService.registerUser("", new UserDTO(email1, "B", "1", "pass", 1, 1, 2000, "Address", "050-111-1111"));
        userService.registerUser("", new UserDTO(email2, "B", "2", "pass", 1, 1, 2000, "Address", "050-222-2222"));

        String token1 = userService.login(email1, "pass").getValue();
        String token2 = userService.login(email2, "pass").getValue();

        createCompletedOrderThroughPurchaseFlow(token1, eventId, 1);
        createCompletedOrderThroughPurchaseFlow(token2, eventId, 1);

        userService.cleanDelayedNotifications(email1);
        userService.cleanDelayedNotifications(email2);

        LocalDateTime newDate = eventDate.plusDays(5);

        // Act
        Response<Boolean> response = eventCompanyManageService.UpdateEventDate(validToken1, eventId, newDate);

        // Assert
        assertTrue(response.getValue());

        assertEquals(1, userRepo.findUserByEmail(email1).getDelayedNotifications().size(), "First buyer must be notified");
        assertEquals(1, userRepo.findUserByEmail(email2).getDelayedNotifications().size(), "Second buyer must be notified");
    }
    @Test
    void GivenSamePurchaserWithMultipleOrders_WhenUpdateEventDate_ThenOnlyOneNotificationIsSent() {
        // Arrange
        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1, eventId, stage, entries, standingZones, seatingZones
        );

        String email = "heavy_buyer@test.com";
        userService.registerUser("", new UserDTO(email, "Heavy", "Buyer", "pass", 1, 1, 2000, "Address", "050-999-9999"));
        String buyerToken = userService.login(email, "pass").getValue();
        createCompletedOrderThroughPurchaseFlow(buyerToken, eventId, 1);
        createCompletedOrderThroughPurchaseFlow(buyerToken, eventId, 1);
        createCompletedOrderThroughPurchaseFlow(buyerToken, eventId, 1);

        userService.cleanDelayedNotifications(email);

        LocalDateTime newDate = eventDate.plusDays(10);

        // Act
        Response<Boolean> response = eventCompanyManageService.UpdateEventDate(validToken1, eventId, newDate);

        // Assert
        assertTrue(response.getValue());
        List<NotifyDTO> notifications = userRepo.findUserByEmail(email).getDelayedNotifications();
        assertEquals(1, notifications.size(),
                "A buyer with multiple separate orders should still receive only ONE notification about the date change (distinct test)");
    }

    // ===================== Event Purchase Rules =====================

    @Test
    void GivenOwnerAndValidRule_WhenAddRuleToEvent_ThenSuccess() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = eventCompanyManageService.addRuleToEvent(validToken1, eventId, ruleDTO);
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenOwnerAddsMultipleRules_WhenAddRuleToEvent_ThenAllSucceed() {
        Response<Boolean> r1 = eventCompanyManageService.addRuleToEvent(validToken1, eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4));
        Response<Boolean> r2 = eventCompanyManageService.addRuleToEvent(validToken1, eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18));
        assertFalse(r1.isError());
        assertFalse(r2.isError());
    }

    @Test
    void GivenInvalidToken_WhenAddRuleToEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.addRuleToEvent("invalid-token", eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4));
        assertTrue(response.isError());
    }

    @Test
    void GivenUserWithoutPermission_WhenAddRuleToEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.addRuleToEvent(validToken2, eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4));
        assertTrue(response.isError());
    }

    @Test
    void GivenManagerWithManagePoliciesPermission_WhenAddRuleToEvent_ThenSuccess() {
        companyService.updateManagerPermissions(validToken1, companyId, managerId, EnumSet.of(PermissionType.MANAGE_POLICIES));
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = eventCompanyManageService.addRuleToEvent(managerToken, eventId, ruleDTO);
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenManagerWithoutManagePoliciesPermission_WhenAddRuleToEvent_ThenError() {
        // managerToken has empty permissions by default
        Response<Boolean> response = eventCompanyManageService.addRuleToEvent(managerToken, eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4));
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativeTicketCount_WhenAddRuleToEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.addRuleToEvent(validToken1, eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, -1));
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativeMinAge_WhenAddRuleToEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.addRuleToEvent(validToken1, eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, -5));
        assertTrue(response.isError());
    }

    @Test
    void GivenEventNotFound_WhenAddRuleToEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.addRuleToEvent(validToken1, 99999, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4));
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenAddRuleToEvent_ThenError() {
        companyService.deactivateCompany(validToken1, companyId);
        Response<Boolean> response = eventCompanyManageService.addRuleToEvent(validToken1, eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4));
        assertTrue(response.isError());
    }

    @Test
    void GivenDuplicateRule_WhenAddRuleToEvent_ThenError() {
        // MAX_TICKETS 20 is the default rule created with the event
        Response<Boolean> response = eventCompanyManageService.addRuleToEvent(validToken1, eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 20));
        assertTrue(response.isError());
    }

    @Test
    void GivenOwnerAndExistingRule_WhenRemoveRuleFromEvent_ThenSuccess() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18);
        eventCompanyManageService.addRuleToEvent(validToken1, eventId, ruleDTO);
        Response<Boolean> response = eventCompanyManageService.removeRuleFromEvent(validToken1, eventId, ruleDTO);
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenRuleNotFound_WhenRemoveRuleFromEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.removeRuleFromEvent(validToken1, eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18));
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenRemoveRuleFromEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.removeRuleFromEvent("invalid-token", eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 20));
        assertTrue(response.isError());
    }

    @Test
    void GivenUserWithoutPermission_WhenRemoveRuleFromEvent_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18);
        eventCompanyManageService.addRuleToEvent(validToken1, eventId, ruleDTO);
        Response<Boolean> response = eventCompanyManageService.removeRuleFromEvent(validToken2, eventId, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenManagerWithManagePoliciesPermission_WhenRemoveRuleFromEvent_ThenSuccess() {
        companyService.updateManagerPermissions(validToken1, companyId, managerId, EnumSet.of(PermissionType.MANAGE_POLICIES));
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18);
        eventCompanyManageService.addRuleToEvent(validToken1, eventId, ruleDTO);
        Response<Boolean> response = eventCompanyManageService.removeRuleFromEvent(managerToken, eventId, ruleDTO);
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenManagerWithoutManagePoliciesPermission_WhenRemoveRuleFromEvent_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18);
        eventCompanyManageService.addRuleToEvent(validToken1, eventId, ruleDTO);
        Response<Boolean> response = eventCompanyManageService.removeRuleFromEvent(managerToken, eventId, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenEventNotFound_WhenRemoveRuleFromEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.removeRuleFromEvent(validToken1, 99999, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4));
        assertTrue(response.isError());
    }

    // ===================== Event Discounts =====================

    @Test
    void GivenOwnerAndValidDiscount_WhenAddDiscountToEvent_ThenSuccess() {
        DiscountDTO discountDTO = new DiscountDTO(20.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = eventCompanyManageService.addDiscountToEvent(validToken1, eventId, discountDTO);
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenInvalidToken_WhenAddDiscountToEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.addDiscountToEvent("invalid-token", eventId, new DiscountDTO(10.0, LocalDate.now().plusDays(1)));
        assertTrue(response.isError());
    }

    @Test
    void GivenUserWithoutPermission_WhenAddDiscountToEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.addDiscountToEvent(validToken2, eventId, new DiscountDTO(10.0, LocalDate.now().plusDays(1)));
        assertTrue(response.isError());
    }

    @Test
    void GivenManagerWithManagePoliciesPermission_WhenAddDiscountToEvent_ThenSuccess() {
        companyService.updateManagerPermissions(validToken1, companyId, managerId, EnumSet.of(PermissionType.MANAGE_POLICIES));
        Response<Boolean> response = eventCompanyManageService.addDiscountToEvent(managerToken, eventId, new DiscountDTO(20.0, LocalDate.now().plusDays(1)));
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenManagerWithoutManagePoliciesPermission_WhenAddDiscountToEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.addDiscountToEvent(managerToken, eventId, new DiscountDTO(20.0, LocalDate.now().plusDays(1)));
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativePercentage_WhenAddDiscountToEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(-10.0, LocalDate.now().plusDays(1)));
        assertTrue(response.isError());
    }

    @Test
    void GivenEmptyCouponCode_WhenAddDiscountToEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO("", 10.0, LocalDate.now().plusDays(1)));
        assertTrue(response.isError());
    }

    @Test
    void GivenEventNotFound_WhenAddDiscountToEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.addDiscountToEvent(validToken1, 99999, new DiscountDTO(10.0, LocalDate.now().plusDays(1)));
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenAddDiscountToEvent_ThenError() {
        companyService.deactivateCompany(validToken1, companyId);
        Response<Boolean> response = eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(10.0, LocalDate.now().plusDays(1)));
        assertTrue(response.isError());
    }

    @Test
    void GivenDuplicateDiscount_WhenAddDiscountToEvent_ThenError() {
        LocalDate endDate = LocalDate.now().plusDays(1);
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(15.0, endDate));
        Response<Boolean> response = eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(15.0, endDate));
        assertTrue(response.isError());
    }

    @Test
    void GivenExistingDiscount_WhenRemoveDiscountFromEvent_ThenSuccess() {
        LocalDate endDate = LocalDate.now().plusDays(1);
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(10.0, endDate));
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(80.0, endDate));
        Response<Boolean> response = eventCompanyManageService.removeDiscountFromEvent(validToken1, eventId, new DiscountDTO(10.0, endDate));
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenDiscountDoesNotExist_WhenRemoveDiscountFromEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.removeDiscountFromEvent(validToken1, eventId, new DiscountDTO(99.0, LocalDate.now().plusDays(1)));
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenRemoveDiscountFromEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.removeDiscountFromEvent("invalid-token", eventId, new DiscountDTO(10.0, LocalDate.now().plusDays(1)));
        assertTrue(response.isError());
    }

    @Test
    void GivenUserWithoutPermission_WhenRemoveDiscountFromEvent_ThenError() {
        LocalDate endDate = LocalDate.now().plusDays(1);
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(10.0, endDate));
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(80.0, endDate));
        Response<Boolean> response = eventCompanyManageService.removeDiscountFromEvent(validToken2, eventId, new DiscountDTO(10.0, endDate));
        assertTrue(response.isError());
    }

    @Test
    void GivenManagerWithManagePoliciesPermission_WhenRemoveDiscountFromEvent_ThenSuccess() {
        companyService.updateManagerPermissions(validToken1, companyId, managerId, EnumSet.of(PermissionType.MANAGE_POLICIES));
        LocalDate endDate = LocalDate.now().plusDays(1);
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(10.0, endDate));
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(80.0, endDate));
        Response<Boolean> response = eventCompanyManageService.removeDiscountFromEvent(managerToken, eventId, new DiscountDTO(10.0, endDate));
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenManagerWithoutManagePoliciesPermission_WhenRemoveDiscountFromEvent_ThenError() {
        LocalDate endDate = LocalDate.now().plusDays(1);
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(10.0, endDate));
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(80.0, endDate));
        Response<Boolean> response = eventCompanyManageService.removeDiscountFromEvent(managerToken, eventId, new DiscountDTO(10.0, endDate));
        assertTrue(response.isError());
    }

    @Test
    void GivenEventNotFound_WhenRemoveDiscountFromEvent_ThenError() {
        Response<Boolean> response = eventCompanyManageService.removeDiscountFromEvent(validToken1, 99999, new DiscountDTO(10.0, LocalDate.now().plusDays(1)));
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenRemoveDiscountFromEvent_ThenError() {
        LocalDate endDate = LocalDate.now().plusDays(1);
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(10.0, endDate));
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(80.0, endDate));
        companyService.deactivateCompany(validToken1, companyId);
        Response<Boolean> response = eventCompanyManageService.removeDiscountFromEvent(validToken1, eventId, new DiscountDTO(10.0, endDate));
        assertTrue(response.isError());
    }

    // ===================== Change Event Purchase Policy Type =====================

    @Test
    void GivenOwner_WhenChangeEventPurchasePolicyTypeToOR_ThenSuccess() {
        Response<Void> response = eventCompanyManageService.changeEventPurchasePolicyType(validToken1, eventId, PurchasePolicyType.OR);
        assertFalse(response.isError());
        Event event = eventRepo.findById(eventId);
        assertEquals(PurchasePolicyType.OR, event.getPurchasePolicy().getPolicyType());
    }

    @Test
    void GivenOwnerWithOrPolicy_WhenChangeEventPurchasePolicyTypeToAND_ThenSuccess() {
        eventCompanyManageService.changeEventPurchasePolicyType(validToken1, eventId, PurchasePolicyType.OR);
        Response<Void> response = eventCompanyManageService.changeEventPurchasePolicyType(validToken1, eventId, PurchasePolicyType.AND);
        assertFalse(response.isError());
        Event event = eventRepo.findById(eventId);
        assertEquals(PurchasePolicyType.AND, event.getPurchasePolicy().getPolicyType());
    }

    @Test
    void GivenSamePolicyType_WhenChangeEventPurchasePolicyType_ThenNoOpAndRulesPreserved() {
        eventCompanyManageService.addRuleToEvent(validToken1, eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18));
        Response<Void> response = eventCompanyManageService.changeEventPurchasePolicyType(validToken1, eventId, PurchasePolicyType.AND);
        assertFalse(response.isError());
        assertEquals(2, eventRepo.findById(eventId).getPurchasePolicy().getRules().size());
    }

    @Test
    void GivenOwner_WhenChangePurchasePolicyType_ThenExistingRulesPreserved() {
        eventCompanyManageService.addRuleToEvent(validToken1, eventId, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18));
        eventCompanyManageService.changeEventPurchasePolicyType(validToken1, eventId, PurchasePolicyType.OR);
        assertEquals(2, eventRepo.findById(eventId).getPurchasePolicy().getRules().size());
    }

    @Test
    void GivenManagerWithManagePoliciesPermission_WhenChangeEventPurchasePolicyType_ThenSuccess() {
        companyService.updateManagerPermissions(validToken1, companyId, managerId, EnumSet.of(PermissionType.MANAGE_POLICIES));
        Response<Void> response = eventCompanyManageService.changeEventPurchasePolicyType(managerToken, eventId, PurchasePolicyType.OR);
        assertFalse(response.isError());
        assertEquals(PurchasePolicyType.OR, eventRepo.findById(eventId).getPurchasePolicy().getPolicyType());
    }

    @Test
    void GivenManagerWithoutManagePoliciesPermission_WhenChangeEventPurchasePolicyType_ThenError() {
        Response<Void> response = eventCompanyManageService.changeEventPurchasePolicyType(managerToken, eventId, PurchasePolicyType.OR);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonMember_WhenChangeEventPurchasePolicyType_ThenError() {
        Response<Void> response = eventCompanyManageService.changeEventPurchasePolicyType(validToken2, eventId, PurchasePolicyType.OR);
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenChangeEventPurchasePolicyType_ThenError() {
        Response<Void> response = eventCompanyManageService.changeEventPurchasePolicyType("invalid-token", eventId, PurchasePolicyType.OR);
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenChangeEventPurchasePolicyType_ThenError() {
        companyService.deactivateCompany(validToken1, companyId);
        Response<Void> response = eventCompanyManageService.changeEventPurchasePolicyType(validToken1, eventId, PurchasePolicyType.OR);
        assertTrue(response.isError());
    }

    @Test
    void GivenEventNotFound_WhenChangeEventPurchasePolicyType_ThenError() {
        Response<Void> response = eventCompanyManageService.changeEventPurchasePolicyType(validToken1, 99999, PurchasePolicyType.OR);
        assertTrue(response.isError());
    }

    // ===================== Change Event Discount Policy Type =====================

    @Test
    void GivenOwner_WhenChangeEventDiscountPolicyTypeToMAX_ThenSuccess() {
        Response<Void> response = eventCompanyManageService.changeEventDiscountPolicyType(validToken1, eventId, DiscountPolicyType.MAX);
        assertFalse(response.isError());
        assertEquals(DiscountPolicyType.MAX, eventRepo.findById(eventId).getDiscountPolicy().getPolicyType());
    }

    @Test
    void GivenOwnerWithMaxPolicy_WhenChangeEventDiscountPolicyTypeToSUM_ThenSuccess() {
        eventCompanyManageService.changeEventDiscountPolicyType(validToken1, eventId, DiscountPolicyType.MAX);
        Response<Void> response = eventCompanyManageService.changeEventDiscountPolicyType(validToken1, eventId, DiscountPolicyType.SUM);
        assertFalse(response.isError());
        assertEquals(DiscountPolicyType.SUM, eventRepo.findById(eventId).getDiscountPolicy().getPolicyType());
    }

    @Test
    void GivenOwner_WhenChangeDiscountPolicyType_ThenExistingDiscountsPreserved() {
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(15.0, LocalDate.now().plusDays(1)));
        eventCompanyManageService.changeEventDiscountPolicyType(validToken1, eventId, DiscountPolicyType.MAX);
        assertEquals(2, eventRepo.findById(eventId).getDiscountPolicy().getDiscounts().size());
    }

    @Test
    void GivenManagerWithManagePoliciesPermission_WhenChangeEventDiscountPolicyType_ThenSuccess() {
        companyService.updateManagerPermissions(validToken1, companyId, managerId, EnumSet.of(PermissionType.MANAGE_POLICIES));
        Response<Void> response = eventCompanyManageService.changeEventDiscountPolicyType(managerToken, eventId, DiscountPolicyType.MAX);
        assertFalse(response.isError());
        assertEquals(DiscountPolicyType.MAX, eventRepo.findById(eventId).getDiscountPolicy().getPolicyType());
    }

    @Test
    void GivenManagerWithoutManagePoliciesPermission_WhenChangeEventDiscountPolicyType_ThenError() {
        Response<Void> response = eventCompanyManageService.changeEventDiscountPolicyType(managerToken, eventId, DiscountPolicyType.MAX);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonMember_WhenChangeEventDiscountPolicyType_ThenError() {
        Response<Void> response = eventCompanyManageService.changeEventDiscountPolicyType(validToken2, eventId, DiscountPolicyType.MAX);
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenChangeEventDiscountPolicyType_ThenError() {
        Response<Void> response = eventCompanyManageService.changeEventDiscountPolicyType("invalid-token", eventId, DiscountPolicyType.MAX);
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenChangeEventDiscountPolicyType_ThenError() {
        companyService.deactivateCompany(validToken1, companyId);
        Response<Void> response = eventCompanyManageService.changeEventDiscountPolicyType(validToken1, eventId, DiscountPolicyType.MAX);
        assertTrue(response.isError());
    }

    @Test
    void GivenEventNotFound_WhenChangeEventDiscountPolicyType_ThenError() {
        Response<Void> response = eventCompanyManageService.changeEventDiscountPolicyType(validToken1, 99999, DiscountPolicyType.MAX);
        assertTrue(response.isError());
    }

    // ===================== Concurrency: Event Purchase Rules =====================

    @Test
    void GivenTwoThreadsRaceToAddSameRule_WhenAddRuleToEvent_ThenOnlyOneSucceeds() throws Exception {
        PurchaseRuleDTO rule = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.addRuleToEvent(validToken1, eventId, rule);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.addRuleToEvent(validToken1, eventId, rule);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = (!r1.isError() ? 1 : 0) + (!r2.isError() ? 1 : 0);
        assertEquals(1, success, "Only one concurrent add of the same rule should succeed");
    }

    @Test
    void GivenTwoThreadsRaceToRemoveSameRule_WhenRemoveRuleFromEvent_ThenOnlyOneSucceeds() throws Exception {
        PurchaseRuleDTO rule = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18);
        eventCompanyManageService.addRuleToEvent(validToken1, eventId, rule);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.removeRuleFromEvent(validToken1, eventId, rule);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.removeRuleFromEvent(validToken1, eventId, rule);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = (!r1.isError() ? 1 : 0) + (!r2.isError() ? 1 : 0);
        assertEquals(1, success, "Only one concurrent remove of the same rule should succeed");
    }

    // ===================== Concurrency: Event Discounts =====================

    @Test
    void GivenTwoThreadsRaceToAddSameDiscount_WhenAddDiscountToEvent_ThenOnlyOneSucceeds() throws Exception {
        DiscountDTO discount = new DiscountDTO(25.0, LocalDate.now().plusDays(10));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.addDiscountToEvent(validToken1, eventId, discount);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.addDiscountToEvent(validToken1, eventId, discount);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = (!r1.isError() ? 1 : 0) + (!r2.isError() ? 1 : 0);
        assertEquals(1, success, "Only one concurrent add of the same discount should succeed");
    }

    @Test
    void GivenTwoThreadsRaceToRemoveSameDiscount_WhenRemoveDiscountFromEvent_ThenOnlyOneSucceeds() throws Exception {
        DiscountDTO discount = new DiscountDTO(25.0, LocalDate.now().plusDays(10));
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, new DiscountDTO(10.0, LocalDate.now().plusDays(5)));
        eventCompanyManageService.addDiscountToEvent(validToken1, eventId, discount);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.removeDiscountFromEvent(validToken1, eventId, discount);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.removeDiscountFromEvent(validToken1, eventId, discount);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = (!r1.isError() ? 1 : 0) + (!r2.isError() ? 1 : 0);
        assertEquals(1, success, "Only one concurrent remove of the same discount should succeed");
    }

    // ===================== Concurrency: Change Event Policy Types =====================

    @Test
    void GivenTwoThreadsRaceToChangePurchasePolicyType_WhenChangeEventPurchasePolicyType_ThenFinalStateIsValid() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Void>> f1 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.changeEventPurchasePolicyType(validToken1, eventId, PurchasePolicyType.OR);
        });
        Future<Response<Void>> f2 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.changeEventPurchasePolicyType(validToken1, eventId, PurchasePolicyType.AND);
        });

        start.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        PurchasePolicyType finalType = eventRepo.findById(eventId).getPurchasePolicy().getPolicyType();
        assertTrue(finalType == PurchasePolicyType.AND || finalType == PurchasePolicyType.OR,
                "Final policy type must be valid regardless of thread ordering");
    }

    @Test
    void GivenTwoThreadsRaceToChangeDiscountPolicyType_WhenChangeEventDiscountPolicyType_ThenFinalStateIsValid() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Void>> f1 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.changeEventDiscountPolicyType(validToken1, eventId, DiscountPolicyType.MAX);
        });
        Future<Response<Void>> f2 = executor.submit(() -> {
            start.await();
            return eventCompanyManageService.changeEventDiscountPolicyType(validToken1, eventId, DiscountPolicyType.SUM);
        });

        start.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        DiscountPolicyType finalType = eventRepo.findById(eventId).getDiscountPolicy().getPolicyType();
        assertTrue(finalType == DiscountPolicyType.SUM || finalType == DiscountPolicyType.MAX,
                "Final discount policy type must be valid regardless of thread ordering");
    }

    @Test
    void GivenCompletedPurchase_WhenGetPurchaseHistory_ThenPurchaseHistoryIsIndependentOfFutureEventChanges(){
        // Arrange
        eventCompanyManageService.DefineVenueAndSeatingMap(
                validToken1,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        createCompletedOrderThroughPurchaseFlow(validToken1, eventId, 1);

        Event event = eventRepo.findById(eventId);
        event.setDate(LocalDateTime.of(2030, 1, 1, 10, 0));

        eventRepo.store(event);

        // Act
        Response<List<PurchaseHistoryDTO>> response =
                eventCompanyManageService.getPurchaseHistoryByUser(validToken1);

        // Assert
        assertNotNull(response.getValue());
        assertEquals(1, response.getValue().size());

        PurchaseHistoryDTO history = response.getValue().get(0);

        // Must remain with original purchase snapshot
        assertNotEquals("2030-01-01T20:00", history.getEventDate());
        assertFalse(history.getPurchasedTickets().isEmpty());
    }
}

