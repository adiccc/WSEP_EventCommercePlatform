package application;

import DTO.NotifyDTO;
import DTO.NotifyType;
import domain.activeOrder.ActiveOrder;
import domain.activeOrder.IActiveOrderRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreExpirationNotificationSchedulerTest {

    private static final int ORDER_ID = 42;
    private static final String RECIPIENT = "buyer@mail.com";
    private static final int EVENT_ID = 7;

    private IActiveOrderRepo activeOrderRepo;
    private INotifier notifier;
    private PreExpirationNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        activeOrderRepo = mock(IActiveOrderRepo.class);
        notifier = mock(INotifier.class);
        scheduler = new PreExpirationNotificationScheduler(activeOrderRepo, notifier);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    /** A CHECKING_OUT order whose 10-minute timer is currently inside its final minute. */
    private ActiveOrder orderInWarningWindow() {
        ActiveOrder order = mock(ActiveOrder.class);
        when(order.getCheckoutWarningTime()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(order.isExpired(any(LocalDateTime.class))).thenReturn(false);
        when(order.getUserIdentifier()).thenReturn(RECIPIENT);
        when(order.getEventId()).thenReturn(EVENT_ID);
        return order;
    }

    @Test
    void GivenOrderInWarningWindow_WhenScheduleOrReschedule_ThenRealTimePopupSentToOrderIdentifier() {
        ActiveOrder order = orderInWarningWindow();
        when(activeOrderRepo.findById(ORDER_ID)).thenReturn(order);

        // warningTime already passed -> the one-shot task runs immediately
        scheduler.scheduleOrReschedule(ORDER_ID, LocalDateTime.now().minusSeconds(1));

        ArgumentCaptor<NotifyDTO> captor = ArgumentCaptor.forClass(NotifyDTO.class);
        verify(notifier, timeout(2000)).notifyUser(eq(RECIPIENT), captor.capture());
        verify(notifier, never()).notifyTab(anyString(), any()); // real-time popup, not tab routing

        NotifyDTO sent = captor.getValue();
        assertEquals(NotifyType.GENERAL_POPUP, sent.getType());
        assertEquals(EVENT_ID, sent.getPayload().getEventId().intValue());
    }

    @Test
    void GivenScheduledWarning_WhenCancel_ThenNothingIsSent() {
        ActiveOrder order = orderInWarningWindow();
        when(activeOrderRepo.findById(ORDER_ID)).thenReturn(order);

        scheduler.scheduleOrReschedule(ORDER_ID, LocalDateTime.now().plusSeconds(1));
        scheduler.cancel(ORDER_ID);

        verify(notifier, after(1500).never()).notifyUser(anyString(), any());
    }

    @Test
    void GivenPendingWarning_WhenScheduleOrReschedule_ThenWarningSentExactlyOnce() {
        ActiveOrder order = orderInWarningWindow();
        when(activeOrderRepo.findById(ORDER_ID)).thenReturn(order);

        // first task ~1s out, then reschedule to fire now; the first must be cancelled
        scheduler.scheduleOrReschedule(ORDER_ID, LocalDateTime.now().plusSeconds(1));
        scheduler.scheduleOrReschedule(ORDER_ID, LocalDateTime.now().minusSeconds(1));

        // wait past the original deadline: exactly one send must have happened
        verify(notifier, after(1500).times(1)).notifyUser(eq(RECIPIENT), any());
    }

    @Test
    void GivenOrderNotYetInWarningWindow_WhenScheduleOrReschedule_ThenSkippedWithoutSending() {
        ActiveOrder order = mock(ActiveOrder.class);
        when(order.getCheckoutWarningTime()).thenReturn(LocalDateTime.now().plusMinutes(5));
        when(order.isExpired(any(LocalDateTime.class))).thenReturn(false);
        when(activeOrderRepo.findById(ORDER_ID)).thenReturn(order);

        scheduler.scheduleOrReschedule(ORDER_ID, LocalDateTime.now().minusSeconds(1));

        verify(notifier, after(800).never()).notifyUser(anyString(), any());
    }

    @Test
    void GivenOrderAlreadyExpired_WhenScheduleOrReschedule_ThenSkippedWithoutSending() {
        ActiveOrder order = mock(ActiveOrder.class);
        when(order.getCheckoutWarningTime()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(order.isExpired(any(LocalDateTime.class))).thenReturn(true);
        when(activeOrderRepo.findById(ORDER_ID)).thenReturn(order);

        scheduler.scheduleOrReschedule(ORDER_ID, LocalDateTime.now().minusSeconds(1));

        verify(notifier, after(800).never()).notifyUser(anyString(), any());
    }

    @Test
    void GivenOrderAlreadyRemoved_WhenScheduleOrReschedule_ThenSkippedWithoutSending() {
        when(activeOrderRepo.findById(ORDER_ID)).thenThrow(new NoSuchElementException("gone"));

        scheduler.scheduleOrReschedule(ORDER_ID, LocalDateTime.now().minusSeconds(1));

        verify(notifier, after(800).never()).notifyUser(anyString(), any());
    }

    @Test
    void GivenNullWarningTime_WhenScheduleOrReschedule_ThenAnyPendingWarningIsCleared() {
        ActiveOrder order = orderInWarningWindow();
        when(activeOrderRepo.findById(ORDER_ID)).thenReturn(order);

        scheduler.scheduleOrReschedule(ORDER_ID, LocalDateTime.now().plusSeconds(1));
        scheduler.scheduleOrReschedule(ORDER_ID, null); // order no longer CHECKING_OUT -> clear

        verify(notifier, after(1500).never()).notifyUser(anyString(), any());
    }
}
