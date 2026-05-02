package domain.activeOrder;

import java.time.LocalDateTime;
import domain.event.Event;

import java.util.ArrayList;
import java.util.List;

public class ActiveOrder {
    private final int orderId;
    private final int userId;
    private final Integer eventId;
    private List<Integer> tickets;
    private final LocalDateTime createdAt;
    private LocalDateTime checkoutStartedAt;
    private long version;
    private STAGE stage;

    public ActiveOrder(int orderId, int userId, Integer eventId, List<Integer> tickets) {
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = tickets;
        this.version = 0;
        this.createdAt = LocalDateTime.now();
        this.checkoutStartedAt = null;
        this.stage = STAGE.VIEWING_MAP;
    }

    public ActiveOrder(ActiveOrder activeOrder) {
        this.orderId = activeOrder.orderId;
        this.userId = activeOrder.userId;
        this.eventId = activeOrder.eventId;
        this.tickets = new ArrayList<>(activeOrder.tickets);
        this.createdAt = activeOrder.createdAt;
        this.checkoutStartedAt = activeOrder.checkoutStartedAt;
        this.version = activeOrder.version;
        this.stage = activeOrder.stage;

    }

    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }

    public int getId() {
        return orderId;
    }

    public Integer getEventId() {
        return eventId;
    }

    public int getUserId() {
        return userId;
    }

    public List<Integer> getTickets() {
        return new ArrayList<>(tickets);
    }

    public boolean hasTickets() {
        return tickets != null && !tickets.isEmpty();
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ActiveOrder other = (ActiveOrder) obj;
        return orderId==other.orderId && version == other.getVersion();
    }

    public STAGE getStage() {
        return stage;
    }

    public void proceedToCheckout() {
        if (stage == STAGE.SELECTING_TICKETS) {
            stage = STAGE.CHECKING_OUT;
        }
    }

    public void proceedToSelectingTickets() {
        stage = STAGE.SELECTING_TICKETS;
        this.checkoutStartedAt = LocalDateTime.now();
    }

    public void returnToSelecting() {
        // todo: call when return to selecting tickets from checkout
        if (stage == STAGE.CHECKING_OUT) {
            stage = STAGE.SELECTING_TICKETS;
        }
    }

    public boolean isExpired(LocalDateTime now) {
        if (stage == STAGE.VIEWING_MAP) {
            return createdAt.plusMinutes(5).isBefore(now);
        }

        if (stage == STAGE.SELECTING_TICKETS) {
            return checkoutStartedAt.plusMinutes(10).isBefore(now);
        }

        return false;
    }

    public void setTickets(List<Integer> newTickets) {
        this.tickets = new ArrayList<>(newTickets);
    }

    public LocalDateTime getCheckoutStartedAt() {
        return checkoutStartedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}