package domain.activeOrder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ActiveOrder {
    private final int orderId;
    private final String userIdentifier;
    private final Integer eventId;
    private List<Integer> tickets;
    private LocalDateTime createdAt;
    private LocalDateTime checkoutStartedAt;
    private long version;
    private STAGE stage;
    private Double approvedCheckoutPrice;
    private static final int SELECTING_TICKETS_TIMEOUT_MINUTES = 5;
    private static final int CHECKOUT_TIMEOUT_MINUTES = 2;
    private static final int WARNING_BEFORE_CHECKOUT_EXPIRY_MINUTES = 1;

    public ActiveOrder(int orderId, String userIdentifier, Integer eventId, List<Integer> tickets) {
        this.orderId = orderId;
        this.userIdentifier = userIdentifier;
        this.eventId = eventId;
        this.tickets = tickets;
        this.version = 0;
        this.createdAt = LocalDateTime.now();
        this.checkoutStartedAt = null;
        this.stage = STAGE.SELECTING_TICKETS;
        this.approvedCheckoutPrice = null;
    }

    public ActiveOrder(ActiveOrder activeOrder) {
        this.orderId = activeOrder.orderId;
        this.userIdentifier = activeOrder.userIdentifier;
        this.eventId = activeOrder.eventId;
        this.tickets = new ArrayList<>(activeOrder.tickets);
        this.createdAt = activeOrder.createdAt;
        this.checkoutStartedAt = activeOrder.checkoutStartedAt;
        this.version = activeOrder.version;
        this.stage = activeOrder.stage;
        this.approvedCheckoutPrice = activeOrder.approvedCheckoutPrice;

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
    public boolean isExpired() {
        return isExpired(LocalDateTime.now());
    }

    public STAGE getStage() {
        return stage;
    }

    public void proceedToCheckout() {
        if (stage == STAGE.SELECTING_TICKETS) {
            stage = STAGE.CHECKING_OUT;
            this.checkoutStartedAt = LocalDateTime.now();
        }
    }

    public void returnToEditSelection() {
        if (stage == STAGE.CHECKING_OUT) {
            stage = STAGE.EDITING;
        }
        // checkoutStartedAt is deliberately NOT touched: the 10-minute seat-hold deadline
        // is continuous from the original lock and must not be extended by entering edit mode.
    }

    public void confirmEdit() {
        if (stage == STAGE.EDITING) {
            stage = STAGE.CHECKING_OUT;
        }
        // checkoutStartedAt is deliberately NOT touched: the timer keeps running against the
        // original lock instant, enforcing the hard 10-minute deadline "in any case".
    }

    public LocalDateTime getCheckoutWarningTime() {
        if ((stage == STAGE.CHECKING_OUT || stage == STAGE.EDITING) && checkoutStartedAt != null) {
            return checkoutStartedAt.plusMinutes(
                    CHECKOUT_TIMEOUT_MINUTES - WARNING_BEFORE_CHECKOUT_EXPIRY_MINUTES);
        }
        return null;
    }

    public boolean isExpired(LocalDateTime now) {
        if (stage == STAGE.SELECTING_TICKETS) {
            return createdAt.plusMinutes(SELECTING_TICKETS_TIMEOUT_MINUTES).isBefore(now);
        }
        if (stage == STAGE.CHECKING_OUT || stage == STAGE.EDITING) {
            return checkoutStartedAt.plusMinutes(CHECKOUT_TIMEOUT_MINUTES).isBefore(now);
        }
        return false;
    }

    public void setTickets(List<Integer> newTickets) {
        this.tickets = new ArrayList<>(newTickets);
        clearApprovedCheckoutPrice();
    }

    public LocalDateTime getCheckoutStartedAt() {
        return checkoutStartedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void forceExpireForTest(LocalDateTime now) {
        if (stage == STAGE.SELECTING_TICKETS) {
            this.createdAt = now.minusMinutes(6);
        } else if (stage == STAGE.CHECKING_OUT || stage == STAGE.EDITING) {
            this.checkoutStartedAt = now.minusMinutes(11);
        }
    }
        public void startPayment() {
        if (stage == STAGE.PAYMENT_IN_PROGRESS) {
            throw new IllegalStateException("Payment already in progress");
        }

        if (stage != STAGE.CHECKING_OUT) {
            throw new IllegalStateException("Active order is not ready for payment");
        }

        stage = STAGE.PAYMENT_IN_PROGRESS;
    }

    public void returnToCheckout() {
        if (stage == STAGE.PAYMENT_IN_PROGRESS) {
            stage = STAGE.CHECKING_OUT;
        }
    }
    
    public String getUserIdentifier() {
        return userIdentifier;
    }

    public void setApprovedCheckoutPrice(double approvedCheckoutPrice) {
        if (approvedCheckoutPrice < 0) {
            throw new IllegalArgumentException("Approved checkout price cannot be negative");
        }

        this.approvedCheckoutPrice = approvedCheckoutPrice;
    }

    public boolean hasApprovedCheckoutPrice() {
        return approvedCheckoutPrice != null;
    }

    public double getApprovedCheckoutPrice() {
        if (approvedCheckoutPrice == null) {
            throw new IllegalStateException("Checkout price was not approved");
        }

        return approvedCheckoutPrice;
    }

    public void clearApprovedCheckoutPrice() {
        this.approvedCheckoutPrice = null;
    }
}