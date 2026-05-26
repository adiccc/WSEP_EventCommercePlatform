package application;

import DTO.*;
import Log.LoggerSetup;

import java.util.*;

import domain.Suspension.ISuspensionRepo;
import domain.activeOrder.ActiveOrder;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dataType.TicketStatus;
import domain.dataType.PermissionType;
import domain.dto.ActiveOrderDTO;
import domain.dto.SeatingTicketDTO;
import domain.dto.UserDTO;
import domain.event.Event;
import domain.event.Order;
import domain.event.OrderStatus;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.webQueue.WebQueue;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.vaadin.flow.shared.Registration;

import static org.junit.jupiter.api.Assertions.*;

import org.mockito.Mockito;

class ActiveOrderServiceTest {

    private ActiveOrderService service;
    private IAuth auth;
    private EventRepoImpl eventRepo;
    private ActiveOrderRepoImpl activeOrderRepo;
    private IPaymentSystem paymentSystem;
    private ITicketSupply ticketSupply;
    private INotifier notifier;
    private IUserRepo userRepo;
    private CompanyService companyService;
    private EventCompanyManageService companyEventService;

    private String validToken;
    private Integer eventId;
    private Integer concurrentEventId;

    private UserService userService;

    private final int companyId = 1;
    private final int capacity = 20;

    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
        TokenService tokenService = new TokenService();
        userRepo = new UserRepo();
        IPasswordEncoder passwordEncoder = new PasswordEncoderUtil();
        ISuspensionRepo suspensionRepo = new SuspensionRepoImpl();
        IAccessValidator accessValidator=new AccessValidator(suspensionRepo);
        auth = new Auth(tokenService, userRepo, passwordEncoder);
        notifier = Mockito.spy(new VaadinNotifier(userRepo));
        userService = new UserService(tokenService, auth, userRepo, passwordEncoder,notifier);

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
        CompanyRepoImpl companyRepo = new CompanyRepoImpl();
        LotteryRepoImpl lotteryRepo = new LotteryRepoImpl();

        paymentSystem = Mockito.mock(IPaymentSystem.class);
        ticketSupply = Mockito.mock(ITicketSupply.class);

        companyService = new CompanyService(auth, companyRepo, userRepo,accessValidator);
        companyService.createProductionCompany(validToken, companyId,
                "test-company", "testC@company.com", "054-5556677", "leumi");

        companyEventService = new EventCompanyManageService(companyRepo, eventRepo, auth, paymentSystem,accessValidator,notifier);

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

        ElementPositionDTO stage = new ElementPositionDTO(10, 20);
        List<ElementPositionDTO> entries = List.of(new ElementPositionDTO(0, 0), new ElementPositionDTO(50, 10));
        List<StandingZoneDTO> standingZones = List.of(new StandingZoneDTO(200, "floor", 100.0, new ElementPositionDTO(1, 1)));
        List<SeatingZoneDTO> seatingZones = List.of(new SeatingZoneDTO(10, 20, "tribune", 150.0, new ElementPositionDTO(5, 5)));

        companyEventService.DefineVenueAndSeatingMap(validToken, eventId, stage, entries, standingZones, seatingZones);
        companyEventService.DefineVenueAndSeatingMap(validToken, concurrentEventId, stage, entries, standingZones, seatingZones);

