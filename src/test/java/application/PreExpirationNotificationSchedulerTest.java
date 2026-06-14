package application;

import DTO.NotifyDTO;
import DTO.NotifyType;
import com.vaadin.flow.shared.Registration;
import domain.activeOrder.ActiveOrder;
import domain.activeOrder.IActiveOrderRepo;
import domain.lottery.AccessCodeGenerator;
import infrastructure.inMemory.ActiveOrderRepoImpl;
import infrastructure.Auth;
import infrastructure.Broadcaster;
import infrastructure.VaadinNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PreExpirationNotificationSchedulerTest {

    private static final int ORDER_ID = 42;
    private static final String RECIPIENT = "buyer@mail.com";
    private static final int EVENT_ID = 7;

    private IActiveOrderRepo activeOrderRepo;
    private PreExpirationNotificationScheduler scheduler;
    private BlockingQueue<NotifyDTO> delivered;
    private Registration recipientRegistration;
    private String tabToken;

    @BeforeEach
    void setUp() {
        AccessCodeGenerator.configure(
                "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                6
        );
        activeOrderRepo = new ActiveOrderRepoImpl();
        TokenService tokenService = new TokenService();
        IAuth auth = new Auth(tokenService);
        scheduler = new PreExpirationNotificationScheduler(activeOrderRepo, new VaadinNotifier(), auth);

        tabToken = tokenService.generateGuestToken();
        String userIdentifier = auth.getUserIdentifier(tabToken).getValue();

        delivered = new LinkedBlockingQueue<>();
        // Warnings now go to the user listener keyed by the token's identifier, not a tab listener.
        recipientRegistration = Broadcaster.registerUser(userIdentifier, delivered::add);
    }

    @AfterEach
    void tearDown() {
        recipientRegistration.remove();
        scheduler.shutdown();
    }

    /** A real order, freshly moved into CHECKING_OUT (its 10-minute timer starts now). */
    private ActiveOrder checkingOutOrder() {
        ActiveOrder order = new ActiveOrder(ORDER_ID, RECIPIENT, EVENT_ID, List.of(1));
        order.proceedToCheckout();
        return order;
    }

    @Test
    void GivenOrderInWarningWindow_WhenScheduleOrReschedule_ThenRealTimePopupSentToOrderIdentifier()
            throws InterruptedException {
        ActiveOrder order = checkingOutOrder();
        order.forceExpireForTest(LocalDateTime.now().plusMinutes(2));
        activeOrderRepo.store(order);

        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,LocalDateTime.now().minusSeconds(1)); // fire immediately

        NotifyDTO sent = delivered.poll(2, TimeUnit.SECONDS);
        assertNotNull(sent, "a real-time popup should have been delivered to the recipient");
        assertEquals(NotifyType.GENERAL_POPUP, sent.getType());
        assertEquals(EVENT_ID, sent.getPayload().getEventId().intValue());
    }

    @Test
    void GivenScheduledWarning_WhenCancel_ThenNothingIsSent() throws InterruptedException {
        ActiveOrder order = checkingOutOrder();
        order.forceExpireForTest(LocalDateTime.now().plusMinutes(2)); // inside the warning window
        activeOrderRepo.store(order);

        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,LocalDateTime.now().plusSeconds(1));
        scheduler.cancel(ORDER_ID);

        assertFalse(scheduler.hasPendingWarning(ORDER_ID));
        assertNull(delivered.poll(1500, TimeUnit.MILLISECONDS));
    }

    @Test
    void GivenPendingWarning_WhenScheduleOrReschedule_ThenWarningSentExactlyOnce() throws InterruptedException {
        ActiveOrder order = checkingOutOrder();
        order.forceExpireForTest(LocalDateTime.now().plusMinutes(2)); // inside the warning window
        activeOrderRepo.store(order);

        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,LocalDateTime.now().plusSeconds(1));  // would fire at +1s
        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,LocalDateTime.now().minusSeconds(1)); // reschedule: fire now

        assertNotNull(delivered.poll(2, TimeUnit.SECONDS), "the rescheduled warning should fire");
        // the original +1s task must have been cancelled by the reschedule -> no second delivery
        assertNull(delivered.poll(1500, TimeUnit.MILLISECONDS));
    }

    @Test
    void GivenOrderNotYetInWarningWindow_WhenScheduleOrReschedule_ThenSkippedWithoutSending()
            throws InterruptedException {
        activeOrderRepo.store(checkingOutOrder()); // checkoutStartedAt = now -> warning is 9 min away

        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,LocalDateTime.now().minusSeconds(1));

        assertNull(delivered.poll(800, TimeUnit.MILLISECONDS));
    }

    @Test
    void GivenOrderAlreadyExpired_WhenScheduleOrReschedule_ThenSkippedWithoutSending() throws InterruptedException {
        ActiveOrder order = checkingOutOrder();
        order.forceExpireForTest(LocalDateTime.now()); // deadline lands at now-1min -> already expired
        activeOrderRepo.store(order);

        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,LocalDateTime.now().minusSeconds(1));

        assertNull(delivered.poll(800, TimeUnit.MILLISECONDS));
    }

    @Test
    void GivenOrderAlreadyRemoved_WhenScheduleOrReschedule_ThenSkippedWithoutSending() throws InterruptedException {
        // order never stored -> repo.findById throws NoSuchElementException, so the warning is skipped
        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,LocalDateTime.now().minusSeconds(1));

        assertNull(delivered.poll(800, TimeUnit.MILLISECONDS));
    }

    @Test
    void GivenNullWarningTime_WhenScheduleOrReschedule_ThenAnyPendingWarningIsCleared() throws InterruptedException {
        ActiveOrder order = checkingOutOrder();
        order.forceExpireForTest(LocalDateTime.now().plusMinutes(2));
        activeOrderRepo.store(order);

        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,LocalDateTime.now().plusSeconds(1));
        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,null); // order no longer CHECKING_OUT -> clear

        assertFalse(scheduler.hasPendingWarning(ORDER_ID));
        assertNull(delivered.poll(1500, TimeUnit.MILLISECONDS));
    }
}