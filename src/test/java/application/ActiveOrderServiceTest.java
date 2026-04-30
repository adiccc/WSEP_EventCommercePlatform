package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import Log.LoggerSetup;
import domain.activeOrder.ActiveOrder;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dto.EventMapDTO;
import domain.dto.UserDTO;
import domain.event.Event;
import domain.event.EventMap;
import domain.lottery.Lottery;
import domain.user.IUserRepo;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
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

    private String validToken;
    private String eventId;
    private String concurrentEventId;

    private TokenService tokenService;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private UserService userService;

    private ElementPositionDTO stage;
    private List<ElementPositionDTO> entries;
    private List<StandingZoneDTO> standingZones;
    private List<SeatingZoneDTO> seatingZones;

    private final int companyId = 1;
    // private final int capacity = MAX_ACTIVE_ORDERS_PER_EVENT;
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

        Response<String> r = companyEventService.createEvent(
                validToken,
                companyId,
                LocalDateTime.now().plusDays(5),
                "Test Event",
                LocalDateTime.now().plusHours(2),     //saleStartDate is in the future
                true,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        Response<String> eventResponse = companyEventService.createEvent(
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
                capacity
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
        Response<EventMapDTO> response = service.enterEventPurchase(validToken, companyId, "bad-id");

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

}