        LotteryService lotteryService = new LotteryService(lotteryRepo, eventRepo, auth, companyRepo,accessValidator);
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
                accessValidator,
                notifier,
                capacity
        );
    }
    @Test
    void GivenInvalidToken_WhenEnterPurchase_ThenErrorReturned() {
        Response<EnterPurchaseDTO> response = service.enterEventPurchase("", companyId, eventId);

        assertNull(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenNonExistingEvent_WhenEnterPurchase_ThenEventNotFound() {
        Response<EnterPurchaseDTO> response = service.enterEventPurchase(validToken, companyId, -1);

        assertNull(response.getValue());
        assertEquals("Event not found", response.getMessage());
    }

    @Test
    void GivenWrongCompany_WhenEnterPurchase_ThenMismatchError() {
        Response<EnterPurchaseDTO> response = service.enterEventPurchase(validToken, 999, eventId);

        assertNull(response.getValue());
        assertEquals("The selected event does not belong to the company", response.getMessage());
    }

    @Test
    void GivenFutureSaleWithoutLottery_WhenEnterPurchase_ThenSaleNotStarted() {
        Response<EnterPurchaseDTO> response = service.enterEventPurchase(validToken, companyId, eventId);

        assertNull(response.getValue());
        assertEquals("The sale for this event has not started yet", response.getMessage());
    }

    @Test
    void GivenValidEvent_WhenEnterPurchase_ThenReturnEventMap() {
        Response<EnterPurchaseDTO> response =
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
        List<Future<Response<EnterPurchaseDTO>>> futures = new ArrayList<>();

        for (String token : tokens) {
            futures.add(executor.submit(() -> {
                start.await();
                return service.enterEventPurchase(token, companyId, concurrentEventId);
            }));
        }

        start.countDown();

        int success = 0;
        int queued = 0;

        for (Future<Response<EnterPurchaseDTO>> future : futures) {
            Response<EnterPurchaseDTO> response = future.get();
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
        List<Future<Response<EnterPurchaseDTO>>> futures = new ArrayList<>();

        for (String token : tokens) {
            futures.add(executor.submit(() -> {
                start.await();
                return service.enterEventPurchase(token, companyId, concurrentEventId);
            }));
        }

        start.countDown();

        int success = 0;
        int queued = 0;

        for (Future<Response<EnterPurchaseDTO>> future : futures) {
            Response<EnterPurchaseDTO> response = future.get();
            EnterPurchaseDTO dto = response.getValue();

            if (dto != null && !dto.isWaitingInQueue()) {
                success++;
            }

            if (dto != null && dto.isWaitingInQueue()) {
                queued++;
            }
        }

        executor.shutdown();
        assertEquals(activeOrderRepo.countActiveOrdersForEvent(concurrentEventId), capacity,
                "Active orders in repo should match successful map retrievals");
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

            Response<EnterPurchaseDTO> fillerResp =
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

        Future<Response<EnterPurchaseDTO>> futureA = executor.submit(() -> {
            start.await();
            return service.enterEventPurchase(tokenA, companyId, concurrentEventId);
        });
        Future<Response<EnterPurchaseDTO>> futureB = executor.submit(() -> {
            start.await();
            return service.enterEventPurchase(tokenB, companyId, concurrentEventId);
        });

        start.countDown();

        Response<EnterPurchaseDTO> responseA = futureA.get();
        Response<EnterPurchaseDTO> responseB = futureB.get();
        executor.shutdown();

        int success = 0;
        int queued = 0;

        EnterPurchaseDTO dtoA = responseA.getValue();
        EnterPurchaseDTO dtoB = responseB.getValue();

        if (dtoA != null && dtoA.isWaitingInQueue()) queued++;
        else if (dtoA != null) success++;

        if (dtoB != null && dtoB.isWaitingInQueue()) queued++;
        else if (dtoB != null) success++;

        // The critical race-condition assertion: two threads must never both win the last slot
        assertEquals(1, success);
        assertEquals(1, queued);

        // Confirm the loser has a real queue position (not an error response)
        Response<EnterPurchaseDTO> loser = responseA.getValue().isWaitingInQueue() ? responseA : responseB;
        assertTrue(loser.getValue().isWaitingInQueue());
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
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        Response<Integer> response = service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNotNull(response.getValue());
        assertEquals("Tickets selected successfully", response.getMessage());
    }

    @Test
    void GivenExpiredTimeViewingMap_WhenUserSelectTickets_ThenFailureReturned() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 3);
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        String userEmail = auth.getUserEmail(validToken).getValue();
        int orderId = activeOrderRepo.findOrderByUserId(userEmail).getId();
        forceExpireOrder(orderId);

        service.cleanupExpiredOrders();
        Response<Integer> response = service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNull(response.getValue());
        assertEquals("Active order not found for user", response.getMessage());
    }

    @Test
    void GivenStandingQuantityAboveZoneCapacity_WhenUserSelectTickets_ThenFailureReturned() {
        // "floor" zone capacity is 200
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 201);
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        Response<Integer> response = service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNull(response.getValue());
        assertNotNull(response.getMessage());
    }

    @Test
    void GivenNonExistentStandingZoneName_WhenUserSelectTickets_ThenFailureReturned() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("no-such-zone", 1);
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
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
                service.enterEventPurchase(t, companyId, concurrentEventId);
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
                service.enterEventPurchase(t, companyId, concurrentEventId);
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
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        Response<Integer> r = service.userSelectTickets(validToken, concurrentEventId, seating, standing);
        assertNotNull(r.getValue());

        service.cleanupExpiredOrders();

        assertEquals(1, activeOrderRepo.getAll().size(),
                "Non-expired orders must not be removed by cleanup");
    }

    @Test
    void GivenSingleExpiredOrder_WhenCleanupExpiredOrders_ThenOrderRemovedAndTicketsReleased() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        Response<Integer> initial = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 20));
        assertNotNull(initial.getValue(), "Booking failed: " + initial.getMessage());
        int orderId = initial.getValue();
        forceExpireOrder(orderId);
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
        service.enterEventPurchase(newToken, companyId, concurrentEventId);
        Response<Integer> rebook = service.userSelectTickets(
                newToken, concurrentEventId, new HashMap<>(), Map.of("floor", 20));
        assertNotNull(rebook.getValue(),
                "Released tickets must become available again: " + rebook.getMessage());
    }


    private void forceExpireOrder(int orderId) {
        ActiveOrder order = activeOrderRepo.findById(orderId);
        order.forceExpireForTest(LocalDateTime.now());
        activeOrderRepo.store(order);
    }

    @Test
    void GivenMixedExpiredAndActiveOrders_WhenCleanupExpiredOrders_ThenOnlyExpiredAreRemoved() throws Exception {
        String emailA = "mix_a@mail.com";
        String emailB = "mix_b@mail.com";
        userService.registerUser("", new UserDTO(emailA, "a", "a", "pass", 1, 1, 2000, "Israel", "050-100-2000"));
        userService.registerUser("", new UserDTO(emailB, "b", "b", "pass", 1, 1, 2000, "Israel", "050-100-2001"));
        String tokenA = userService.login(emailA, "pass").getValue();
        String tokenB = userService.login(emailB, "pass").getValue();
        service.enterEventPurchase(tokenA, companyId, concurrentEventId);
        int orderA = service.userSelectTickets(
                tokenA, concurrentEventId, new HashMap<>(), Map.of("floor", 5)).getValue();
        forceExpireOrder(orderA);   // only A is "expired"
        service.enterEventPurchase(tokenB, companyId, concurrentEventId); //also cleanup orderA
        int orderB = service.userSelectTickets(
                tokenB, concurrentEventId, new HashMap<>(), Map.of("floor", 5)).getValue();

        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(orderA),
                "Expired order A must be removed");
        assertNotNull(activeOrderRepo.findById(orderB),
                "Non-expired order B must remain after cleanup");
    }

    @Test
    void GivenMultipleExpiredOrders_WhenCleanupExpiredOrders_ThenAllExpiredAreRemoved() {
        int users = 5;
        List<Integer> orderIds = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            String email = "many_" + i + "@mail.com";
            userService.registerUser("", new UserDTO(
                    email, "f" + i, "l" + i, "pass",
                    1, 1, 2000, "Israel", "050-300-4000"));
            String token = userService.login(email, "pass").getValue();
            service.enterEventPurchase(token, companyId, concurrentEventId);
            int id = service.userSelectTickets(
                    token, concurrentEventId, new HashMap<>(), Map.of("floor", 10)).getValue();
            orderIds.add(id);
            forceExpireOrder(id);
        }

        service.cleanupExpiredOrders();

        assertTrue(activeOrderRepo.getAll().isEmpty(),
                "All expired orders must be removed in a single sweep");
    }

    @Test
    void GivenExpiredOrderWithSeatingTickets_WhenCleanupExpiredOrders_ThenSeatingTicketsReleased() {
        SeatingTicketDTO seat = new SeatingTicketDTO(0, 0);
        Map<String, List<SeatingTicketDTO>> seating = Map.of("tribune", List.of(seat));
        Map<String, Integer> standing = new HashMap<>();

        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        Response<Integer> initial = service.userSelectTickets(
                validToken, concurrentEventId, seating, standing);
        assertNotNull(initial.getValue());
        int orderId = initial.getValue();
        forceExpireOrder(orderId);
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
        service.enterEventPurchase(newToken, companyId, concurrentEventId);
        Response<Integer> rebook = service.userSelectTickets(
                newToken, concurrentEventId,
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0))),
                new HashMap<>());
        assertNotNull(rebook.getValue(),
                "Released seat must be selectable by another user");
    }

    @Test
    void GivenExpiredAndNonExpiredOrdersForSameUser_WhenCleanupExpiredOrders_ThenUserCanCreateNewOrder() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        Response<Integer> first = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5));
        assertNotNull(first.getValue());

        service.cleanupExpiredOrders();
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        Response<Integer> second = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5));
        assertNotNull(second.getValue(),
                "After cleanup removed the expired order, same user must be able to create a new order");
    }


    @Test
    void GivenInvalidToken_WhenMemberProceedActiveOrder_ThenErrorReturned() {
        Response<ActiveOrderDTO> response = service.memberProceedAnActiveOrder("not-a-real-token");

        assertNull(response.getValue());
    }

    @Test
    void GivenNoActiveOrderForUser_WhenMemberProceedActiveOrder_ThenNotFound() {
        Response<ActiveOrderDTO> response = service.memberProceedAnActiveOrder(validToken);

        assertNull(response.getValue());
        assertEquals("Active order not found", response.getMessage());
    }

    @Test
    void GivenValidActiveOrder_WhenMemberProceedActiveOrder_ThenReturnsDTO() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        Response<Integer> created = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5));
        assertNotNull(created.getValue(), "setup failed: " + created.getMessage());
        int orderId = created.getValue();

        Response<ActiveOrderDTO> response = service.memberProceedAnActiveOrder(validToken);

        assertNotNull(response.getValue(), "expected DTO, got null. msg=" + response.getMessage());
        assertEquals(orderId, response.getValue().getId());
        assertEquals(concurrentEventId, response.getValue().getEventId());
        assertEquals("Active order retrieved successfully", response.getMessage());
    }

    @Test
    void GivenExpiredActiveOrder_WhenMemberProceedActiveOrder_ThenExpiredError() throws Exception {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        Response<Integer> created = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5));
        assertNotNull(created.getValue());

        forceExpireOrder(created.getValue());

        Response<ActiveOrderDTO> response = service.memberProceedAnActiveOrder(validToken);

        assertNull(response.getValue());
        assertEquals("Active order has expired", response.getMessage());
    }

    @Test
    void GivenTwoUsersEachWithOrder_WhenMemberProceedActiveOrder_ThenEachUserSeesOnlyTheirOwn() {
        String emailB = "isolation_b@mail.com";
        userService.registerUser("", new UserDTO(
                emailB, "iso", "b", "pass", 1, 1, 2000, "Israel", "050-111-2222"));
        String tokenB = userService.login(emailB, "pass").getValue();
        String userIdA = auth.getUserEmail(validToken).getValue();
        String userIdB = auth.getUserEmail(tokenB).getValue();
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderA = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5)).getValue();
        service.enterEventPurchase(tokenB, companyId, concurrentEventId);
        int orderB = service.userSelectTickets(
                tokenB, concurrentEventId, new HashMap<>(), Map.of("floor", 5)).getValue();

        Response<ActiveOrderDTO> respA = service.memberProceedAnActiveOrder(validToken);
        Response<ActiveOrderDTO> respB = service.memberProceedAnActiveOrder(tokenB);

        assertEquals(orderA, respA.getValue().getId());
        assertEquals(userIdA, respA.getValue().getUserIdentifier());
        assertEquals(orderB, respB.getValue().getId());
        assertEquals(userIdB, respB.getValue().getUserIdentifier());
        assertNotEquals(orderA, orderB);
    }

    @Test
    void GivenSingleUserWithOrder_WhenManyConcurrentProceedCalls_ThenAllReturnSameOrder() throws Exception {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5)).getValue();

        int threadCount = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<ActiveOrderDTO>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return service.memberProceedAnActiveOrder(validToken);
            }));
        }
        start.countDown();

        Set<Integer> seenOrderIds = new HashSet<>();
        int successes = 0;
        for (Future<Response<ActiveOrderDTO>> f : futures) {
            Response<ActiveOrderDTO> r = f.get();
            if (r.getValue() != null) {
                successes++;
                seenOrderIds.add(r.getValue().getId());
            }
        }
        pool.shutdown();

        assertEquals(threadCount, successes, "every concurrent read should succeed");
        assertEquals(Set.of(orderId), seenOrderIds, "all threads must see the same order");
    }

    @Test
    void GivenMultipleUsersWithOrders_WhenAllProceedConcurrently_ThenEachGetsOwnOrder() throws Exception {
        int usersCount = 10;
        List<String> tokens = new ArrayList<>();
        List<String> userIds = new ArrayList<>();
        Map<String, Integer> tokenToOrderId = new HashMap<>();

        for (int i = 0; i < usersCount; i++) {
            String email = "conc_proceed_" + i + "@mail.com";
            userService.registerUser("", new UserDTO(
                    email, "f" + i, "l" + i, "pass",
                    1, 1, 2000, "Israel", "050-555-6677"));
            String t = userService.login(email, "pass").getValue();
            tokens.add(t);
            userIds.add(auth.getUserEmail(t).getValue());
            service.enterEventPurchase(t, companyId, concurrentEventId);
            int oid = service.userSelectTickets(
                    t, concurrentEventId, new HashMap<>(), Map.of("floor", 2)).getValue();
            tokenToOrderId.put(t, oid);
        }

        ExecutorService pool = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        Map<String, Future<Response<ActiveOrderDTO>>> futures = new HashMap<>();

        for (String t : tokens) {
            futures.put(t, pool.submit(() -> {
                start.await();
                return service.memberProceedAnActiveOrder(t);
            }));
        }
        start.countDown();

        for (int i = 0; i < usersCount; i++) {
            String t = tokens.get(i);
            Response<ActiveOrderDTO> r = futures.get(t).get();
            assertNotNull(r.getValue(), "user " + i + " got null: " + r.getMessage());
            assertEquals(tokenToOrderId.get(t), r.getValue().getId(),
                    "user " + i + " saw a different order than their own");
            assertEquals(userIds.get(i), r.getValue().getUserIdentifier(),
                    "user " + i + " saw another user's userId — leakage between threads");
        }
        pool.shutdown();
    }


    @Test
    void GivenInvalidToken_WhenEditTicketSelection_ThenInvalidToken() {
        Response<ActiveOrderDTO> r = service.editTicketSelection(
                "garbage", new HashMap<>(), new HashMap<>(), new HashMap<>());
        assertNull(r.getValue());
        assertEquals("Invalid token", r.getMessage());
    }

    @Test
    void GivenNoActiveOrder_WhenEditTicketSelection_ThenOrderNotFound() {
        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken, new HashMap<>(), new HashMap<>(), new HashMap<>());
        assertNull(r.getValue());
        assertEquals("Order or event not found", r.getMessage());
    }

    @Test
    void GivenExpiredOrder_WhenEditTicketSelection_ThenExpiredError() throws Exception {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5)).getValue();
        forceExpireOrder(orderId);

        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken, new HashMap<>(), new HashMap<>(), Map.of("floor", 3));

        assertNull(r.getValue());
        assertEquals("Active order has expired", r.getMessage());
    }

    @Test
    void GivenStandingDesiredEqualToCurrent_WhenEditTicketSelection_ThenNoChange() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5)).getValue();
        int ticketCountBefore = activeOrderRepo.findById(orderId).getTickets().size();

        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken, new HashMap<>(), new HashMap<>(), Map.of("floor", 5));

        assertNotNull(r.getValue(), "msg=" + r.getMessage());
        assertEquals(ticketCountBefore, activeOrderRepo.findById(orderId).getTickets().size());
    }

    @Test
    void GivenStandingDesiredHigher_WhenEditTicketSelection_ThenMoreTicketsBooked() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5)).getValue();

        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken, new HashMap<>(), new HashMap<>(), Map.of("floor", 8));

        assertNotNull(r.getValue(), "msg=" + r.getMessage());
        assertEquals(8, activeOrderRepo.findById(orderId).getTickets().size());
    }

    @Test
    void GivenStandingDesiredLower_WhenEditTicketSelection_ThenExtrasReleased() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5)).getValue();

        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken, new HashMap<>(), new HashMap<>(), Map.of("floor", 2));

        assertNotNull(r.getValue(), "msg=" + r.getMessage());
        assertEquals(2, activeOrderRepo.findById(orderId).getTickets().size());

        String email = "leftover@mail.com";
        userService.registerUser("", new UserDTO(
                email, "x", "y", "pass", 1, 1, 2000, "Israel", "050-000-1111"));
        String otherToken = userService.login(email, "pass").getValue();
        service.enterEventPurchase(otherToken, companyId, concurrentEventId);
        Response<Integer> rebook = service.userSelectTickets(
                otherToken, concurrentEventId, new HashMap<>(), Map.of("floor", 3));
        assertNotNull(rebook.getValue(), "released standing tickets must be available again");
    }

    @Test
    void GivenSpecificSeatRemoved_WhenEditTicketSelection_ThenSeatReleasedAndRebookable() {
        SeatingTicketDTO seat = new SeatingTicketDTO(0, 0);
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId,
                Map.of("tribune", List.of(seat)), new HashMap<>()).getValue();

        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken,
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0))),
                new HashMap<>(),
                new HashMap<>());

        assertNotNull(r.getValue(), "msg=" + r.getMessage());
        assertEquals(0, activeOrderRepo.findById(orderId).getTickets().size());

        String email = "seatgrabber@mail.com";
        userService.registerUser("", new UserDTO(
                email, "s", "g", "pass", 1, 1, 2000, "Israel", "050-222-3333"));
        String otherToken = userService.login(email, "pass").getValue();
        service.enterEventPurchase(otherToken, companyId, concurrentEventId);
        Response<Integer> rebook = service.userSelectTickets(
                otherToken, concurrentEventId,
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0))),
                new HashMap<>());
        assertNotNull(rebook.getValue(), "released seat must be rebookable: " + rebook.getMessage());
    }

    @Test
    void GivenSpecificSeatAdded_WhenEditTicketSelection_ThenSeatBookedToOrder() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 1)).getValue();

        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken,
                new HashMap<>(),
                Map.of("tribune", List.of(new SeatingTicketDTO(2, 3))),
                new HashMap<>());

        assertNotNull(r.getValue(), "msg=" + r.getMessage());
        assertEquals(2, activeOrderRepo.findById(orderId).getTickets().size());

        String email = "blocked@mail.com";
        userService.registerUser("", new UserDTO(
                email, "b", "k", "pass", 1, 1, 2000, "Israel", "050-444-7777"));
        String otherToken = userService.login(email, "pass").getValue();
        service.enterEventPurchase(otherToken, companyId, concurrentEventId);
        Response<Integer> conflict = service.userSelectTickets(
                otherToken, concurrentEventId,
                Map.of("tribune", List.of(new SeatingTicketDTO(2, 3))),
                new HashMap<>());
        assertNull(conflict.getValue(), "seat (2,3) should already be locked by user 1");
    }

    @Test
    void GivenSwapSeats_WhenEditTicketSelection_ThenOldReleasedAndNewBooked() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId,
                Map.of("tribune", List.of(new SeatingTicketDTO(1, 1))),
                new HashMap<>()).getValue();

        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken,
                Map.of("tribune", List.of(new SeatingTicketDTO(1, 1))),
                Map.of("tribune", List.of(new SeatingTicketDTO(4, 4))),
                new HashMap<>());

        assertNotNull(r.getValue(), "msg=" + r.getMessage());
        assertEquals(1, activeOrderRepo.findById(orderId).getTickets().size());

        String email = "swap@mail.com";
        userService.registerUser("", new UserDTO(
                email, "s", "w", "pass", 1, 1, 2000, "Israel", "050-555-8888"));
        String otherToken = userService.login(email, "pass").getValue();
        service.enterEventPurchase(otherToken, companyId, concurrentEventId);
        Response<Integer> oldSeat = service.userSelectTickets(
                otherToken, concurrentEventId,
                Map.of("tribune", List.of(new SeatingTicketDTO(1, 1))),
                new HashMap<>());
        assertNotNull(oldSeat.getValue(), "old seat (1,1) should be free");
    }

    @Test
    void GivenSameSeatInRemoveAndAdd_WhenEditTicketSelection_ThenRejected() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId,
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0))),
                new HashMap<>()).getValue();

        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken,
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0))),
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0))),
                new HashMap<>());

        assertNull(r.getValue());
        assertTrue(r.getMessage().toLowerCase().contains("both"),
                "expected overlap rejection, got: " + r.getMessage());
    }

    @Test
    void GivenRemoveSeatNotInOrder_WhenEditTicketSelection_ThenError() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId,
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0))),
                new HashMap<>()).getValue();

        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken,
                Map.of("tribune", List.of(new SeatingTicketDTO(9, 9))), // not theirs
                new HashMap<>(),
                new HashMap<>());

        assertNull(r.getValue());
        assertTrue(r.getMessage().toLowerCase().contains("not in your order")
                        || r.getMessage().toLowerCase().contains("invalid"),
                "got: " + r.getMessage());
    }

    @Test
    void GivenNegativeStandingQuantity_WhenEditTicketSelection_ThenError() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5));

        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken, new HashMap<>(), new HashMap<>(), Map.of("floor", -1));

        assertNull(r.getValue());
        assertTrue(r.getMessage().toLowerCase().contains("negative"));
    }

    @Test
    void GivenEditFromCheckingOut_WhenEditTicketSelection_ThenStageReturnsToSelecting() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5)).getValue();

        ActiveOrder o = activeOrderRepo.findById(orderId);
        o.proceedToCheckout();
        activeOrderRepo.store(o);
        assertEquals(domain.activeOrder.STAGE.CHECKING_OUT,
                activeOrderRepo.findById(orderId).getStage());

        Response<ActiveOrderDTO> r = service.editTicketSelection(
                validToken, new HashMap<>(), new HashMap<>(), Map.of("floor", 6));

        assertNotNull(r.getValue(), "msg=" + r.getMessage());
        assertEquals(domain.activeOrder.STAGE.SELECTING_TICKETS,
                activeOrderRepo.findById(orderId).getStage());
    }

    @Test
    void GivenTwoUsersAddingSameSeatConcurrently_WhenEditTicketSelection_ThenExactlyOneSucceeds() throws Exception {
        String emailB = "race_b@mail.com";
        userService.registerUser("", new UserDTO(
                emailB, "r", "b", "pass", 1, 1, 2000, "Israel", "050-700-8000"));
        String tokenB = userService.login(emailB, "pass").getValue();
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        service.userSelectTickets(validToken, concurrentEventId,
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0))), new HashMap<>());
        service.enterEventPurchase(tokenB, companyId, concurrentEventId);
        service.userSelectTickets(tokenB, concurrentEventId,
                Map.of("tribune", List.of(new SeatingTicketDTO(1, 1))), new HashMap<>());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<ActiveOrderDTO>> fA = pool.submit(() -> {
            start.await();
            return service.editTicketSelection(validToken,
                    new HashMap<>(),
                    Map.of("tribune", List.of(new SeatingTicketDTO(5, 5))),
                    new HashMap<>());
        });
        Future<Response<ActiveOrderDTO>> fB = pool.submit(() -> {
            start.await();
            return service.editTicketSelection(tokenB,
                    new HashMap<>(),
                    Map.of("tribune", List.of(new SeatingTicketDTO(5, 5))),
                    new HashMap<>());
        });

        start.countDown();
        Response<ActiveOrderDTO> rA = fA.get();
        Response<ActiveOrderDTO> rB = fB.get();
        pool.shutdown();

        int successes = (rA.getValue() != null ? 1 : 0) + (rB.getValue() != null ? 1 : 0);
        assertEquals(1, successes,
                "exactly one user should win the race for seat (5,5); rA=" + rA.getMessage()
                        + " rB=" + rB.getMessage());

        int sizeA = activeOrderRepo.findOrderByUserId(auth.getUserEmail(validToken).getValue()).getTickets().size();
        int sizeB = activeOrderRepo.findOrderByUserId(auth.getUserEmail(tokenB).getValue()).getTickets().size();
        System.err.println("DEBUG sizeA=" + sizeA + " sizeB=" + sizeB
                + " rA=" + (rA.getValue() != null ? "OK" : "FAIL:" + rA.getMessage())
                + " rB=" + (rB.getValue() != null ? "OK" : "FAIL:" + rB.getMessage()));
        assertEquals(3, sizeA + sizeB, "winner=2 tickets, loser=1 ticket");
    }

    @Test
    void GivenTwoEditsToSameOrderConcurrently_WhenEditTicketSelection_ThenBothEventuallySucceed() throws Exception {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 5)).getValue();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<ActiveOrderDTO>> f1 = pool.submit(() -> {
            start.await();
            return service.editTicketSelection(validToken,
                    new HashMap<>(), new HashMap<>(), Map.of("floor", 7));
        });
        Future<Response<ActiveOrderDTO>> f2 = pool.submit(() -> {
            start.await();
            return service.editTicketSelection(validToken,
                    new HashMap<>(), new HashMap<>(), Map.of("floor", 3));
        });

        start.countDown();
        Response<ActiveOrderDTO> r1 = f1.get();
        Response<ActiveOrderDTO> r2 = f2.get();
        pool.shutdown();

        assertNotNull(r1.getValue(), "edit 1 unexpectedly failed: " + r1.getMessage());
        assertNotNull(r2.getValue(), "edit 2 unexpectedly failed: " + r2.getMessage());

        int finalSize = activeOrderRepo.findById(orderId).getTickets().size();
        assertTrue(finalSize == 3 || finalSize == 7,
                "final ticket count must be one of the two requested totals, got: " + finalSize);
    }

    // helper function for ticket status validation:
    private void assertTicketStatuses(int eventId, List<Integer> ticketIds, TicketStatus expectedStatus) {
        Event event = eventRepo.findById(eventId);

        for (Integer ticketId : ticketIds) {
            assertEquals(expectedStatus, event.getMap().getTicketStatus(ticketId),
                    "Ticket " + ticketId + " should have status " + expectedStatus);
        }
    }

    @Test
    void GivenValidActiveOrder_WhenCheckoutAndPayment_ThenOrderCreatedAndActiveOrderDeleted() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 2);

        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();

        ActiveOrder activeOrderBeforeCheckout = activeOrderRepo.findById(activeOrderId);
        List<Integer> selectedTicketIds = new ArrayList<>(activeOrderBeforeCheckout.getTickets());

        // validation ticket status
        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.LOCKED);

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-123");

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(checkoutPriceResponse.getValue(), "Checkout price should be prepared before payment.");

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNotNull(checkoutResponse.getValue(),
                "Checkout should return created order id. Message: " + checkoutResponse.getMessage());

        assertEquals("Purchase completed successfully", checkoutResponse.getMessage());

        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(activeOrderId),
                "Active order should be deleted after successful checkout");

        assertEquals(1, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Successful checkout should create exactly one order");

        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.SOLD);
        Mockito.verify(paymentSystem).pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));
        Mockito.verify(ticketSupply).issue(Mockito.any(TicketSupplyRequestDTO.class));

        Member buyer = userRepo.findUserByEmail("testuser1@gmail.com");
        assertEquals(1, buyer.getDelayedNotifications().size(),
                "Buyer should receive exactly one purchase-confirmation notification");
        NotifyDTO confirmation = buyer.getDelayedNotifications().get(0);
        assertEquals(NotifyType.GENERAL_POPUP, confirmation.getType());
        assertTrue(confirmation.getPayload().getMessage().contains("completed successfully"),
                "Confirmation message should state the order completed successfully: "
                        + confirmation.getPayload().getMessage());
        assertEquals(concurrentEventId, confirmation.getPayload().getEventId().intValue());
    }

    @Test
    void GivenConfirmationNotificationThrows_WhenCheckoutAndPayment_ThenPurchaseStillSucceeds() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 2);

        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, concurrentEventId, seating, standing);
        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());
        int activeOrderId = selectResponse.getValue();

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-confirm-throws");
        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);
        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        // Force ONLY the confirmation notification (notifyUser) to blow up
        Mockito.doThrow(new RuntimeException("confirmation boom"))
                .when(notifier).notifyUser(Mockito.anyString(), Mockito.any(NotifyDTO.class));

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);
        assertNotNull(checkoutPriceResponse.getValue(), "Checkout price should be prepared before payment.");

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNotNull(checkoutResponse.getValue(),
                "Purchase must succeed even when the confirmation notification throws. Message: "
                        + checkoutResponse.getMessage());
        assertEquals("Purchase completed successfully", checkoutResponse.getMessage());
        assertEquals(1, eventRepo.findById(concurrentEventId).getOrders().size(),
                "The order must be persisted even when the confirmation notification fails");
        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(activeOrderId),
                "Active order should still be deleted after a successful checkout");
    }

    @Test
    void GivenPaymentRejected_WhenCheckoutAndPayment_ThenActiveOrderRemainsAndOrderNotCreated() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 2);

        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();

        ActiveOrder activeOrderBeforeCheckout = activeOrderRepo.findById(activeOrderId);
        List<Integer> selectedTicketIds = new ArrayList<>(activeOrderBeforeCheckout.getTickets());

        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.LOCKED);

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn(null);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(checkoutPriceResponse.getValue(), "Checkout price should be prepared before payment.");

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNull(checkoutResponse.getValue());
        assertEquals("Payment rejected", checkoutResponse.getMessage());

        assertNotNull(activeOrderRepo.findById(activeOrderId),
                "Active order should remain when payment is rejected");

        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.LOCKED);

        assertEquals(0, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Rejected payment must not create an order");

        Mockito.verify(paymentSystem).pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));
        Mockito.verify(ticketSupply, Mockito.never()).issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    @Test
    void GivenTicketIssuanceRejected_WhenCheckoutAndPayment_ThenRefundAndActiveOrderDeleted() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 2);

        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();

        ActiveOrder activeOrderBeforeCheckout = activeOrderRepo.findById(activeOrderId);
        List<Integer> selectedTicketIds = new ArrayList<>(activeOrderBeforeCheckout.getTickets());

        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.LOCKED);

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-123");

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(false);

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        Mockito.when(paymentSystem.refund(Mockito.eq("payment-123"), Mockito.anyDouble()))
                .thenReturn(true);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(checkoutPriceResponse.getValue(), "Checkout price should be prepared before payment.");

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNull(checkoutResponse.getValue());
        assertEquals("Ticket issuance failed", checkoutResponse.getMessage());

        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(activeOrderId),
                "Active order should be deleted after ticket issuance failure");

        assertEquals(1, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Order should be created before refund handling");

        assertEquals(domain.event.OrderStatus.REFUNDED,
                eventRepo.findById(concurrentEventId).getOrders().get(0).getStatus(),
                "Order should be marked REFUNDED when refund succeeds");

        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.AVAILABLE);

        Mockito.verify(paymentSystem).pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));
        Mockito.verify(ticketSupply).issue(Mockito.any(TicketSupplyRequestDTO.class));
        Mockito.verify(paymentSystem).refund(Mockito.eq("payment-123"), Mockito.anyDouble());
    }

    @Test
    void GivenTicketIssuanceThrows_WhenCheckoutAndPayment_ThenRefundAndActiveOrderDeleted() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 2);

        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();

        ActiveOrder activeOrderBeforeCheckout = activeOrderRepo.findById(activeOrderId);
        List<Integer> selectedTicketIds = new ArrayList<>(activeOrderBeforeCheckout.getTickets());

        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.LOCKED);

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-123");

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenThrow(new RuntimeException("Ticket supply unavailable"));

        Mockito.when(paymentSystem.refund(Mockito.eq("payment-123"), Mockito.anyDouble()))
                .thenReturn(true);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(checkoutPriceResponse.getValue(), "Checkout price should be prepared before payment.");

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNull(checkoutResponse.getValue());
        assertEquals("Ticket issuance failed", checkoutResponse.getMessage());

        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(activeOrderId),
                "Active order should be deleted after ticket issuance exception");

        assertEquals(domain.event.OrderStatus.REFUNDED,
                eventRepo.findById(concurrentEventId).getOrders().get(0).getStatus(),
                "Order should be marked REFUNDED when refund succeeds");

        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.AVAILABLE);

        Mockito.verify(paymentSystem).pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));
        Mockito.verify(ticketSupply).issue(Mockito.any(TicketSupplyRequestDTO.class));
        Mockito.verify(paymentSystem).refund(Mockito.eq("payment-123"), Mockito.anyDouble());
    }

    @Test
    void GivenRefundFailsAfterIssuanceFailure_WhenCheckoutAndPayment_ThenOrderMarkedRefundRequired() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 2);

        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();

        ActiveOrder activeOrderBeforeCheckout = activeOrderRepo.findById(activeOrderId);
        List<Integer> selectedTicketIds = new ArrayList<>(activeOrderBeforeCheckout.getTickets());

        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.LOCKED);

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-123");

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(false);

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        Mockito.when(paymentSystem.refund(Mockito.eq("payment-123"), Mockito.anyDouble()))
                .thenReturn(false);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(checkoutPriceResponse.getValue(), "Checkout price should be prepared before payment.");

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNull(checkoutResponse.getValue());
        assertEquals("Ticket issuance failed", checkoutResponse.getMessage());

        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(activeOrderId),
                "Active order should be deleted after failed issuance");

        assertEquals(domain.event.OrderStatus.REFUND_REQUIRED,
                eventRepo.findById(concurrentEventId).getOrders().get(0).getStatus(),
                "Order should be marked REFUND_REQUIRED when refund fails");
        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.AVAILABLE);

        Mockito.verify(paymentSystem).pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));
        Mockito.verify(ticketSupply).issue(Mockito.any(TicketSupplyRequestDTO.class));
        Mockito.verify(paymentSystem).refund(Mockito.eq("payment-123"), Mockito.anyDouble());
    }

    @Test
    void GivenExpiredActiveOrder_WhenCheckoutAndPayment_ThenTicketsReleasedAndActiveOrderDeleted() {
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 2);

        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, concurrentEventId, seating, standing);

        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();

        ActiveOrder activeOrderBeforeExpiration = activeOrderRepo.findById(activeOrderId);
        List<Integer> selectedTicketIds = new ArrayList<>(activeOrderBeforeExpiration.getTickets());

        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.LOCKED);

        forceExpireOrder(activeOrderId);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNull(checkoutResponse.getValue());
        assertEquals("Active order expired", checkoutResponse.getMessage());

        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(activeOrderId),
                "Expired active order should be deleted during checkout");

        assertEquals(0, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Expired checkout must not create an order");
        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.AVAILABLE);
        String newEmail = "after_expired_checkout@mail.com";
        userService.registerUser("", new UserDTO(
                newEmail, "after", "expired", "pass",
                1, 1, 2000, "Israel", "050-101-2020"
        ));

        String newToken = userService.login(newEmail, "pass").getValue();

        Response<EnterPurchaseDTO> enterResponse =
                service.enterEventPurchase(newToken, companyId, concurrentEventId);

        assertNotNull(enterResponse.getValue(),
                "New user should enter purchase before rebooking. Message: " + enterResponse.getMessage());

        Response<Integer> rebookResponse =
                service.userSelectTickets(newToken, concurrentEventId, new HashMap<>(), Map.of("floor", 2));

        assertNotNull(rebookResponse.getValue(),
                "Tickets from expired active order should be released and selectable again. Message: "
                        + rebookResponse.getMessage());

        Mockito.verify(paymentSystem, Mockito.never())
                .pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.never())
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    @Test
    void GivenActiveOrderHasNoTickets_WhenCheckoutAndPayment_ThenErrorAndActiveOrderRemains() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        String userEmail = auth.getUserEmail(validToken).getValue();
        int activeOrderId = activeOrderRepo.findOrderByUserId(userEmail).getId();

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNull(checkoutResponse.getValue());
        assertEquals("Active order has no selected tickets", checkoutResponse.getMessage());

        assertNotNull(activeOrderRepo.findById(activeOrderId),
                "Active order should remain when no tickets were selected");

        assertEquals(0, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Checkout without tickets must not create an order");

        Mockito.verify(paymentSystem, Mockito.never())
                .pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.never())
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    @Test
    void GivenInvalidToken_WhenCheckoutAndPayment_ThenErrorReturned() {
        String invalidToken = "not-a-real-token";

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> response =
                service.checkoutAndPayment(invalidToken, 999, paymentDetails);

        assertNull(response.getValue());
        assertEquals("Invalid token", response.getMessage());

        Mockito.verify(paymentSystem, Mockito.never())
                .pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.never())
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }


    @Test
    void GivenSameActiveOrder_WhenCheckoutAndPaymentConcurrently_With10Threads_ThenOnlyOneSucceeds() throws Exception {
        int threadCount = 10;

        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse = service.userSelectTickets(
                validToken,
                concurrentEventId,
                new HashMap<>(),
                Map.of("floor", 2)
        );

        assertNotNull(selectResponse.getValue(),
                "setup select failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();

        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before concurrent payment."
        );

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenAnswer(invocation -> "payment-" + UUID.randomUUID());

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<Integer>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                start.await();

                PaymentDetailsDTO paymentDetails =
                        new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

                return service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);
            }));
        }

        start.countDown();

        int successes = 0;
        int failures = 0;
        List<String> failureMessages = new ArrayList<>();

        for (Future<Response<Integer>> future : futures) {
            Response<Integer> response = future.get();

            if (response.getValue() != null) {
                successes++;
            } else {
                failures++;
                failureMessages.add(response.getMessage());
            }
        }

        executor.shutdown();

        assertEquals(1, successes,
                "Only one concurrent checkout on the same active order should succeed. Failures: "
                        + failureMessages);

        assertEquals(threadCount - 1, failures,
                "All other concurrent checkout attempts should fail cleanly");

        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(activeOrderId),
                "Active order should be deleted after the successful checkout");

        assertEquals(1, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Concurrent checkout must not create duplicate orders");

        Mockito.verify(ticketSupply, Mockito.times(1))
                .issue(Mockito.any(TicketSupplyRequestDTO.class));

        Mockito.verify(paymentSystem, Mockito.times(1))
                .pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));
    }

    @Test
    void GivenDifferentActiveOrders_WhenCheckoutAndPaymentConcurrently_WithSlowPayment_ThenAllSucceed() throws Exception {
        int usersCount = 10;

        List<String> tokens = new ArrayList<>();
        List<Integer> activeOrderIds = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String email = "checkout_slow_payment_" + i + "@mail.com";

            Response<Boolean> registerResponse = userService.registerUser("", new UserDTO(
                    email,
                    "f" + i,
                    "l" + i,
                    "pass",
                    1,
                    1,
                    2000,
                    "Israel",
                    "050-456-78" + String.format("%02d", i)
            ));

            assertTrue(registerResponse.getValue(),
                    "register failed for user " + i + ": " + registerResponse.getMessage());

            String token = userService.login(email, "pass").getValue();
            tokens.add(token);

            Response<EnterPurchaseDTO> enterResponse =
                    service.enterEventPurchase(token, companyId, concurrentEventId);

            assertNotNull(enterResponse.getValue(),
                    "enter failed for user " + i + ": " + enterResponse.getMessage());

            Response<Integer> selectResponse = service.userSelectTickets(
                    token,
                    concurrentEventId,
                    new HashMap<>(),
                    Map.of("floor", 1)
            );

            assertNotNull(selectResponse.getValue(),
                    "select failed for user " + i + ": " + selectResponse.getMessage());

            int activeOrderId = selectResponse.getValue();
            activeOrderIds.add(activeOrderId);

            Response<CheckoutPriceDTO> checkoutPriceResponse =
                    service.prepareCheckout(token, activeOrderId);

            assertNotNull(
                    checkoutPriceResponse.getValue(),
                    "checkout price preparation failed for user " + i + ": " + checkoutPriceResponse.getMessage()
            );
        }

        assertEquals(usersCount, activeOrderIds.stream().distinct().count(),
                "setup bug: active order ids must be unique. ids=" + activeOrderIds);

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(50);
                    return "payment-" + UUID.randomUUID();
                });

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        ExecutorService executor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<Integer>>> futures = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String token = tokens.get(i);
            int activeOrderId = activeOrderIds.get(i);

            futures.add(executor.submit(() -> {
                start.await();

                PaymentDetailsDTO paymentDetails =
                        new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

                return service.checkoutAndPayment(token, activeOrderId, paymentDetails);
            }));
        }

        start.countDown();

        int successes = 0;
        List<String> failures = new ArrayList<>();

        for (Future<Response<Integer>> future : futures) {
            Response<Integer> response = future.get();

            if (response.getValue() != null) {
                successes++;
            } else {
                failures.add(response.getMessage());
            }
        }

        executor.shutdown();

        assertEquals(usersCount, successes,
                "All different active orders should succeed even when payment is slow. Failures: " + failures);

        assertEquals(usersCount, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Each successful checkout should create exactly one order");

        Mockito.verify(paymentSystem, Mockito.times(usersCount))
                .pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.times(usersCount))
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    @Test
    void GivenDifferentActiveOrders_WhenCheckoutAndPaymentConcurrently_WithSlowTicketSupply_ThenAllSucceed() throws Exception {
        int usersCount = 10;

        List<String> tokens = new ArrayList<>();
        List<Integer> activeOrderIds = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String email = "checkout_slow_supply_" + i + "@mail.com";

            Response<Boolean> registerResponse = userService.registerUser("", new UserDTO(
                    email,
                    "f" + i,
                    "l" + i,
                    "pass",
                    1,
                    1,
                    2000,
                    "Israel",
                    "050-567-89" + String.format("%02d", i)
            ));

            assertTrue(registerResponse.getValue(),
                    "register failed for user " + i + ": " + registerResponse.getMessage());

            String token = userService.login(email, "pass").getValue();
            tokens.add(token);

            Response<EnterPurchaseDTO> enterResponse =
                    service.enterEventPurchase(token, companyId, concurrentEventId);

            assertNotNull(enterResponse.getValue(),
                    "enter failed for user " + i + ": " + enterResponse.getMessage());

            Response<Integer> selectResponse = service.userSelectTickets(
                    token,
                    concurrentEventId,
                    new HashMap<>(),
                    Map.of("floor", 1)
            );

            assertNotNull(selectResponse.getValue(),
                    "select failed for user " + i + ": " + selectResponse.getMessage());

            int activeOrderId = selectResponse.getValue();

            Response<CheckoutPriceDTO> checkoutPriceResponse =
                    service.prepareCheckout(token, activeOrderId);

            assertNotNull(
                    checkoutPriceResponse.getValue(),
                    "Checkout price should be prepared before payment for user " + i + ": "
                            + checkoutPriceResponse.getMessage()
            );

            activeOrderIds.add(activeOrderId);
        }

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenAnswer(invocation -> "payment-" + UUID.randomUUID());

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(50);
                    return supplyResult;
                });

        ExecutorService executor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<Integer>>> futures = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String token = tokens.get(i);
            int activeOrderId = activeOrderIds.get(i);

            futures.add(executor.submit(() -> {
                start.await();

                PaymentDetailsDTO paymentDetails =
                        new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

                return service.checkoutAndPayment(token, activeOrderId, paymentDetails);
            }));
        }

        start.countDown();

        int successes = 0;
        List<String> failures = new ArrayList<>();

        for (Future<Response<Integer>> future : futures) {
            Response<Integer> response = future.get();

            if (response.getValue() != null) {
                successes++;
            } else {
                failures.add(response.getMessage());
            }
        }

        executor.shutdown();

        assertEquals(usersCount, successes,
                "All different active orders should succeed even when ticket supply is slow. Failures: " + failures);

        assertEquals(usersCount, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Each successful checkout should create exactly one order");

        Mockito.verify(paymentSystem, Mockito.times(usersCount))
                .pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.times(usersCount))
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    @Test
    void GivenDifferentActiveOrders_WhenCheckoutAndPaymentConcurrently_WithPartialTicketSupplyFailure_ThenConsistentState() throws Exception {
        int usersCount = 10;

        List<String> tokens = new ArrayList<>();
        List<Integer> activeOrderIds = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String email = "checkout_partial_failure_" + i + "@mail.com";

            Response<Boolean> registerResponse = userService.registerUser("", new UserDTO(
                    email,
                    "f" + i,
                    "l" + i,
                    "pass",
                    1,
                    1,
                    2000,
                    "Israel",
                    "050-678-90" + String.format("%02d", i)
            ));

            assertTrue(registerResponse.getValue(),
                    "register failed for user " + i + ": " + registerResponse.getMessage());

            String token = userService.login(email, "pass").getValue();
            tokens.add(token);

            service.enterEventPurchase(token, companyId, concurrentEventId);

            Response<Integer> selectResponse = service.userSelectTickets(
                    token,
                    concurrentEventId,
                    new HashMap<>(),
                    Map.of("floor", 1)
            );

            assertNotNull(selectResponse.getValue(),
                    "select failed for user " + i + ": " + selectResponse.getMessage());

            int activeOrderId = selectResponse.getValue();

            Response<CheckoutPriceDTO> checkoutPriceResponse =
                    service.prepareCheckout(token, activeOrderId);

            assertNotNull(
                    checkoutPriceResponse.getValue(),
                    "Checkout price should be prepared before payment for user " + i + ": "
                            + checkoutPriceResponse.getMessage()
            );

            activeOrderIds.add(activeOrderId);
        }

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenAnswer(invocation -> "payment-" + UUID.randomUUID());

        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble()))
                .thenReturn(true);

        AtomicInteger counter = new AtomicInteger(0);

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenAnswer(invocation -> {
                    int index = counter.getAndIncrement();

                    TicketSupplyResultDTO result = Mockito.mock(TicketSupplyResultDTO.class);

                    if (index % 2 == 0) {
                        Mockito.when(result.isSuccess()).thenReturn(true);
                    } else {
                        Mockito.when(result.isSuccess()).thenReturn(false);
                    }

                    return result;
                });

        ExecutorService executor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<Integer>>> futures = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String token = tokens.get(i);
            int activeOrderId = activeOrderIds.get(i);

            futures.add(executor.submit(() -> {
                start.await();

                PaymentDetailsDTO paymentDetails =
                        new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

                return service.checkoutAndPayment(token, activeOrderId, paymentDetails);
            }));
        }

        start.countDown();

        int successResponses = 0;
        int failedResponses = 0;

        for (Future<Response<Integer>> future : futures) {
            Response<Integer> response = future.get();

            if (response.getValue() != null) {
                successResponses++;
            } else {
                failedResponses++;
            }
        }

        executor.shutdown();

        List<Order> orders = eventRepo.findById(concurrentEventId).getOrders();

        long approved = orders.stream().filter(o -> o.getStatus() == OrderStatus.APPROVED).count();
        long refunded = orders.stream().filter(o -> o.getStatus() == OrderStatus.REFUNDED).count();

        assertEquals(usersCount, orders.size(),
                "Every checkout attempt should result in exactly one order");

        assertEquals(successResponses, approved,
                "Successful responses must match approved orders");

        assertEquals(failedResponses, refunded,
                "Failed responses must result in refunded orders");

        Mockito.verify(paymentSystem, Mockito.times(usersCount))
                .pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.times(usersCount))
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    @Test
    void GivenDifferentActiveOrders_WhenCheckoutAndPaymentConcurrently_With10Threads_ThenAllSucceed() throws Exception {
        int usersCount = 10;

        List<String> tokens = new ArrayList<>();
        List<Integer> activeOrderIds = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String email = "checkout_distinct_" + i + "@mail.com";

            Response<Boolean> registerResponse = userService.registerUser("", new UserDTO(
                    email,
                    "f" + i,
                    "l" + i,
                    "pass",
                    1,
                    1,
                    2000,
                    "Israel",
                    "050-888-77" + String.format("%02d", i)
            ));

            assertTrue(registerResponse.getValue(),
                    "register failed: " + registerResponse.getMessage());

            String token = userService.login(email, "pass").getValue();
            tokens.add(token);

            Response<EnterPurchaseDTO> enterResponse =
                    service.enterEventPurchase(token, companyId, concurrentEventId);

            assertNotNull(enterResponse.getValue(),
                    "enter failed: " + enterResponse.getMessage());

            Response<Integer> selectResponse =
                    service.userSelectTickets(token, concurrentEventId,
                            new HashMap<>(),
                            Map.of("floor", 1));

            assertNotNull(selectResponse.getValue(),
                    "select failed: " + selectResponse.getMessage());

            int activeOrderId = selectResponse.getValue();

            Response<CheckoutPriceDTO> checkoutPriceResponse =
                    service.prepareCheckout(token, activeOrderId);

            assertNotNull(
                    checkoutPriceResponse.getValue(),
                    "Checkout price should be prepared before payment for user " + i + ": "
                            + checkoutPriceResponse.getMessage()
            );

            activeOrderIds.add(activeOrderId);
        }

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any()))
                .thenAnswer(inv -> "payment-" + UUID.randomUUID());

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);

        Mockito.when(ticketSupply.issue(Mockito.any()))
                .thenReturn(supplyResult);

        ExecutorService executor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<Integer>>> futures = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String token = tokens.get(i);
            int orderId = activeOrderIds.get(i);

            futures.add(executor.submit(() -> {
                start.await();
                return service.checkoutAndPayment(token, orderId,
                        new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null));
            }));
        }

        start.countDown();

        int successes = 0;
        List<String> failures = new ArrayList<>();

        for (Future<Response<Integer>> f : futures) {
            Response<Integer> r = f.get();
            if (r.getValue() != null) successes++;
            else failures.add(r.getMessage());
        }

        executor.shutdown();

        assertEquals(usersCount, successes,
                "Failures: " + failures);

        assertEquals(usersCount,
                eventRepo.findById(concurrentEventId).getOrders().size());
    }

    @Test
    void GivenLimitedTickets_WhenCheckoutAndPaymentConcurrently_With10Threads_ThenNoOversellAndNoDuplicateOrders() throws Exception {
        int usersCount = 10;
        int ticketsPerUser = 20;

        List<String> tokens = new ArrayList<>();
        List<Integer> activeOrderIds = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String email = "checkout_limited_" + i + "@mail.com";

            Response<Boolean> registerResponse = userService.registerUser("", new UserDTO(
                    email,
                    "f" + i,
                    "l" + i,
                    "pass",
                    1,
                    1,
                    2000,
                    "Israel",
                    "050-123-45" + String.format("%02d", i)
            ));

            assertTrue(registerResponse.getValue());

            String token = userService.login(email, "pass").getValue();
            tokens.add(token);

            Response<EnterPurchaseDTO> enterResponse =
                    service.enterEventPurchase(token, companyId, concurrentEventId);

            assertNotNull(enterResponse.getValue());

            Response<Integer> selectResponse =
                    service.userSelectTickets(token, concurrentEventId,
                            new HashMap<>(),
                            Map.of("floor", ticketsPerUser));

            assertNotNull(selectResponse.getValue());

            activeOrderIds.add(selectResponse.getValue());
        }

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any()))
                .thenAnswer(inv -> "payment-" + UUID.randomUUID());

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);

        Mockito.when(ticketSupply.issue(Mockito.any()))
                .thenReturn(supplyResult);

        ExecutorService executor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<Integer>>> futures = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String token = tokens.get(i);
            int orderId = activeOrderIds.get(i);

            futures.add(executor.submit(() -> {
                start.await();
                return service.checkoutAndPayment(token, orderId,
                        new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null));
            }));
        }

        start.countDown();

        int successes = 0;
        int failures = 0;

        for (Future<Response<Integer>> f : futures) {
            Response<Integer> r = f.get();
            if (r.getValue() != null) successes++;
            else failures++;
        }

        executor.shutdown();

        int orders = eventRepo.findById(concurrentEventId).getOrders().size();

        assertEquals(successes, orders);
        assertEquals(usersCount, successes + failures);
        assertTrue(orders <= usersCount);
    }

    @Test
    void GivenActiveOrderBelongsToAnotherUser_WhenCheckoutAndPayment_ThenFail() {
        // user A
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        int orderId = service.userSelectTickets(
                validToken, concurrentEventId, new HashMap<>(), Map.of("floor", 2)
        ).getValue();

        // user B
        String emailB = "other_user@mail.com";
        userService.registerUser("", new UserDTO(
                emailB, "b", "b", "pass",
                1, 1, 2000, "Israel", "050-999-0000"
        ));
        String tokenB = userService.login(emailB, "pass").getValue();

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> response =
                service.checkoutAndPayment(tokenB, orderId, paymentDetails);

        assertNull(response.getValue());
        assertEquals("Active order does not belong to user", response.getMessage());
    }

    @Test
    void GivenEventBecomesInactiveAfterTicketSelection_WhenCheckoutAndPayment_ThenFail() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse = service.userSelectTickets(
                validToken,
                concurrentEventId,
                new HashMap<>(),
                Map.of("floor", 2)
        );

        assertNotNull(selectResponse.getValue(),
                "setup select failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();

        Event event = eventRepo.findById(concurrentEventId);
        event.setActive(false);
        eventRepo.store(event);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> response =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNull(response.getValue());
        assertEquals("Event is not active", response.getMessage());

        assertNotNull(activeOrderRepo.findById(activeOrderId),
                "Active order should remain when checkout is rejected because event is inactive");

        assertEquals(0, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Inactive event checkout must not create an order");

        Mockito.verify(paymentSystem, Mockito.never())
                .pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.never())
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    @Test
    void GivenTicketSupplyReturnsNull_WhenCheckoutAndPayment_ThenRefundAndActiveOrderDeleted() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse = service.userSelectTickets(
                validToken,
                concurrentEventId,
                new HashMap<>(),
                Map.of("floor", 2)
        );

        assertNotNull(selectResponse.getValue(),
                "setup select failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();
        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );

        ActiveOrder activeOrderBeforeCheckout = activeOrderRepo.findById(activeOrderId);
        List<Integer> selectedTicketIds = new ArrayList<>(activeOrderBeforeCheckout.getTickets());

        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.LOCKED);

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-123");

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(null);

        Mockito.when(paymentSystem.refund(Mockito.eq("payment-123"), Mockito.anyDouble()))
                .thenReturn(true);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> response =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNull(response.getValue());
        assertEquals("Ticket issuance failed", response.getMessage());

        assertThrows(NoSuchElementException.class,
                () -> activeOrderRepo.findById(activeOrderId),
                "Active order should be deleted after ticket supply returns null");

        assertEquals(1, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Order should be created before refund handling");

        assertEquals(domain.event.OrderStatus.REFUNDED,
                eventRepo.findById(concurrentEventId).getOrders().get(0).getStatus(),
                "Order should be marked REFUNDED when refund succeeds");
        assertTicketStatuses(concurrentEventId, selectedTicketIds, TicketStatus.AVAILABLE);

        Mockito.verify(paymentSystem).pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));
        Mockito.verify(ticketSupply).issue(Mockito.any(TicketSupplyRequestDTO.class));
        Mockito.verify(paymentSystem).refund(Mockito.eq("payment-123"), Mockito.anyDouble());
    }

    @Test
    void GivenPaymentThrowsException_WhenCheckoutAndPayment_ThenFailAndActiveOrderRemains() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse = service.userSelectTickets(
                validToken,
                concurrentEventId,
                new HashMap<>(),
                Map.of("floor", 2)
        );

        assertNotNull(selectResponse.getValue(),
                "setup select failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();
        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenThrow(new RuntimeException("payment service unavailable"));

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> response =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNull(response.getValue());
        assertEquals("Failed to complete purchase", response.getMessage());

        assertNotNull(activeOrderRepo.findById(activeOrderId),
                "Active order should remain when payment throws before order creation");

        assertEquals(0, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Payment exception must not create an order");

        Mockito.verify(paymentSystem).pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.never())
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    @Test
    void GivenPaymentReturnsBlankConfirmation_WhenCheckoutAndPayment_ThenPaymentRejectedAndActiveOrderRemains() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse = service.userSelectTickets(
                validToken,
                concurrentEventId,
                new HashMap<>(),
                Map.of("floor", 2)
        );

        assertNotNull(selectResponse.getValue(),
                "setup select failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();
        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("   ");

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> response =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNull(response.getValue());
        assertEquals("Payment rejected", response.getMessage());

        assertNotNull(activeOrderRepo.findById(activeOrderId),
                "Active order should remain when payment confirmation is blank");

        assertEquals(0, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Blank payment confirmation must not create an order");

        Mockito.verify(paymentSystem).pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.never())
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    @Test
    void GivenNonExistingActiveOrder_WhenCheckoutAndPayment_ThenNotFoundAndNoExternalServicesCalled() {
        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> response =
                service.checkoutAndPayment(validToken, 999999, paymentDetails);

        assertNull(response.getValue());
        assertEquals("Event or active order not found", response.getMessage());

        assertEquals(0, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Checkout with non-existing active order must not create an order");

        Mockito.verify(paymentSystem, Mockito.never())
                .pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.never())
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    @Test
    void GivenNullPaymentDetails_WhenCheckoutAndPayment_ThenFailAndActiveOrderRemains() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);

        Response<Integer> selectResponse = service.userSelectTickets(
                validToken,
                concurrentEventId,
                new HashMap<>(),
                Map.of("floor", 2)
        );

        assertNotNull(selectResponse.getValue(),
                "setup select failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();
        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );

        Response<Integer> response =
                service.checkoutAndPayment(validToken, activeOrderId, null);

        assertNull(response.getValue());
        assertEquals("Payment rejected", response.getMessage());

        assertNotNull(activeOrderRepo.findById(activeOrderId),
                "Active order should remain when payment details are null");

        assertEquals(0, eventRepo.findById(concurrentEventId).getOrders().size(),
                "Null payment details must not create an order");

        Mockito.verify(paymentSystem, Mockito.never())
                .pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.never())
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    @Test
    void GivenUsersAboveQueueCapacity_WhenActiveOrdersCompleteCheckout_ThenQueuedUsersArePromotedAndCanSelectTickets() throws Exception {
        int usersCount = 40;
        int expectedCapacity = capacity; // 20
        int expectedQueued = usersCount - expectedCapacity; // 20

        List<String> tokens = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String email = "queue_promote_after_checkout_" + i + "@mail.com";

            Response<Boolean> registerResponse = userService.registerUser("", new UserDTO(
                    email,
                    "f" + i,
                    "l" + i,
                    "pass",
                    1,
                    1,
                    2000,
                    "Israel",
                    "050-909-88" + String.format("%02d", i)
            ));

            assertTrue(registerResponse.getValue(),
                    "register failed: " + registerResponse.getMessage());

            tokens.add(userService.login(email, "pass").getValue());
        }

        ExecutorService enterExecutor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch enterStart = new CountDownLatch(1);
        Map<String, Future<Response<EnterPurchaseDTO>>> enterFutures = new HashMap<>();

        for (String token : tokens) {
            enterFutures.put(token, enterExecutor.submit(() -> {
                enterStart.await();
                return service.enterEventPurchase(token, companyId, concurrentEventId);
            }));
        }

        enterStart.countDown();

        List<String> admittedTokens = new ArrayList<>();
        List<String> queuedTokens = new ArrayList<>();

        for (String token : tokens) {
            Response<EnterPurchaseDTO> response = enterFutures.get(token).get();
            EnterPurchaseDTO dto = response.getValue();

            if (dto != null && !dto.isWaitingInQueue()) {
                admittedTokens.add(token);
            }
            if (dto != null && dto.isWaitingInQueue()) {
                queuedTokens.add(token);
            }
        }

        enterExecutor.shutdown();

        assertEquals(expectedCapacity, admittedTokens.size(),
                "Exactly capacity users should enter the purchase flow");

        assertEquals(expectedQueued, queuedTokens.size(),
                "All users above capacity should be queued");

        assertEquals(expectedCapacity, activeOrderRepo.countActiveOrdersForEvent(concurrentEventId),
                "Repo should contain exactly capacity active orders after first wave");

        List<Integer> activeOrderIds = new ArrayList<>();

        for (String token : admittedTokens) {
            Response<Integer> selectResponse = service.userSelectTickets(
                    token,
                    concurrentEventId,
                    new HashMap<>(),
                    Map.of("floor", 1)
            );
            assertNotNull(selectResponse.getValue(),
                    "ticket selection failed: " + selectResponse.getMessage());
            int activeOrderId = selectResponse.getValue();

            Response<CheckoutPriceDTO> checkoutPriceResponse =
                    service.prepareCheckout(token, activeOrderId);

            assertNotNull(
                    checkoutPriceResponse.getValue(),
                    "Checkout price should be prepared before payment: "
                            + checkoutPriceResponse.getMessage()
            );

            activeOrderIds.add(activeOrderId);
        }

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenAnswer(invocation -> "payment-" + UUID.randomUUID());

        TicketSupplyResultDTO successfulSupplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(successfulSupplyResult.isSuccess()).thenReturn(true);

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(successfulSupplyResult);

        ExecutorService checkoutExecutor = Executors.newFixedThreadPool(expectedCapacity);
        CountDownLatch checkoutStart = new CountDownLatch(1);
        List<Future<Response<Integer>>> checkoutFutures = new ArrayList<>();

        for (int i = 0; i < admittedTokens.size(); i++) {
            String token = admittedTokens.get(i);
            int activeOrderId = activeOrderIds.get(i);

            checkoutFutures.add(checkoutExecutor.submit(() -> {
                checkoutStart.await();

                PaymentDetailsDTO paymentDetails =
                        new PaymentDetailsDTO("1234", "12/30", "123", "111111111", 1, null);
                return service.checkoutAndPayment(token, activeOrderId, paymentDetails);
            }));
        }

        checkoutStart.countDown();

        int successfulCheckouts = 0;

        for (Future<Response<Integer>> future : checkoutFutures) {
            Response<Integer> response = future.get();
            assertNotNull(response.getValue(),
                    "checkout failed unexpectedly: " + response.getMessage());

            successfulCheckouts++;
        }

        checkoutExecutor.shutdown();

        assertEquals(expectedCapacity, successfulCheckouts,
                "All admitted users should complete checkout successfully");

        assertEquals(expectedCapacity, activeOrderRepo.countActiveOrdersForEvent(concurrentEventId),
                "After first wave checkout, queued users should be promoted into freed active-order slots");

        List<Integer> promotedOrderIds = new ArrayList<>();

        for (String queuedToken : queuedTokens) {
            Response<ActiveOrderDTO> proceedResponse =
                    service.memberProceedAnActiveOrder(queuedToken);


            assertNotNull(proceedResponse.getValue(),
                    "queued user should have been promoted to active order: "
                            + proceedResponse.getMessage());

            assertEquals(concurrentEventId, proceedResponse.getValue().getEventId(),
                    "promoted active order should belong to the same event");

            promotedOrderIds.add(proceedResponse.getValue().getId());
        }

        assertEquals(expectedQueued, promotedOrderIds.size(),
                "Every queued user should now have an active order");

        assertEquals(
                promotedOrderIds.size(),
                promotedOrderIds.stream().distinct().count(),
                "Every promoted active order should have a unique id"
        );

        for (String queuedToken : queuedTokens) {
            Response<Integer> selectResponse = service.userSelectTickets(
                    queuedToken,
                    concurrentEventId,
                    new HashMap<>(),
                    Map.of("floor", 1)
            );

            assertNotNull(selectResponse.getValue(),
                    "promoted queued user should be able to select tickets: "
                            + selectResponse.getMessage());
        }

        Mockito.verify(paymentSystem, Mockito.times(expectedCapacity))
                .pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class));

        Mockito.verify(ticketSupply, Mockito.times(expectedCapacity))
                .issue(Mockito.any(TicketSupplyRequestDTO.class));
    }

    private int createSoldOutTestEvent(String nameSuffix) {
        Response<Integer> r = companyEventService.createEvent(
                validToken,
                companyId,
                LocalDateTime.now().plusDays(5),
                "Sold Out Test Event " + nameSuffix,
                LocalDateTime.now().minusMinutes(5),
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );
        assertNotNull(r.getValue(), "create event setup failed: " + r.getMessage());
        int id = r.getValue();

        ElementPositionDTO stage = new ElementPositionDTO(10, 20);
        List<ElementPositionDTO> entries = List.of(new ElementPositionDTO(0, 0));
        List<StandingZoneDTO> standingZones = List.of(
                new StandingZoneDTO(2, "floor", 100.0, new ElementPositionDTO(1, 1)));
        List<SeatingZoneDTO> seatingZones = List.of(
                new SeatingZoneDTO(1, 1, "tribune", 150.0, new ElementPositionDTO(5, 5)));

        companyEventService.DefineVenueAndSeatingMap(validToken, id, stage, entries, standingZones, seatingZones);
        return id;
    }

    private long countSoldOutNotifications(Member member) {
        long count = 0;
        for (NotifyDTO n : member.getDelayedNotifications()) {
            if (n.getPayload().getMessage().contains("is sold out")) {
                count++;
            }
        }
        return count;
    }

    private NotifyDTO firstSoldOutNotification(Member member) {
        for (NotifyDTO n : member.getDelayedNotifications()) {
            if (n.getPayload().getMessage().contains("is sold out")) {
                return n;
            }
        }
        return null;
    }

    @Test
    void GivenEventSoldOutAfterFinalPurchase_WhenCheckoutAndPayment_ThenFounderReceivesSoldOutNotification() {
        int soldOutEventId = createSoldOutTestEvent("sold-out-1");

        service.enterEventPurchase(validToken, companyId, soldOutEventId);
        Map<String, List<SeatingTicketDTO>> seating =
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0)));
        Map<String, Integer> standing = Map.of("floor", 2);

        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, soldOutEventId, seating, standing);
        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());
        int activeOrderId = selectResponse.getValue();
        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-soldout");

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);
        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNotNull(checkoutResponse.getValue(),
                "Checkout should succeed. Message: " + checkoutResponse.getMessage());
        assertTrue(eventRepo.findById(soldOutEventId).isSoldOut(),
                "Event should be sold out after final purchase");

        Member founder = userRepo.findUserByEmail("testuser1@gmail.com");
        assertEquals(1, countSoldOutNotifications(founder),
                "Founder should receive exactly one sold-out notification");

        NotifyDTO notification = firstSoldOutNotification(founder);
        assertNotNull(notification);
        assertEquals(NotifyType.GENERAL_POPUP, notification.getType());
        assertEquals(soldOutEventId, notification.getPayload().getEventId().intValue());
        assertTrue(notification.getPayload().getMessage().contains("sold out"),
                "Notification message should mention sold out: "
                        + notification.getPayload().getMessage());
    }

    @Test
    void GivenEventStillHasAvailableTickets_WhenCheckoutAndPayment_ThenNoSoldOutNotificationSent() {
        service.enterEventPurchase(validToken, companyId, concurrentEventId);
        Map<String, List<SeatingTicketDTO>> seating = new HashMap<>();
        Map<String, Integer> standing = Map.of("floor", 2);

        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, concurrentEventId, seating, standing);
        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());
        int activeOrderId = selectResponse.getValue();
        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-not-soldout");

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);
        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNotNull(checkoutResponse.getValue(),
                "Checkout should succeed. Message: " + checkoutResponse.getMessage());
        assertFalse(eventRepo.findById(concurrentEventId).isSoldOut(),
                "Event should still have capacity after a small purchase");

        Member founder = userRepo.findUserByEmail("testuser1@gmail.com");
        assertEquals(0, countSoldOutNotifications(founder),
                "No sold-out notification should be sent when the event still has capacity");
    }

    @Test
    void GivenSoldOutEventWithExtraOwnerAndManager_WhenCheckoutAndPayment_ThenAllOwnersAndManagersReceiveNotification() {
        userService.registerUser("", new UserDTO(
                "owner2@gmail.com", "Alice", "Owner", "pw2",
                1, 1, 1990, "TLV", "050-000-0002"));
        userService.registerUser("", new UserDTO(
                "manager1@gmail.com", "Bob", "Manager", "pw3",
                1, 1, 1990, "TLV", "050-000-0003"));

        String owner2Token = userService.login("owner2@gmail.com", "pw2").getValue();
        String manager1Token = userService.login("manager1@gmail.com", "pw3").getValue();
        int owner2Id = auth.getUserId(owner2Token).getValue();
        int manager1Id = auth.getUserId(manager1Token).getValue();

        Response<Boolean> reqOwner = companyService.requestAppointOwner(validToken, companyId, owner2Id);
        assertNotNull(reqOwner.getValue(),
                "request appoint owner setup failed: " + reqOwner.getMessage());
        Response<Boolean> acceptOwner = companyService.respondToOwnerAppointment(owner2Token, companyId, true);
        assertNotNull(acceptOwner.getValue(),
                "accept owner setup failed: " + acceptOwner.getMessage());

        Response<Boolean> reqManager = companyService.requestAppointManager(
                validToken, companyId, manager1Id,
                Set.of(PermissionType.MANAGE_EVENTS_INVENTORY));
        assertNotNull(reqManager.getValue(),
                "request appoint manager setup failed: " + reqManager.getMessage());
        Response<Boolean> acceptManager = companyService.respondToManagerAppointment(manager1Token, companyId, true);
        assertNotNull(acceptManager.getValue(),
                "accept manager setup failed: " + acceptManager.getMessage());

        int soldOutEventId = createSoldOutTestEvent("sold-out-3");

        service.enterEventPurchase(validToken, companyId, soldOutEventId);
        Map<String, List<SeatingTicketDTO>> seating =
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0)));
        Map<String, Integer> standing = Map.of("floor", 2);

        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, soldOutEventId, seating, standing);
        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());
        int activeOrderId = selectResponse.getValue();
        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-multi");

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);
        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNotNull(checkoutResponse.getValue(),
                "Checkout should succeed. Message: " + checkoutResponse.getMessage());

        Member founder = userRepo.findUserByEmail("testuser1@gmail.com");
        Member owner2 = userRepo.findUserByEmail("owner2@gmail.com");
        Member manager1 = userRepo.findUserByEmail("manager1@gmail.com");

        assertEquals(1, countSoldOutNotifications(founder),
                "Founder should receive exactly one sold-out notification");
        assertEquals(1, countSoldOutNotifications(owner2),
                "Additional owner should receive exactly one sold-out notification");
        assertEquals(1, countSoldOutNotifications(manager1),
                "Manager should receive exactly one sold-out notification");

        assertEquals(NotifyType.GENERAL_POPUP, firstSoldOutNotification(founder).getType());
        assertEquals(NotifyType.GENERAL_POPUP, firstSoldOutNotification(owner2).getType());
        assertEquals(NotifyType.GENERAL_POPUP, firstSoldOutNotification(manager1).getType());
    }

    @Test
    void GivenIssuanceFailedAfterPayment_WhenCheckoutAndPayment_ThenNoSoldOutNotificationSent() {
        int soldOutEventId = createSoldOutTestEvent("sold-out-4");

        service.enterEventPurchase(validToken, companyId, soldOutEventId);
        Map<String, List<SeatingTicketDTO>> seating =
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0)));
        Map<String, Integer> standing = Map.of("floor", 2);

        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, soldOutEventId, seating, standing);
        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());
        int activeOrderId = selectResponse.getValue();
        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-refund");
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble()))
                .thenReturn(true);

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(false);
        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNull(checkoutResponse.getValue(),
                "Checkout must fail when ticket issuance fails");
        assertFalse(eventRepo.findById(soldOutEventId).isSoldOut(),
                "Tickets released back, event must not be sold out");

        Member founder = userRepo.findUserByEmail("testuser1@gmail.com");
        assertTrue(founder.getDelayedNotifications().isEmpty(),
                "No sold-out notification should be sent when tickets are released back");
    }

    @Test
    void GivenSoldOutEventAndOwnerOnline_WhenCheckoutAndPayment_ThenOwnerReceivesRealtimeBroadcast() throws InterruptedException {
        String founderEmail = "testuser1@gmail.com";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> received = new AtomicReference<>();

        Registration registration = Broadcaster.registerUser(founderEmail, dto -> {
            received.set(dto);
            latch.countDown();
        });

        try {
            int soldOutEventId = createSoldOutTestEvent("realtime");

            service.enterEventPurchase(validToken, companyId, soldOutEventId);
            Map<String, List<SeatingTicketDTO>> seating =
                    Map.of("tribune", List.of(new SeatingTicketDTO(0, 0)));
            Map<String, Integer> standing = Map.of("floor", 2);
            Response<Integer> selectResponse =
                    service.userSelectTickets(validToken, soldOutEventId, seating, standing);
            assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());

            int activeOrderId = selectResponse.getValue();

            Response<CheckoutPriceDTO> checkoutPriceResponse =
                    service.prepareCheckout(validToken, activeOrderId);

            assertNotNull(
                    checkoutPriceResponse.getValue(),
                    "Checkout price should be prepared before payment: "
                            + checkoutPriceResponse.getMessage()
            );

            Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                    .thenReturn("payment-realtime");


            TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
            Mockito.when(supplyResult.isSuccess()).thenReturn(true);
            Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                    .thenReturn(supplyResult);

            PaymentDetailsDTO paymentDetails =
                    new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

            Response<Integer> checkoutResponse =
                    service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

            assertNotNull(checkoutResponse.getValue(),
                    "Checkout should succeed. Message: " + checkoutResponse.getMessage());

            assertTrue(latch.await(2, TimeUnit.SECONDS),
                    "Online owner must receive the sold-out broadcast in real time");
            assertNotNull(received.get());
            assertEquals(NotifyType.GENERAL_POPUP, received.get().getType());
            assertEquals(soldOutEventId, received.get().getPayload().getEventId().intValue());

            Member founder = userRepo.findUserByEmail(founderEmail);
            assertTrue(founder.getDelayedNotifications().isEmpty(),
                    "Real-time delivery must not fall back to the delayed-notifications list");
        } finally {
            registration.remove();
        }
    }

    @Test
    void GivenNotifierThrows_WhenSoldOutCheckoutAndPayment_ThenPurchaseStillSucceeds() {
        Mockito.doThrow(new RuntimeException("notify boom"))
                .when(notifier).notifyMemberById(Mockito.any(Integer.class), Mockito.any(NotifyDTO.class));

        int soldOutEventId = createSoldOutTestEvent("notify-throws");

        service.enterEventPurchase(validToken, companyId, soldOutEventId);
        Map<String, List<SeatingTicketDTO>> seating =
                Map.of("tribune", List.of(new SeatingTicketDTO(0, 0)));
        Map<String, Integer> standing = Map.of("floor", 2);

        Response<Integer> selectResponse =
                service.userSelectTickets(validToken, soldOutEventId, seating, standing);
        assertNotNull(selectResponse.getValue(), "setup failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();

        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-notify-throws");
        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);
        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);

        assertNotNull(checkoutResponse.getValue(),
                "Purchase must succeed even when the notifier throws. Message: "
                        + checkoutResponse.getMessage());
        assertEquals("Purchase completed successfully", checkoutResponse.getMessage());
        assertEquals(1, eventRepo.findById(soldOutEventId).getOrders().size(),
                "The order must be persisted regardless of notification failure");
        assertTrue(eventRepo.findById(soldOutEventId).isSoldOut(),
                "Event must still be marked sold out after a successful purchase");
        Mockito.verify(notifier, Mockito.atLeastOnce())
                .notifyMemberById(Mockito.any(Integer.class), Mockito.any(NotifyDTO.class));
    }

    @Test
    void GivenSecondToLastPurchase_WhenCheckoutAndPayment_ThenNoNotificationUntilLastPurchase() {
        Response<Integer> createResp = companyEventService.createEvent(
                validToken,
                companyId,
                LocalDateTime.now().plusDays(5),
                "Boundary Event",
                LocalDateTime.now().minusMinutes(5),
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );
        assertNotNull(createResp.getValue(), "create event setup failed: " + createResp.getMessage());
        int boundaryEventId = createResp.getValue();

        ElementPositionDTO stage = new ElementPositionDTO(10, 20);
        List<ElementPositionDTO> entries = List.of(new ElementPositionDTO(0, 0));
        List<StandingZoneDTO> standingZones = List.of(
                new StandingZoneDTO(2, "floor", 100.0, new ElementPositionDTO(1, 1)));
        List<SeatingZoneDTO> seatingZones = List.of(
                new SeatingZoneDTO(1, 2, "tribune", 150.0, new ElementPositionDTO(5, 5)));
        companyEventService.DefineVenueAndSeatingMap(
                validToken, boundaryEventId, stage, entries, standingZones, seatingZones);

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-boundary");
        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);
        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);
        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        // Purchase 1 — buy both seats only; standing zone untouched, event NOT sold out
        service.enterEventPurchase(validToken, companyId, boundaryEventId);
        Map<String, List<SeatingTicketDTO>> firstSeating = Map.of("tribune",
                List.of(new SeatingTicketDTO(0, 0), new SeatingTicketDTO(0, 1)));
        Response<Integer> firstSelect = service.userSelectTickets(
                validToken, boundaryEventId, firstSeating, new HashMap<>());
        assertNotNull(firstSelect.getValue(), "first select failed: " + firstSelect.getMessage());
        int firstActiveOrderId = firstSelect.getValue();

        Response<CheckoutPriceDTO> firstCheckoutPriceResponse =
                service.prepareCheckout(validToken, firstActiveOrderId);

        assertNotNull(
                firstCheckoutPriceResponse.getValue(),
                "First checkout price should be prepared before payment: "
                        + firstCheckoutPriceResponse.getMessage()
        );

        Response<Integer> firstCheckout =
                service.checkoutAndPayment(validToken, firstActiveOrderId, paymentDetails);
        assertNotNull(firstCheckout.getValue(),
                "first checkout should succeed. Message: " + firstCheckout.getMessage());
        assertFalse(eventRepo.findById(boundaryEventId).isSoldOut(),
                "Event should NOT be sold out while standing zone has capacity");

        Member founderAfterFirst = userRepo.findUserByEmail("testuser1@gmail.com");
        assertEquals(0, countSoldOutNotifications(founderAfterFirst),
                "No sold-out notification should be sent before the event is fully drained");

        // Purchase 2 — buy both standing tickets; event becomes sold out
        service.enterEventPurchase(validToken, companyId, boundaryEventId);
        Response<Integer> secondSelect = service.userSelectTickets(
                validToken, boundaryEventId, new HashMap<>(), Map.of("floor", 2));
        assertNotNull(secondSelect.getValue(), "second select failed: " + secondSelect.getMessage());
        int secondActiveOrderId = secondSelect.getValue();

        Response<CheckoutPriceDTO> secondCheckoutPriceResponse =
                service.prepareCheckout(validToken, secondActiveOrderId);

        assertNotNull(
                secondCheckoutPriceResponse.getValue(),
                "Second checkout price should be prepared before payment: "
                        + secondCheckoutPriceResponse.getMessage()
        );

        Response<Integer> secondCheckout =
                service.checkoutAndPayment(validToken, secondActiveOrderId, paymentDetails);
        assertNotNull(secondCheckout.getValue(),
                "second checkout should succeed. Message: " + secondCheckout.getMessage());
        assertTrue(eventRepo.findById(boundaryEventId).isSoldOut(),
                "Event should be sold out after final purchase");

        Member founderAfterFinal = userRepo.findUserByEmail("testuser1@gmail.com");
        assertEquals(1, countSoldOutNotifications(founderAfterFinal),
                "Exactly one sold-out notification should fire — on the final purchase");
        NotifyDTO notification = firstSoldOutNotification(founderAfterFinal);
        assertNotNull(notification);
        assertEquals(NotifyType.GENERAL_POPUP, notification.getType());
        assertEquals(boundaryEventId, notification.getPayload().getEventId().intValue());
    }

    @Test
    void GivenManagersAppointedByDifferentOwners_WhenCheckoutAndPayment_ThenAllManagersInTreeReceiveNotification() {
        userService.registerUser("", new UserDTO(
                "owner2@gmail.com", "Alice", "Owner", "pw2",
                1, 1, 1990, "TLV", "050-000-0002"));
        userService.registerUser("", new UserDTO(
                "manager1@gmail.com", "Bob", "Manager", "pw3",
                1, 1, 1990, "TLV", "050-000-0003"));
        userService.registerUser("", new UserDTO(
                "manager2@gmail.com", "Carol", "Manager", "pw4",
                1, 1, 1990, "TLV", "050-000-0004"));

        String owner2Token = userService.login("owner2@gmail.com", "pw2").getValue();
        String manager1Token = userService.login("manager1@gmail.com", "pw3").getValue();
        String manager2Token = userService.login("manager2@gmail.com", "pw4").getValue();
        int owner2Id = auth.getUserId(owner2Token).getValue();
        int manager1Id = auth.getUserId(manager1Token).getValue();
        int manager2Id = auth.getUserId(manager2Token).getValue();

        Response<Boolean> reqOwner = companyService.requestAppointOwner(validToken, companyId, owner2Id);
        assertNotNull(reqOwner.getValue(), "request appoint owner setup failed");
        Response<Boolean> acceptOwner = companyService.respondToOwnerAppointment(owner2Token, companyId, true);
        assertNotNull(acceptOwner.getValue(), "accept owner setup failed");

        Response<Boolean> reqManager1 = companyService.requestAppointManager(
                validToken, companyId, manager1Id, Set.of(PermissionType.MANAGE_EVENTS_INVENTORY));
        assertNotNull(reqManager1.getValue(), "request appoint manager1 setup failed");
        Response<Boolean> acceptManager1 = companyService.respondToManagerAppointment(manager1Token, companyId, true);
        assertNotNull(acceptManager1.getValue(), "accept manager1 setup failed");

        Response<Boolean> reqManager2 = companyService.requestAppointManager(
                owner2Token, companyId, manager2Id, Set.of(PermissionType.MANAGE_POLICIES));
        assertNotNull(reqManager2.getValue(), "request appoint manager2 setup failed");
        Response<Boolean> acceptManager2 = companyService.respondToManagerAppointment(manager2Token, companyId, true);
        assertNotNull(acceptManager2.getValue(), "accept manager2 setup failed");

        int soldOutEventId = createSoldOutTestEvent("sold-out-5");
        service.enterEventPurchase(validToken, companyId, soldOutEventId);
        Map<String, List<SeatingTicketDTO>> seating = Map.of("tribune", List.of(new SeatingTicketDTO(0, 0)));
        Map<String, Integer> standing = Map.of("floor", 2);
        Response<Integer> selectResponse = service.userSelectTickets(validToken, soldOutEventId, seating, standing);
        assertNotNull(selectResponse.getValue(), "setup failed");
        int activeOrderId = selectResponse.getValue();

        Response<CheckoutPriceDTO> checkoutPriceResponse =
                service.prepareCheckout(validToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-tree");
        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);
        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);
        PaymentDetailsDTO paymentDetails = new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        userService.cleanDelayedNotifications("testuser1@gmail.com");
        userService.cleanDelayedNotifications("owner2@gmail.com");
        userService.cleanDelayedNotifications("manager1@gmail.com");
        userService.cleanDelayedNotifications("manager2@gmail.com");

        Response<Integer> checkoutResponse =
                service.checkoutAndPayment(validToken, activeOrderId, paymentDetails);
        assertNotNull(checkoutResponse.getValue(), "Checkout should succeed");

        Member founder = userRepo.findUserByEmail("testuser1@gmail.com");
        Member owner2 = userRepo.findUserByEmail("owner2@gmail.com");
        Member manager1 = userRepo.findUserByEmail("manager1@gmail.com");
        Member manager2 = userRepo.findUserByEmail("manager2@gmail.com");

        assertEquals(1, countSoldOutNotifications(founder), "Founder should receive the sold-out notification");
        assertEquals(1, countSoldOutNotifications(owner2), "Extra owner should receive the sold-out notification");
        assertEquals(1, countSoldOutNotifications(manager1), "Manager1 (appointed by founder) should receive the sold-out notification");
        assertEquals(1, countSoldOutNotifications(manager2), "Manager2 (appointed by another owner) should also receive the sold-out notification");
    }
    @Test
    void GivenSoldOutEventWithWaitingUser_WhenSomeoneCheckouts_ThenWaitingUserPromotedAndNotified() throws Exception {
        // Arrange
        WebQueue.resetForTesting();
        WebQueue.getInstance(100);

        Response<Integer> eventResp = companyEventService.createEvent(validToken, companyId,
                LocalDateTime.now().plusDays(1), "Isolated Queue Event",
                LocalDateTime.now().minusMinutes(10), false, GeographicalArea.CENTER, CategoryEvent.SPORTS);

        assertNotNull(eventResp.getValue(), "Event creation failed: " + eventResp.getMessage());
        int isolatedEventId = eventResp.getValue();

        companyEventService.DefineVenueAndSeatingMap(validToken, isolatedEventId,
                new ElementPositionDTO(10, 20), List.of(new ElementPositionDTO(0,0)),
                List.of(new StandingZoneDTO(capacity, "floor", 100.0, new ElementPositionDTO(1, 1))), new ArrayList<>());

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-123");
        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);
        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        String firstFillerToken = null;
        int firstFillerOrderId = -1;
        for (int i = 0; i < capacity; i++) {
            String uniqueEmail = "filler_" + UUID.randomUUID() + "@test.com";
            String uniquePhone = "050-888-77" + String.format("%02d", (i % 100));

            Response<Boolean> regResp = userService.registerUser("", new UserDTO(uniqueEmail, "f", "l", "p", 1, 1, 2000, "Israel", uniquePhone));

            assertNotNull(regResp.getValue(), "Registration failed for filler " + i + ". Server says: " + regResp.getMessage());
            assertTrue(regResp.getValue(), "Registration returned false for filler " + i);

            String token = userService.login(uniqueEmail, "p").getValue();
            assertNotNull(token, "Login failed for filler " + i);

            Response<EnterPurchaseDTO> enterResp = service.enterEventPurchase(token, companyId, isolatedEventId);
            assertNotNull(enterResp.getValue(), "Enter event failed for filler " + i + ". Server says: " + enterResp.getMessage());

            Response<Integer> selectResp = service.userSelectTickets(token, isolatedEventId, new HashMap<>(), Map.of("floor", 1));
            assertNotNull(selectResp.getValue(), "Ticket selection failed for filler " + i + ". Server says: " + selectResp.getMessage());

            if (i == 0) {
                firstFillerToken = token;
                firstFillerOrderId = selectResp.getValue();

                Response<CheckoutPriceDTO> checkoutPriceResponse =
                        service.prepareCheckout(firstFillerToken, firstFillerOrderId);

                assertNotNull(
                        checkoutPriceResponse.getValue(),
                        "Checkout price should be prepared before payment: "
                                + checkoutPriceResponse.getMessage()
                );
            }
        }

        // Register waiting user
        String waiterEmail = "waiter_" + UUID.randomUUID() + "@test.com";
        Response<Boolean> waiterRegResp = userService.registerUser("", new UserDTO(waiterEmail, "w", "w", "pass", 1, 1, 2000, "Israel", "050-123-4567"));
        assertNotNull(waiterRegResp.getValue(), "Waiter registration failed: " + waiterRegResp.getMessage());

        String waitingToken = userService.login(waiterEmail, "pass").getValue();

        Response<EnterPurchaseDTO> enterResp = service.enterEventPurchase(waitingToken, companyId, isolatedEventId);
        assertNotNull(enterResp.getValue(), "Waiter enter event failed: " + enterResp.getMessage());
        assertTrue(enterResp.getValue().isWaitingInQueue(), "User should be in queue");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> receivedNotif = new AtomicReference<>();
        Registration registration = Broadcaster.registerTab(waitingToken, n -> {
            receivedNotif.set(n);
            latch.countDown();
        });

        // Act
        PaymentDetailsDTO payment = new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);
        Response<Integer> checkoutResp = service.checkoutAndPayment(firstFillerToken, firstFillerOrderId, payment);

        assertNotNull(checkoutResp.getValue(), "Checkout failed, promoteNextInQueue will not be triggered. Server says: " + checkoutResp.getMessage());

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Waiting user should have been notified via Tab");
        assertNotNull(receivedNotif.get());
        assertEquals(NotifyType.QUEUE_EVENT_TURN_ARRIVED, receivedNotif.get().getType());
        assertEquals(isolatedEventId, receivedNotif.get().getPayload().getEventId());

        registration.remove();
    }
    @Test
    void GivenExpiredOrderInFullEvent_WhenCleanupRuns_ThenWaitingUserPromotedAndNotified() throws Exception {
        // Arrange
        WebQueue.resetForTesting();
        WebQueue.getInstance(100);

        Response<Integer> eventResp = companyEventService.createEvent(validToken, companyId,
                LocalDateTime.now().plusDays(1), "Cleanup Queue Event",
                LocalDateTime.now().minusMinutes(10), false, GeographicalArea.CENTER, CategoryEvent.SPORTS);

        assertNotNull(eventResp.getValue(), "Event creation failed: " + eventResp.getMessage());
        int isolatedEventId = eventResp.getValue();

        companyEventService.DefineVenueAndSeatingMap(validToken, isolatedEventId,
                new ElementPositionDTO(10, 20), List.of(new ElementPositionDTO(0,0)),
                List.of(new StandingZoneDTO(capacity, "floor", 100.0, new ElementPositionDTO(1, 1))), new ArrayList<>());

        String firstFillerToken = null;
        int firstFillerOrderId = -1;

        // Fill capacity to the limit with unique users
        for (int i = 0; i < capacity; i++) {
            String uniqueEmail = "cleanup_filler_" + UUID.randomUUID() + "@test.com";
            String uniquePhone = "050-888-77" + String.format("%02d", (i % 100));

            Response<Boolean> regResp = userService.registerUser("", new UserDTO(uniqueEmail, "f", "l", "p", 1, 1, 2000, "Israel", uniquePhone));
            assertNotNull(regResp.getValue(), "Registration failed for filler " + i + ". Server says: " + regResp.getMessage());

            String token = userService.login(uniqueEmail, "p").getValue();
            Response<EnterPurchaseDTO> enterResp = service.enterEventPurchase(token, companyId, isolatedEventId);
            Response<Integer> selectResp = service.userSelectTickets(token, isolatedEventId, new HashMap<>(), Map.of("floor", 1));

            if (i == 0) {
                firstFillerToken = token;
                firstFillerOrderId = selectResp.getValue();
            }
        }

        // Register waiting user
        String waiterEmail = "waiter_cleanup_" + UUID.randomUUID() + "@test.com";
        Response<Boolean> waiterRegResp = userService.registerUser("", new UserDTO(waiterEmail, "w", "w", "pass", 1, 1, 2000, "Israel", "050-765-4321"));
        assertNotNull(waiterRegResp.getValue(), "Waiter registration failed: " + waiterRegResp.getMessage());
        String waitingToken = userService.login(waiterEmail, "pass").getValue();

        Response<EnterPurchaseDTO> enterResp = service.enterEventPurchase(waitingToken, companyId, isolatedEventId);
        assertNotNull(enterResp.getValue(), "Waiter enter event failed: " + enterResp.getMessage());
        assertTrue(enterResp.getValue().isWaitingInQueue(), "User should be in queue");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> receivedNotif = new AtomicReference<>();
        Registration registration = Broadcaster.registerTab(waitingToken, n -> {
            receivedNotif.set(n);
            latch.countDown();
        });

        // Force expiration for the first user's active order using the class helper method
        forceExpireOrder(firstFillerOrderId);

        // Act
        service.cleanupExpiredOrders();

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Waiting user should have been notified via Tab after cleanup process");
        assertNotNull(receivedNotif.get());
        assertEquals(NotifyType.QUEUE_EVENT_TURN_ARRIVED, receivedNotif.get().getType());
        assertEquals(isolatedEventId, receivedNotif.get().getPayload().getEventId());

        registration.remove();
    }
}
