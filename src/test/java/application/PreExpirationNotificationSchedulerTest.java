package application;

import DTO.NotifyDTO;
import DTO.NotifyType;
import app.config.SystemProperties;
import com.vaadin.flow.shared.Registration;
import app.config.ActiveOrderProperties;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreExpirationNotificationSchedulerTest {

    private static final int ORDER_ID = 42;
    private static final String RECIPIENT = "buyer@mail.com";
    private static final int EVENT_ID = 7;
    private static final int SELECTING_TIMEOUT_MINUTES = 5;
    private static final int CHECKOUT_TIMEOUT_MINUTES = 10;
    private static final int WARNING_BEFORE_EXPIRY_MINUTES = 1;

    private IActiveOrderRepo activeOrderRepo;
    private PreExpirationNotificationScheduler scheduler;
    private BlockingQueue<NotifyDTO> delivered;
    private Registration recipientRegistration;
    private String tabToken;

    @BeforeEach
    void setUp() {
        TokenService tokenService = new TokenService(createTestSystemProperties());

        SystemProperties systemProperties = createTestSystemProperties();
        tokenService = new TokenService(systemProperties);
        new RetryHelper(systemProperties);

        AccessCodeGenerator.configure(
                "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                6
        );
        activeOrderRepo = new ActiveOrderRepoImpl();
        IAuth auth = new Auth(tokenService);
        ActiveOrderProperties activeOrderProperties = new ActiveOrderProperties();
        activeOrderProperties.setCapacity(20);
        activeOrderProperties.setSelectingTimeoutMinutes(SELECTING_TIMEOUT_MINUTES);
        activeOrderProperties.setCheckoutTimeoutMinutes(CHECKOUT_TIMEOUT_MINUTES);
        activeOrderProperties.setWarningBeforeExpiryMinutes(WARNING_BEFORE_EXPIRY_MINUTES);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        scheduler = new PreExpirationNotificationScheduler(activeOrderRepo, new VaadinNotifier(), auth, activeOrderProperties, transactionTemplate);

        tabToken = tokenService.generateGuestToken();
        String userIdentifier = auth.getUserIdentifier(tabToken).getValue();

        delivered = new LinkedBlockingQueue<>();
        // Warnings now go to the user listener keyed by the token's identifier, not a tab listener.
        recipientRegistration = Broadcaster.registerUser(userIdentifier, delivered::add);
    }
    private SystemProperties createTestSystemProperties() {
        SystemProperties systemProperties = new SystemProperties();
        systemProperties.setMaxConcurrentUsers(50);
        systemProperties.setInitStateFile("classpath:init-state.json");
        systemProperties.setAccessCodeChars("ABCDEFGHJKMNPQRSTUVWXYZ23456789");
        systemProperties.setAccessCodeLength(6);
        systemProperties.setTokenExpirationHours(24);
        systemProperties.setRetryCount(50);
        systemProperties.setRetryJitterMaxMs(50);

        return systemProperties;
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
        order.forceExpireForTest(LocalDateTime.now().plusMinutes(2), CHECKOUT_TIMEOUT_MINUTES);
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
        order.forceExpireForTest(LocalDateTime.now().plusMinutes(2), CHECKOUT_TIMEOUT_MINUTES); // inside the warning window
        activeOrderRepo.store(order);

        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,LocalDateTime.now().plusSeconds(1));
        scheduler.cancel(ORDER_ID);

        assertFalse(scheduler.hasPendingWarning(ORDER_ID));
        assertNull(delivered.poll(1500, TimeUnit.MILLISECONDS));
    }

    @Test
    void GivenPendingWarning_WhenScheduleOrReschedule_ThenWarningSentExactlyOnce() throws InterruptedException {
        ActiveOrder order = checkingOutOrder();
        order.forceExpireForTest(LocalDateTime.now().plusMinutes(2), CHECKOUT_TIMEOUT_MINUTES); // inside the warning window
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
        order.forceExpireForTest(LocalDateTime.now(), CHECKOUT_TIMEOUT_MINUTES); // deadline lands at now-1min -> already expired
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
        order.forceExpireForTest(LocalDateTime.now().plusMinutes(2), CHECKOUT_TIMEOUT_MINUTES);
        activeOrderRepo.store(order);

        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,LocalDateTime.now().plusSeconds(1));
        scheduler.scheduleOrReschedule(tabToken, ORDER_ID,null); // order no longer CHECKING_OUT -> clear

        assertFalse(scheduler.hasPendingWarning(ORDER_ID));
        assertNull(delivered.poll(1500, TimeUnit.MILLISECONDS));
    }

    // ===================== rescheduleOnStartup =====================

    @Test
    void GivenNullWarningTime_WhenRescheduleOnStartup_ThenPendingWarningCleared() throws InterruptedException {
        ActiveOrder order = checkingOutOrder();
        order.forceExpireForTest(LocalDateTime.now().plusMinutes(2), CHECKOUT_TIMEOUT_MINUTES);
        activeOrderRepo.store(order);

        scheduler.scheduleOrReschedule(tabToken, ORDER_ID, LocalDateTime.now().plusSeconds(1));
        assertTrue(scheduler.hasPendingWarning(ORDER_ID));

        scheduler.rescheduleOnStartup(RECIPIENT, ORDER_ID, null);

        assertFalse(scheduler.hasPendingWarning(ORDER_ID));
        assertNull(delivered.poll(1200, TimeUnit.MILLISECONDS));
    }

    @Test
    void GivenPastWarningTime_WhenRescheduleOnStartup_ThenPendingWarningCleared() throws InterruptedException {
        ActiveOrder order = checkingOutOrder();
        order.forceExpireForTest(LocalDateTime.now().plusMinutes(2), CHECKOUT_TIMEOUT_MINUTES);
        activeOrderRepo.store(order);

        scheduler.scheduleOrReschedule(tabToken, ORDER_ID, LocalDateTime.now().plusSeconds(1));
        assertTrue(scheduler.hasPendingWarning(ORDER_ID));

        scheduler.rescheduleOnStartup(RECIPIENT, ORDER_ID, LocalDateTime.now().minusSeconds(1));

        assertFalse(scheduler.hasPendingWarning(ORDER_ID));
        assertNull(delivered.poll(1200, TimeUnit.MILLISECONDS));
    }

    @Test
    void GivenFutureWarningTimeAndOrderInWindow_WhenRescheduleOnStartup_ThenNotificationSentViaIdentifier()
            throws InterruptedException {
        BlockingQueue<NotifyDTO> identifierQueue = new LinkedBlockingQueue<>();
        Registration reg = Broadcaster.registerUser(RECIPIENT, identifierQueue::add);
        try {
            ActiveOrder order = checkingOutOrder();
            order.forceExpireForTest(LocalDateTime.now().plusMinutes(2), CHECKOUT_TIMEOUT_MINUTES); // puts order in warning window
            activeOrderRepo.store(order);

            // schedule via startup path to fire in ~50 ms
            scheduler.rescheduleOnStartup(RECIPIENT, ORDER_ID, LocalDateTime.now().plus(50, java.time.temporal.ChronoUnit.MILLIS));
            assertTrue(scheduler.hasPendingWarning(ORDER_ID));

            NotifyDTO sent = identifierQueue.poll(2, TimeUnit.SECONDS);
            assertNotNull(sent, "startup recovery must send warning via identifier");
            assertEquals(NotifyType.GENERAL_POPUP, sent.getType());
            assertEquals(EVENT_ID, sent.getPayload().getEventId().intValue());
        } finally {
            reg.remove();
        }
    }

    @Test
    void GivenFutureWarningTimeAndOrderNotInRepo_WhenRescheduleOnStartup_ThenNothingSent()
            throws InterruptedException {
        BlockingQueue<NotifyDTO> identifierQueue = new LinkedBlockingQueue<>();
        Registration reg = Broadcaster.registerUser(RECIPIENT, identifierQueue::add);
        try {
            // order is NOT stored in repo → fireWarningForIdentifier will catch NoSuchElementException
            scheduler.rescheduleOnStartup(RECIPIENT, ORDER_ID, LocalDateTime.now().plus(50, java.time.temporal.ChronoUnit.MILLIS));
            assertTrue(scheduler.hasPendingWarning(ORDER_ID));

            assertNull(identifierQueue.poll(800, TimeUnit.MILLISECONDS),
                    "no notification must be sent when order is missing from repo");
        } finally {
            reg.remove();
        }
    }

    @Test
    void GivenFutureWarningTimeButOrderExpired_WhenRescheduleOnStartup_ThenNothingSent()
            throws InterruptedException {
        BlockingQueue<NotifyDTO> identifierQueue = new LinkedBlockingQueue<>();
        Registration reg = Broadcaster.registerUser(RECIPIENT, identifierQueue::add);
        try {
            ActiveOrder order = checkingOutOrder();
            order.forceExpireForTest(LocalDateTime.now(), CHECKOUT_TIMEOUT_MINUTES); // expired now
            activeOrderRepo.store(order);

            scheduler.rescheduleOnStartup(RECIPIENT, ORDER_ID, LocalDateTime.now().plus(50, java.time.temporal.ChronoUnit.MILLIS));

            assertNull(identifierQueue.poll(800, TimeUnit.MILLISECONDS),
                    "expired order must not trigger a warning");
        } finally {
            reg.remove();
        }
    }
}