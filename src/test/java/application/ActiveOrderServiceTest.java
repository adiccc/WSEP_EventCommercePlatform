package application;

import domain.activeOrder.ActiveOrder;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.event.*;
import domain.lottery.Lottery;
import domain.user.IUserRepo;
import domain.user.Member;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveOrderServiceIntegrationTest {

    private ActiveOrderService service;
    private IAuth auth;
    private EventRepoImpl eventRepo;
    private ActiveOrderRepoImpl activeOrderRepo;
    private CompanyRepoImpl companyRepo;
    private LotteryRepoImpl lotteryRepo;

    private String validToken;
    private Event event;
    private Member member;

    private final int companyId = 1;
    private final int userId = 123;
    private final int capacity = 100;

    @BeforeEach
    void setUp() {

        auth = mock(IAuth.class);

        IUserRepo userRepo = new UserRepo();

        validToken = "valid-token";

        member = new Member("email", "pass", "test", "user", "phone",
                LocalDate.now(), "address");

        userRepo.store(member);

        when(auth.isLoggedIn(validToken)).thenReturn(true);
        when(auth.getUserId(validToken)).thenReturn(member.getUserId());

        eventRepo = new EventRepoImpl();
        activeOrderRepo = new ActiveOrderRepoImpl();
        companyRepo = new CompanyRepoImpl();
        lotteryRepo = new LotteryRepoImpl();

        event = new Event(
                companyId,
                userId,
                LocalDateTime.now().plusDays(5),
                "Test Event",
                LocalDateTime.now().minusHours(1),
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        event.setActive(true);
        event.setMap(new EventMap(null, List.of(), List.of()));
        eventRepo.store(event);

        service = new ActiveOrderService(auth, activeOrderRepo, eventRepo, companyRepo, lotteryRepo);
    }

    @Test
    void GivenInvalidToken_WhenEnterPurchase_ThenErrorReturned() {
        Response<EventMap> response = service.enterEventPurchase("", companyId, event.getId());

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
        Response<EventMap> response = service.enterEventPurchase(validToken, 999, event.getId());

        assertNull(response.getValue());
        assertEquals("The selected event does not belong to the company", response.getMessage());
    }

    @Test
    void GivenInactiveEvent_WhenEnterPurchase_ThenError() {
        event.setActive(false);

        Response<EventMap> response = service.enterEventPurchase(validToken, companyId, event.getId());

        assertNull(response.getValue());
        assertEquals("The selected event is not active", response.getMessage());
    }

    @Test
    void GivenSaleNotStarted_WhenEnterPurchase_ThenError() {
        Event futureEvent = new Event(
                companyId,
                userId,
                LocalDateTime.now().plusDays(5),
                "Future Event",
                LocalDateTime.now().plusHours(2),
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        futureEvent.setActive(true);
        futureEvent.setMap(new EventMap(null, List.of(), List.of()));
        eventRepo.store(futureEvent);

        Response<EventMap> response = service.enterEventPurchase(validToken, companyId, futureEvent.getId());

        assertNull(response.getValue());
        assertEquals("The sale for this event has not started yet", response.getMessage());
    }

    @Test
    void GivenAvailableCapacity_WhenEnterPurchase_ThenMapReturned() {
        Response<EventMap> response = service.enterEventPurchase(validToken, companyId, event.getId());

        assertNotNull(response.getValue());
        assertEquals("Event map retrieved successfully", response.getMessage());
    }

    @Test
    void GivenFullCapacity_WhenEnterPurchase_ThenUserAddedToQueue() {
        // Fill capacity
        for (int i = 0; i < capacity; i++) {
            activeOrderRepo.store(new ActiveOrder(i, i, event.getId(), new ArrayList<>()));
        }

        Response<EventMap> response = service.enterEventPurchase(validToken, companyId, event.getId());

        assertNull(response.getValue());
        assertEquals("Event is full, user added to waiting queue", response.getMessage());
        assertTrue(event.getEventQueue().contains(validToken));
    }

    @Test
    void GivenUserInQueueNotFirst_WhenEnterPurchase_ThenStillWaiting() {
        for (int i = 0; i < capacity; i++) {
            activeOrderRepo.store(new ActiveOrder(i, i, event.getId(), new ArrayList<>()));
        }

        event.getEventQueue().enqueue("someoneElse");
        event.getEventQueue().enqueue(validToken);

        Response<EventMap> response = service.enterEventPurchase(validToken, companyId, event.getId());

        assertNull(response.getValue());
        assertEquals("User is still waiting in queue", response.getMessage());
    }

    @Test
    void GivenUserFirstInQueue_WhenCapacityFull_ThenAllowedToEnter() {
        for (int i = 0; i < capacity; i++) {
            activeOrderRepo.store(new ActiveOrder(i, i, event.getId(), new ArrayList<>()));
        }

        event.getEventQueue().enqueue(validToken);

        Response<EventMap> response = service.enterEventPurchase(validToken, companyId, event.getId());

        assertNotNull(response.getValue());
        assertEquals("Event map retrieved successfully", response.getMessage());
    }

    @Test
    void GivenLotteryAndUserNotWinner_WhenEnterPurchase_ThenBlocked() {
        Event lotteryEvent = new Event(
                companyId,
                userId,
                LocalDateTime.now().plusDays(5),
                "Lottery Event",
                LocalDateTime.now().minusHours(1),
                true,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        lotteryEvent.setActive(true);
        lotteryEvent.setMap(new EventMap(null, List.of(), List.of()));
        eventRepo.store(lotteryEvent);

        Lottery lottery = new Lottery(
                lotteryEvent.getId(),
                10,
                LocalDateTime.now().minusHours(2),
                5 // still open: saleStartDate + 5h > now
        );
        lotteryRepo.store(lottery);

        Response<EventMap> response = service.enterEventPurchase(validToken, companyId, lotteryEvent.getId());

        assertNull(response.getValue());
        assertEquals("User is not a lottery winner  and lottery registration is still open", response.getMessage());
    }

    @Test
    void GivenLotteryAndUserIsWinner_WhenEnterPurchase_ThenMapReturned() {
        Event lotteryEvent = new Event(
                companyId,
                userId,
                LocalDateTime.now().plusDays(5),
                "Lottery Event",
                LocalDateTime.now().minusHours(1),
                true,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        lotteryEvent.setActive(true);
        lotteryEvent.setMap(new EventMap(null, List.of(), List.of()));
        eventRepo.store(lotteryEvent);

        Lottery lottery = new Lottery(
                lotteryEvent.getId(),
                10,
                LocalDateTime.now().minusHours(2),
                5
        );

        lottery.getWinners().add(member.getUserId());
        lotteryRepo.store(lottery);

        Response<EventMap> response = service.enterEventPurchase(validToken, companyId, lotteryEvent.getId());

        assertNotNull(response.getValue());
        assertEquals("Event map retrieved successfully", response.getMessage());
    }

    @Test
    void GivenLotteryExpiredAndUserNotWinner_WhenEnterPurchase_ThenMapReturned() {
        Event lotteryEvent = new Event(
                companyId,
                userId,
                LocalDateTime.now().plusDays(5),
                "Lottery Event",
                LocalDateTime.now().minusHours(10),
                true,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        lotteryEvent.setActive(true);
        lotteryEvent.setMap(new EventMap(null, List.of(), List.of()));
        eventRepo.store(lotteryEvent);

        Lottery lottery = new Lottery(
                lotteryEvent.getId(),
                10,
                LocalDateTime.now().minusHours(12),
                2 // expired: saleStartDate + 2h < now
        );
        lotteryRepo.store(lottery);

        Response<EventMap> response = service.enterEventPurchase(validToken, companyId, lotteryEvent.getId());

        assertNotNull(response.getValue());
        assertEquals("Event map retrieved successfully", response.getMessage());
    }

}