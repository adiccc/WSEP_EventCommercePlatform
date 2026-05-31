package application;

import DTO.NotifyDTO;
import DTO.NotifyPayload;
import DTO.NotifyType;
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

/**
 * Schedules the one-minute "your checkout is about to expire" warning for active
 * orders sitting in the CHECKING_OUT stage. Each order gets a single one-shot task
 * fired at {@link ActiveOrder#getCheckoutWarningTime()}; editing tickets reschedules
 * it, and completing/expiring an order cancels it.
 *
 * Depends only on the {@link IActiveOrderRepo} interface so it stays decoupled from
 * the concrete (currently in-memory) repository implementation.
 */
@Component
public class PreExpirationNotificationScheduler {

    private static final Logger logger =
            Logger.getLogger(PreExpirationNotificationScheduler.class.getName());

    private final IActiveOrderRepo activeOrderRepo;
    private final INotifier notifier;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Integer, ScheduledFuture<?>> scheduledWarnings;

    @Autowired
    public PreExpirationNotificationScheduler(IActiveOrderRepo activeOrderRepo, INotifier notifier) {
        this.activeOrderRepo = activeOrderRepo;
        this.notifier = notifier;
        this.scheduledWarnings = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "active-order-prewarning");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Schedules (or reschedules) the pre-expiration warning for an order. Any warning
     * already pending for this order is cancelled first, so this is also the reschedule
     * path used after the checkout timer restarts on an edit.
     *
     * @param warningTime the instant to fire, i.e. {@link ActiveOrder#getCheckoutWarningTime()};
     *                    a null value (order not in CHECKING_OUT) simply clears the schedule.
     */
    public void scheduleOrReschedule(int orderId, LocalDateTime warningTime) {
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
            return scheduler.schedule(() -> fireWarning(id), delayMillis, TimeUnit.MILLISECONDS);
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

    private void fireWarning(int orderId) {
        ActiveOrder order;
        try {
            order = activeOrderRepo.findById(orderId);
        } catch (NoSuchElementException e) {
            return; // order already completed/expired and removed — nothing to warn about
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningTime = order.getCheckoutWarningTime();

        // Fire only inside the [warningTime, deadline) window of the CURRENT checkout timer.
        // This rejects orders that have left CHECKING_OUT (e.g. payment started) and any
        // stale task left over from a reschedule whose deadline has since moved later.
        if (warningTime == null || now.isBefore(warningTime) || order.isExpired(now)) {
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
            // Real-time only (never the delayed-notification queue). The identifier is the
            // member email / guest token (order.getUserIdentifier()), matching how the UI
            // registers its Broadcaster listener, so guests receive it too. Best-effort.
            notifier.notifyUser(order.getUserIdentifier(), warning);
        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "Failed to send pre-expiration warning for order " + orderId + ": " + e.getMessage());
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
