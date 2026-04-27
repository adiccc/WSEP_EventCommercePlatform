package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.activeOrder.ActiveOrder;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
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
import java.util.List;

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

    private int userId1;
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
    private final int capacity = 100;

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

        userId1 = userRepo.findUserByEmail("testuser1@gmail.com").getUserId();
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

        service = new ActiveOrderService(auth, activeOrderRepo, eventRepo, companyRepo, lotteryRepo);
    }

    @Test
    void GivenInvalidToken_WhenEnterPurchase_ThenErrorReturned() {
        Response<EventMap> response = service.enterEventPurchase("", companyId, eventId);

        assertNull(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenNonExistingEvent_WhenEnterPurchase_ThenEventNotFound() {
        Response<EventMap> response = service.enterEventPurchase(validToken, companyId, "bad-id");

        assertNull(response.getValue());
        assertEquals("Event not found", response.getMessage());
    }

    @Test
    void GivenWrongCompany_WhenEnterPurchase_ThenMismatchError() {
        Response<EventMap> response = service.enterEventPurchase(validToken, 999, eventId);

        assertNull(response.getValue());
        assertEquals("The selected event does not belong to the company", response.getMessage());
    }

    @Test
    void GivenFutureSaleWithoutLottery_WhenEnterPurchase_ThenSaleNotStarted() {
        Response<String> r = companyEventService.createEvent(
                validToken,
                companyId,
                LocalDateTime.now().plusDays(5),
                "Future Event",
                LocalDateTime.now().plusHours(2),
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        String futureEventId = r.getValue();

        companyEventService.DefineVenueAndSeatingMap(validToken, futureEventId,
                stage, entries, standingZones, seatingZones);

        Event futureEvent = eventRepo.findById(futureEventId);
        futureEvent.setActive(true);
        eventRepo.store(futureEvent);

        Response<EventMap> response = service.enterEventPurchase(validToken, companyId, futureEventId);

        assertNull(response.getValue());
        assertEquals("The sale for this event has not started yet", response.getMessage());
    }
}