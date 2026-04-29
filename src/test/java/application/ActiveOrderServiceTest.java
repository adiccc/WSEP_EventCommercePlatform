package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
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

    private String validToken;
    private String eventId;

    private TokenService tokenService;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;

    private ElementPositionDTO stage;
    private List<ElementPositionDTO> entries;
    private List<StandingZoneDTO> standingZones;
    private List<SeatingZoneDTO> seatingZones;

    private final int companyId = 1;
    private final int capacity = 20;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        userRepo = new UserRepo();
        passwordEncoder = new PasswordEncoderUtil();
        auth = new Auth(tokenService, userRepo, passwordEncoder);

        UserService userService = new UserService(tokenService, auth, userRepo, passwordEncoder);

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

        CompanyService companyService = new CompanyService(auth, companyRepo, userRepo);
        companyService.createProductionCompany(validToken, companyId,
                "test-company", "testC@company.com", "054-5556677", "leumi");

        companyEventService = new EventCompanyManageService(companyRepo, eventRepo, auth, paymentSystem);

        Response<String> r = companyEventService.createEvent(
                validToken,
                companyId,
                LocalDateTime.now().plusDays(5),      //eventDate
                "Test Event",
                LocalDateTime.now().plusHours(2),     //saleStartDate
                true,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        eventId = r.getValue();

        stage = new ElementPositionDTO(10, 20);
        entries = List.of(new ElementPositionDTO(0, 0), new ElementPositionDTO(50, 10));
        standingZones = List.of(new StandingZoneDTO(200, "floor", 100.0, new ElementPositionDTO(1, 1)));
        seatingZones = List.of(new SeatingZoneDTO(10, 20, "tribune", 150.0, new ElementPositionDTO(5, 5)));

        companyEventService.DefineVenueAndSeatingMap(validToken, eventId,
                stage, entries, standingZones, seatingZones);

        LotteryService lotteryService = new LotteryService(lotteryRepo, eventRepo, auth);
        lotteryService.createLottery(validToken, eventId, 10,
                LocalDateTime.now().plusHours(1),     //registerWindow
                5);

        service = new ActiveOrderService(auth, activeOrderRepo, eventRepo, companyRepo, lotteryRepo, capacity);
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
    void GivenConcurrentUsersBelowCapacity_WhenEnterPurchase_ThenAllReceiveMap() throws Exception {
        int usersCount = 10;

        Response<String> eventResponse = companyEventService.createEvent(
                validToken,
                companyId,
                LocalDateTime.now().plusDays(5),          // eventDate
                "Concurrent Event Below Capacity",
                LocalDateTime.now().minusMinutes(10),     // saleStartDate already started
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        String concurrentEventId = eventResponse.getValue();

        companyEventService.DefineVenueAndSeatingMap(validToken, concurrentEventId,
                stage, entries, standingZones, seatingZones);

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < usersCount; i++) {
            String email = "concurrent" + i + "@mail.com";
            UserService us = new UserService(tokenService, auth, userRepo, passwordEncoder);

            us.registerUser("", new UserDTO(
                    email, "f" + i, "l" + i, "pass",
                    1, 1, 2000, "Israel", "050-427-320" + i
            ));

            tokens.add(us.login(email, "pass").getValue());
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
}