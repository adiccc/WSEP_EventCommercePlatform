package application;

import DTO.*;
import Log.LoggerSetup;

import java.util.*;

import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dto.EventMapDTO;
import domain.dto.SeatingTicketDTO;
import domain.dto.UserDTO;
import domain.user.IUserRepo;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static domain.config.PurchaseConfig.MAX_ACTIVE_ORDERS_PER_EVENT;
import static org.junit.jupiter.api.Assertions.*;

import org.mockito.Mockito;

class ActiveOrderServiceTest {

    private ActiveOrderService service;
    private IAuth auth;
    private EventRepoImpl eventRepo;
    private ActiveOrderRepoImpl activeOrderRepo;
    private CompanyRepoImpl companyRepo;
    private LotteryRepoImpl lotteryRepo;
    private EventCompanyManageService companyEventService;
    private IPaymentSystem paymentSystem;
    private ITicketSupply ticketSupply;

    private int userId1;
    private String validToken;
    private Integer eventId;
    private Integer concurrentEventId;

    private TokenService tokenService;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private UserService userService;

    private ElementPositionDTO stage;
    private List<ElementPositionDTO> entries;
    private List<StandingZoneDTO> standingZones;
    private List<SeatingZoneDTO> seatingZones;

    private final int companyId = 1;
    private final int capacity = 20;

    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
        tokenService = new TokenService();
        userRepo = new UserRepo();
        passwordEncoder = new PasswordEncoderUtil();
        auth = new Auth(tokenService, userRepo, passwordEncoder);

        userService = new UserService(tokenService, auth, userRepo, passwordEncoder);

        userService.registerUser(
                "",
                new UserDTO(
                        "testuser1@gmail.com",
                        "yarin",
                        "shemer",
                        "yy",
                        15, 4, 2002,
                        "Omer",
                        "050-427-3201"
                )
        );

        validToken = userService.login("testuser1@gmail.com", "yy").getValue();

        eventRepo = new EventRepoImpl();
        activeOrderRepo = new ActiveOrderRepoImpl();
        companyRepo = new CompanyRepoImpl();
        lotteryRepo = new LotteryRepoImpl();

        paymentSystem = Mockito.mock(IPaymentSystem.class);
        ticketSupply = Mockito.mock(ITicketSupply.class);

        CompanyService companyService = new CompanyService(auth, companyRepo, userRepo);
        companyService.createProductionCompany(validToken, companyId,
                "test-company", "testC@company.com", "054-5556677", "leumi");

        companyEventService = new EventCompanyManageService(companyRepo, eventRepo, auth, paymentSystem);

