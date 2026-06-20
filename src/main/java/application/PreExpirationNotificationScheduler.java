package application;

import DTO.NotifyDTO;
import DTO.NotifyPayload;
import DTO.NotifyType;
import app.config.ActiveOrderProperties;
import domain.activeOrder.ActiveOrder;
import domain.activeOrder.IActiveOrderRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class PreExpirationNotificationScheduler {

    private static final Logger logger =
            Logger.getLogger(PreExpirationNotificationScheduler.class.getName());

    private final IActiveOrderRepo activeOrderRepo;
    private final INotifier notifier;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Integer, ScheduledFuture<?>> scheduledWarnings;
    private final IAuth auth;
    private final int selectingTimeoutMinutes;
    private final int checkoutTimeoutMinutes;
    private final int warningBeforeExpiryMinutes;

    @Autowired
    public PreExpirationNotificationScheduler(IActiveOrderRepo activeOrderRepo, INotifier notifier, IAuth auth, ActiveOrderProperties activeOrderProperties) {
        this.activeOrderRepo = activeOrderRepo;
        this.notifier = notifier;
        this.scheduledWarnings = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "active-order-prewarning");
            t.setDaemon(true);
            return t;
        });
        this.auth = auth;
        this.selectingTimeoutMinutes = activeOrderProperties.getSelectingTimeoutMinutes();
        this.checkoutTimeoutMinutes = activeOrderProperties.getCheckoutTimeoutMinutes();
        this.warningBeforeExpiryMinutes = activeOrderProperties.getWarningBeforeExpiryMinutes();
    }


    public void scheduleOrReschedule(String token,int orderId, LocalDateTime warningTime) {
        if (warningTime == null) {
            cancel(orderId);
            return;
        }

        long delayMillis = Math.max(0, Duration.between(LocalDateTime.now(), warningTime).toMillis());

        // compute() runs atomically for this key's bin only (no instance-wide lock), so we
        // cancel the old future and install the new one without other orders contending.
        scheduledWarnings.compute(orderId, (id, existing) -> {
            if (existing != null) {
                existing.cancel(false);
            }
            return scheduler.schedule(() -> fireWarning(token,id), delayMillis, TimeUnit.MILLISECONDS);
        });
    }

    // Token-free path for crash recovery (Member email as identifier). Only schedules when
    // the warning instant is still strictly in the future — never fires a late "1 minute left".
    public void rescheduleOnStartup(String userIdentifier, int orderId, LocalDateTime warningTime) {
        LocalDateTime now = LocalDateTime.now();
        if (warningTime == null || !warningTime.isAfter(now)) {
            cancel(orderId);
            return;
        }

        long delayMillis = Duration.between(now, warningTime).toMillis();

        scheduledWarnings.compute(orderId, (id, existing) -> {
            if (existing != null) {
                existing.cancel(false);
            }
            return scheduler.schedule(() -> fireWarningForIdentifier(userIdentifier, id), delayMillis, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * Cancels any pending warning for an order (called when the order is completed,
     * paid, or expired) so we don't leak scheduled tasks.
     */
    public void cancel(int orderId) {
        // computeIfPresent + returning null atomically cancels and removes the entry.
        scheduledWarnings.computeIfPresent(orderId, (id, existing) -> {
            existing.cancel(false);
            return null;
        });
    }

    /** Whether a pre-expiration warning is currently scheduled for the given order. */
    public boolean hasPendingWarning(int orderId) {
        return scheduledWarnings.containsKey(orderId);
    }

    private void fireWarning(String token,int orderId) {
        ActiveOrder order;
        try {
            order = activeOrderRepo.findById(orderId);
        } catch (NoSuchElementException e) {
            return; // order already completed/expired and removed — nothing to warn about
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningTime = order.getCheckoutWarningTime(checkoutTimeoutMinutes, warningBeforeExpiryMinutes);

        // Fire only inside the [warningTime, deadline) window of the CURRENT checkout timer.
        // This rejects orders that have left CHECKING_OUT (e.g. payment started) and any
        // stale task left over from a reschedule whose deadline has since moved later.
        if (warningTime == null || now.isBefore(warningTime) || order.isExpired(now, selectingTimeoutMinutes, checkoutTimeoutMinutes)) {
            return;
        }

        try {
            NotifyDTO warning = new NotifyDTO(
                    NotifyType.GENERAL_POPUP,
                    new NotifyPayload(
                            "Your selected seats are reserved for 1 more minute. "
                                    + "Please complete checkout to avoid losing them.",
                            order.getEventId(),
                            null));
            String identifier = auth.getUserIdentifier(token).getValue();
            notifier.notifyUser(identifier, warning);
        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "Failed to send pre-expiration warning for order " + orderId + ": " + e.getMessage());
        }
    }

    // Recovery variant: notifies the supplied (persistent) identifier directly, no token.
    private void fireWarningForIdentifier(String userIdentifier, int orderId) {
        ActiveOrder order;
        try {
            order = activeOrderRepo.findById(orderId);
        } catch (NoSuchElementException e) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningTime = order.getCheckoutWarningTime(checkoutTimeoutMinutes, warningBeforeExpiryMinutes);

        if (warningTime == null || now.isBefore(warningTime) || order.isExpired(now, selectingTimeoutMinutes, checkoutTimeoutMinutes)) {
            return;
        }

        try {
            NotifyDTO warning = new NotifyDTO(
                    NotifyType.GENERAL_POPUP,
                    new NotifyPayload(
                            "Your selected seats are reserved for 1 more minute. "
                                    + "Please complete checkout to avoid losing them.",
                            order.getEventId(),
                            null));
            notifier.notifyUser(userIdentifier, warning);
        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "Failed to send pre-expiration warning for order " + orderId + ": " + e.getMessage());
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