        Response<Integer> r = companyEventService.createEvent(
                validToken,
                companyId,
                LocalDateTime.now().plusDays(5),
                "Test Event",
                LocalDateTime.now().plusHours(2),     //saleStartDate is in the future
                true,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        Response<Integer> eventResponse = companyEventService.createEvent(
                validToken,
                companyId,
                LocalDateTime.now().plusDays(5),
                "Concurrent Event",
                LocalDateTime.now().minusMinutes(10),     // saleStartDate already started
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        concurrentEventId = eventResponse.getValue();

        eventId = r.getValue();

        stage = new ElementPositionDTO(10, 20);
        entries = List.of(new ElementPositionDTO(0, 0), new ElementPositionDTO(50, 10));
        standingZones = List.of(new StandingZoneDTO(200, "floor", 100.0, new ElementPositionDTO(1, 1)));
        seatingZones = List.of(new SeatingZoneDTO(10, 20, "tribune", 150.0, new ElementPositionDTO(5, 5)));

        companyEventService.DefineVenueAndSeatingMap(validToken, eventId, stage, entries, standingZones, seatingZones);
        companyEventService.DefineVenueAndSeatingMap(validToken, concurrentEventId, stage, entries, standingZones, seatingZones);

        LotteryService lotteryService = new LotteryService(lotteryRepo, eventRepo, auth);
        lotteryService.createLottery(validToken, eventId, 10,
                LocalDateTime.now().plusHours(1),     //registerWindow
                5);

        service = new ActiveOrderService(
                auth,
                activeOrderRepo,
                eventRepo,
                companyRepo,
                lotteryRepo,
                paymentSystem,
                ticketSupply,
                capacity,10
        );
    }
    @Test
    void GivenInvalidToken_WhenEnterPurchase_ThenErrorReturned() {
        Response<EventMapDTO> response = service.enterEventPurchase("", companyId, eventId);

        assertNull(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenNonExistingEvent_WhenEnterPurchase_ThenEventNotFound() {
        Response<EventMapDTO> response = service.enterEventPurchase(validToken, companyId, -1);

        assertNull(response.getValue());
        assertEquals("Event not found", response.getMessage());
    }

    @Test
    void GivenWrongCompany_WhenEnterPurchase_ThenMismatchError() {
        Response<EventMapDTO> response = service.enterEventPurchase(validToken, 999, eventId);

        assertNull(response.getValue());
        assertEquals("The selected event does not belong to the company", response.getMessage());
    }

    @Test
    void GivenFutureSaleWithoutLottery_WhenEnterPurchase_ThenSaleNotStarted() {
        Response<EventMapDTO> response = service.enterEventPurchase(validToken, companyId, eventId);

        assertNull(response.getValue());
        assertEquals("The sale for this event has not started yet", response.getMessage());
    }

    @Test
    void GivenValidEvent_WhenEnterPurchase_ThenReturnEventMap() {
        Response<EventMapDTO> response =
                service.enterEventPurchase(validToken, companyId, concurrentEventId);

        assertNotNull(response.getValue());
        assertEquals("Event map retrieved successfully", response.getMessage());
    }

    @Test
    void GivenConcurrentUsersExactlyAtCapacity_WhenEnterPurchase_ThenAllReceiveMap() throws Exception {
        int usersCount = capacity;

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < usersCount; i++) {
            String email = "A" + i + "@mail.com";

            userService.registerUser("", new UserDTO(
                    email, "f" + i, "l" + i, "pass",
                    1, 1, 2000, "Israel", "050-427-3201"
            ));

            tokens.add(userService.login(email, "pass").getValue());
        }

        ExecutorService executor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<EventMapDTO>>> futures = new ArrayList<>();

        for (String token : tokens) {
            futures.add(executor.submit(() -> {
                start.await();
                return service.enterEventPurchase(token, companyId, concurrentEventId);
            }));
        }

        start.countDown();

        int success = 0;
        int queued = 0;

        for (Future<Response<EventMapDTO>> future : futures) {
            Response<EventMapDTO> response = future.get();
            if (response.getValue() != null) success++;
            if ("Event is full, user added to waiting queue".equals(response.getMessage())) queued++;
        }

        executor.shutdown();

        assertEquals(usersCount, success);
        assertEquals(0, queued);
    }


    @Test
    void GivenConcurrentUsersAboveCapacity_WhenEnterPurchase_ThenOnlyCapacityReceiveMapAndRestQueued() throws Exception {
        int overflow = 5;
        int usersCount = capacity + overflow; // 25 total, only 20 can get in

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < usersCount; i++) {
            String email = "B" + i + "@mail.com";

            userService.registerUser("", new UserDTO(
                    email, "f" + i, "l" + i, "pass",
                    1, 1, 2000, "Israel", "050-200-1111"
            ));

            tokens.add(userService.login(email, "pass").getValue());
        }

        ExecutorService executor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<EventMapDTO>>> futures = new ArrayList<>();

        for (String token : tokens) {
            futures.add(executor.submit(() -> {
                start.await();
                return service.enterEventPurchase(token, companyId, concurrentEventId);
            }));
        }

        start.countDown();

        int success = 0;
        int queued = 0;

        for (Future<Response<EventMapDTO>> future : futures) {
            Response<EventMapDTO> response = future.get();
            if (response.getValue() != null) success++;
            if (response.getMessage() != null && response.getMessage().startsWith("Event is full")) queued++;
        }

        executor.shutdown();

        assertEquals(capacity, success, "Exactly capacity users should receive the event map");
        assertEquals(overflow, queued, "All overflow users should be added to the waiting queue");
        assertEquals(usersCount, success + queued, "Every request must result in either a map or a queue position");
    }


    //two users race to claim simultaneously. Exactly one user receives the event map.
    @Test
    void GivenOneSlotRemaining_WhenEnterPurchase__ThenOnlyOneReceivesMap() throws Exception {
        //  Fill capacity - leave exactly one open
        for (int i = 0; i < capacity - 1; i++) {
            String email = "C" + i + "@mail.com";
            userService.registerUser("", new UserDTO(
                    email, "f" + i, "l" + i, "pass",
                    1, 1, 2000, "Israel", "050-600-9999"
            ));
            String fillerToken = userService.login(email, "pass").getValue();

            Response<EventMapDTO> fillerResp =
                    service.enterEventPurchase(fillerToken, companyId, concurrentEventId);
            assertNotNull(fillerResp.getValue(),
                    "Filler user " + i + " should have received the map (slot available)");
        }

        String emailA = "racer_a@mail.com";
        String emailB = "racer_b@mail.com";


        userService.registerUser("", new UserDTO(
                emailA, "racer", "A", "pass",
                1, 1, 2000, "Israel", "050-700-0001"
        ));
        userService.registerUser("", new UserDTO(
                emailB, "racer", "B", "pass",
                1, 1, 2000, "Israel", "050-700-0002"
        ));

        String tokenA = userService.login(emailA, "pass").getValue();
        String tokenB = userService.login(emailB, "pass").getValue();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<EventMapDTO>> futureA = executor.submit(() -> {
            start.await();
            return service.enterEventPurchase(tokenA, companyId, concurrentEventId);
        });
        Future<Response<EventMapDTO>> futureB = executor.submit(() -> {
            start.await();
            return service.enterEventPurchase(tokenB, companyId, concurrentEventId);
        });

        start.countDown();

        Response<EventMapDTO> responseA = futureA.get();
        Response<EventMapDTO> responseB = futureB.get();
        executor.shutdown();

        int success = 0;
        int queued  = 0;

        if (responseA.getValue() != null) success++; else queued++;
        if (responseB.getValue() != null) success++; else queued++;

        // The critical race-condition assertion: two threads must never both win the last slot
        assertEquals(1, success);
        assertEquals(1, queued);

        // Confirm the loser has a real queue position (not an error response)
        Response<EventMapDTO> loser = (responseA.getValue() == null) ? responseA : responseB;
        assertTrue(loser.getMessage().startsWith("Event is full"),
                "Losing racer should receive a queue confirmation, got: " + loser.getMessage());
    }
    @Test
    void GivenNullTicketSupplyRequest_WhenIssueTickets_ThenInvalidRequestReturned() {
        Response<TicketSupplyResultDTO> response = service.issueTickets(null);


        assertNull(response.getValue());
        assertEquals("Invalid ticket supply request", response.getMessage());

        Mockito.verify(ticketSupply, Mockito.never())
                .issue(Mockito.any());
    }

    @Test
    void GivenValidTicketSupplyRequest_WhenIssueTicketsAndExternalServiceApproves_ThenTicketsIssuedSuccessfully() {
        TicketSupplyRequestDTO request = Mockito.mock(TicketSupplyRequestDTO.class);
        TicketSupplyResultDTO result = Mockito.mock(TicketSupplyResultDTO.class);

        Mockito.when(result.isSuccess()).thenReturn(true);
        Mockito.when(ticketSupply.issue(request)).thenReturn(result);

        Response<TicketSupplyResultDTO> response = service.issueTickets(request);

        assertNotNull(response.getValue());
        assertEquals(result, response.getValue());
        assertEquals("Tickets issued successfully", response.getMessage());

        Mockito.verify(ticketSupply).issue(request);
    }

    @Test
    void GivenValidTicketSupplyRequest_WhenIssueTicketsAndExternalServiceRejects_ThenTicketIssuanceFailed() {
        TicketSupplyRequestDTO request = Mockito.mock(TicketSupplyRequestDTO.class);
        TicketSupplyResultDTO result = Mockito.mock(TicketSupplyResultDTO.class);

        Mockito.when(result.isSuccess()).thenReturn(false);
        Mockito.when(ticketSupply.issue(request)).thenReturn(result);

        Response<TicketSupplyResultDTO> response = service.issueTickets(request);

        assertEquals(result, response.getValue());
        assertEquals("Ticket issuance failed", response.getMessage());

        Mockito.verify(ticketSupply).issue(request);
    }

    @Test
    void GivenValidTicketSupplyRequest_WhenIssueTicketsAndExternalServiceReturnsNull_ThenTicketIssuanceFailed() {
        TicketSupplyRequestDTO request = Mockito.mock(TicketSupplyRequestDTO.class);

        Mockito.when(ticketSupply.issue(request)).thenReturn(null);

        Response<TicketSupplyResultDTO> response = service.issueTickets(request);

        assertNull(response.getValue());
        assertEquals("Ticket issuance failed", response.getMessage());

        Mockito.verify(ticketSupply).issue(request);
    }
    @Test
    void GivenValidTicketSupplyRequest_WhenExternalServiceThrowsException_ThenTicketIssuanceFailed() {
        TicketSupplyRequestDTO request = Mockito.mock(TicketSupplyRequestDTO.class);

        Mockito.when(ticketSupply.issue(request))
                .thenThrow(new RuntimeException("Service unavailable"));

        Response<TicketSupplyResultDTO> response = service.issueTickets(request);

        assertNull(response.getValue());
        assertEquals("Ticket issuance failed", response.getMessage());

        Mockito.verify(ticketSupply).issue(request);
    }
    @Test
    void GivenValidTicketSupplyRequest_WhenIssueTicketsAndExternalServiceUnavailable_ThenTicketIssuanceFailed() {
        TicketSupplyRequestDTO request = Mockito.mock(TicketSupplyRequestDTO.class);

        Mockito.when(ticketSupply.issue(request))
                .thenThrow(new RuntimeException("Ticket supply service unavailable"));

        Response<TicketSupplyResultDTO> response = service.issueTickets(request);

        assertNull(response.getValue());
        assertEquals("Ticket issuance failed", response.getMessage());

        Mockito.verify(ticketSupply).issue(request);
    }
    @Test
    void GivenNonExistingEvent_WhenUserSelectTickets_ThenEventNotFound() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 1);

        Response<Integer> response = service.userSelectTickets(validToken, -1, seating, standing);

        assertNull(response.getValue());
        assertEquals("Event not found", response.getMessage());
    }

    @Test
    void GivenValidStandingRequest_WhenUserSelectTickets_ThenOrderIdReturned() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 3);

        Response<Integer> response = service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNotNull(response.getValue());
        assertEquals("Tickets selected successfully", response.getMessage());
    }

    @Test
    void GivenStandingQuantityAboveZoneCapacity_WhenUserSelectTickets_ThenFailureReturned() {
        // "floor" zone capacity is 200
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 201);

        Response<Integer> response = service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNull(response.getValue());
        assertNotNull(response.getMessage());
    }

    @Test
    void GivenNonExistentStandingZoneName_WhenUserSelectTickets_ThenFailureReturned() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("no-such-zone", 1);

        Response<Integer> response = service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNull(response.getValue());
    }

    @Test
    void GivenConcurrentRequestsWithinStandingCapacity_WhenUserSelectTickets_ThenAllSucceedWithUniqueOrderIds() throws Exception {
        // floor capacity = 200; 10 users x 20 tickets = 200 (exact fit)
        int usersCount = 10;
        int ticketsPerUser = 20;

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < usersCount; i++) {
            String email = "G" + i + "@mail.com";
            userService.registerUser("", new UserDTO(
                    email, "f" + i, "l" + i, "pass",
                    1, 1, 2000, "Israel", "050-111-2222"
            ));
            tokens.add(userService.login(email, "pass").getValue());
        }

        ExecutorService executor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<Integer>>> futures = new ArrayList<>();

        for (String t : tokens) {
            futures.add(executor.submit(() -> {
                start.await();
                Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
                Map<String, Integer> standing = Map.of("floor", ticketsPerUser);
                return service.userSelectTickets(t, concurrentEventId, seating, standing);
            }));
        }

        start.countDown();

        List<Integer> orderIds = new ArrayList<>();
        int success = 0;
        for (Future<Response<Integer>> f : futures) {
            Response<Integer> r = f.get();
            if (r.getValue() != null) {
                success++;
                orderIds.add(r.getValue());
            }
        }
        executor.shutdown();

        assertEquals(usersCount, success, "All users should fit within standing capacity");
        assertEquals(orderIds.size(), orderIds.stream().distinct().count(),
                "Every active order must have a unique id");
    }

    @Test
    void GivenConcurrentRequestsAboveStandingCapacity_WhenUserSelectTickets_ThenOnlyFittingUsersSucceed() throws Exception {
        // floor capacity = 200; 11 users x 20 tickets = 220 → only 10 should fit
        int usersCount = 11;
        int ticketsPerUser = 20;

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < usersCount; i++) {
            String email = "H" + i + "@mail.com";
            userService.registerUser("", new UserDTO(
                    email, "f" + i, "l" + i, "pass",
                    1, 1, 2000, "Israel", "050-333-4444"
            ));
            tokens.add(userService.login(email, "pass").getValue());
        }

        ExecutorService executor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<Integer>>> futures = new ArrayList<>();

        for (String t : tokens) {
            futures.add(executor.submit(() -> {
                start.await();
                Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
                Map<String, Integer> standing = Map.of("floor", ticketsPerUser);
                return service.userSelectTickets(t, concurrentEventId, seating, standing);
            }));
        }

        start.countDown();

        int success = 0;
        int failed = 0;
        for (Future<Response<Integer>> f : futures) {
            Response<Integer> r = f.get();
            if (r.getValue() != null) success++;
            else failed++;
        }
        executor.shutdown();

        assertEquals(10, success, "Only 10 users (200/20) should fit in standing capacity");
        assertEquals(1, failed, "1 user must be rejected when capacity is exhausted");
    }

    private ActiveOrderService buildServiceWithExpireMinutes(int expireMinutes) {
        return new ActiveOrderService(
                auth,
                activeOrderRepo,
                eventRepo,
                companyRepo,
                lotteryRepo,
                paymentSystem,
                ticketSupply,
                capacity,
                expireMinutes
        );
    }

    @Test
    void GivenNoActiveOrders_WhenCleanupExpiredOrders_ThenRepoRemainsEmpty() {
        service.cleanupExpiredOrders();

        assertTrue(activeOrderRepo.getAll().isEmpty(),
                "Cleanup on an empty repo must not create or fail anything");
    }

    @Test
    void GivenOnlyNonExpiredOrders_WhenCleanupExpiredOrders_ThenAllOrdersRemain() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 5);

        Response<Integer> r = service.userSelectTickets(validToken, concurrentEventId, seating, standing);
        assertNotNull(r.getValue());

        service.cleanupExpiredOrders();

        assertEquals(1, activeOrderRepo.getAll().size(),
                "Non-expired orders must not be removed by cleanup");
    }

    @Test
    void GivenSingleExpiredOrder_WhenCleanupExpiredOrders_ThenOrderRemovedAndTicketsReleased() {
        ActiveOrderService expiredService = buildServiceWithExpireMinutes(-1);

        Response<Integer> initial = expiredService.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 20));
        assertNotNull(initial.getValue(), "Booking failed: " + initial.getMessage());
        int orderId = initial.getValue();

        service.cleanupExpiredOrders();

        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(orderId),
                "Expired order must be deleted from the repo");

        String email = "released_user@mail.com";
        userService.registerUser("", new UserDTO(
                email, "released", "user", "pass",
                1, 1, 2000, "Israel", "050-999-8888"
        ));
        String newToken = userService.login(email, "pass").getValue();

        Response<Integer> rebook = service.userSelectTickets(
                newToken, concurrentEventId, new HashMap<>(), Map.of("floor", 20));
        assertNotNull(rebook.getValue(),
                "Released tickets must become available again: " + rebook.getMessage());
    }


    // todo: fix test
    @Test
    void GivenMixedExpiredAndActiveOrders_WhenCleanupExpiredOrders_ThenOnlyExpiredAreRemoved() {
        ActiveOrderService expiredService = buildServiceWithExpireMinutes(-1);

        String emailA = "mix_a@mail.com";
        String emailB = "mix_b@mail.com";
        userService.registerUser("", new UserDTO(emailA, "a", "a", "pass", 1, 1, 2000, "Israel", "050-100-2000"));
        userService.registerUser("", new UserDTO(emailB, "b", "b", "pass", 1, 1, 2000, "Israel", "050-100-2001"));
        String tokenA = userService.login(emailA, "pass").getValue();
        String tokenB = userService.login(emailB, "pass").getValue();

        Response<Integer> respA = expiredService.userSelectTickets(
                tokenA, concurrentEventId, new HashMap<>(), Map.of("floor", 5));
        System.out.println("[A] orderA value = " + respA.getValue() + " | msg = " + respA.getMessage());
        assertNotNull(respA.getValue(), "Booking A failed: " + respA.getMessage());
        int orderA = respA.getValue();

        Response<Integer> respB = service.userSelectTickets(
                tokenB, concurrentEventId, new HashMap<>(), Map.of("floor", 5));
        System.out.println("[B] orderB value = " + respB.getValue() + " | msg = " + respB.getMessage());
        assertNotNull(respB.getValue(), "Booking B failed: " + respB.getMessage());
        int orderB = respB.getValue();

        System.out.println("[C] orders before cleanup = " + activeOrderRepo.getAll().size());

        service.cleanupExpiredOrders();

        System.out.println("[D] orders after cleanup = " + activeOrderRepo.getAll().size());

        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(orderA),
                "Expired order A must be removed");

        assertNotNull(activeOrderRepo.findById(orderB),
                "Non-expired order B must remain after cleanup");
    }

    @Test
    void GivenMultipleExpiredOrders_WhenCleanupExpiredOrders_ThenAllExpiredAreRemoved() {
        ActiveOrderService expiredService = buildServiceWithExpireMinutes(-1);

        int users = 5;
        List<Integer> orderIds = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            String email = "many_" + i + "@mail.com";
            userService.registerUser("", new UserDTO(
                    email, "f" + i, "l" + i, "pass",
                    1, 1, 2000, "Israel", "050-300-4000"));
            String token = userService.login(email, "pass").getValue();
            int id = expiredService.userSelectTickets(
                    token, concurrentEventId, new HashMap<>(), Map.of("floor", 10)).getValue();
            orderIds.add(id);
        }

        service.cleanupExpiredOrders();

        assertTrue(activeOrderRepo.getAll().isEmpty(),
                "All expired orders must be removed in a single sweep");
    }

    @Test
    void GivenExpiredOrderWithSeatingTickets_WhenCleanupExpiredOrders_ThenSeatingTicketsReleased() {
        ActiveOrderService expiredService = buildServiceWithExpireMinutes(-1);

        SeatingTicketDTO seat = new SeatingTicketDTO(0, 0);
        Map<String, List<SeatingTicketDTO>> seating = Map.of("tribune", List.of(seat));
        Map<String, Integer> standing = new HashMap<>();

        Response<Integer> initial = expiredService.userSelectTickets(
                validToken, concurrentEventId, seating, standing);
        assertNotNull(initial.getValue());
        int orderId = initial.getValue();

        service.cleanupExpiredOrders();

        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(orderId),
                "Expired seating order must be deleted");

        String email = "seat_taker@mail.com";
        userService.registerUser("", new UserDTO(
                email, "seat", "taker", "pass",
                1, 1, 2000, "Israel", "050-444-5555"
        ));
        String newToken = userService.login(email, "pass").getValue();

        Response<Integer> rebook = service.userSelectTickets(
                newToken, concurrentEventId,
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0))),
                new HashMap<>());
        assertNotNull(rebook.getValue(),
                "Released seat must be selectable by another user");
    }

    @Test
    void GivenExpiredAndNonExpiredOrdersForSameUser_WhenCleanupExpiredOrders_ThenUserCanCreateNewOrder() {
        ActiveOrderService expiredService = buildServiceWithExpireMinutes(-1);

        Response<Integer> first = expiredService.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5));
        assertNotNull(first.getValue());

        service.cleanupExpiredOrders();

        Response<Integer> second = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5));
        assertNotNull(second.getValue(),
                "After cleanup removed the expired order, same user must be able to create a new order");
    }

}
