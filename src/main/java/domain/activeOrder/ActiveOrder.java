package domain.activeOrder;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.time.Duration;

@Entity
@Table(name = "active_orders")
public class ActiveOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "user_identifier", nullable = false)
    private String userIdentifier;

    @Column(name = "event_id")
    private Integer eventId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "active_order_tickets",
            joinColumns = @JoinColumn(name = "order_id")
    )
    @Column(name = "ticket_id")
    private List<Integer> tickets;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "checkout_started_at")
    private LocalDateTime checkoutStartedAt;

    @Version
    @Column(name = "version")
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private STAGE stage;

    @Column(name = "approved_checkout_price")
    private Double approvedCheckoutPrice;

    protected ActiveOrder() {
    }

    public ActiveOrder(String userIdentifier, Integer eventId, List<Integer> tickets) {
        this.orderId = null;
        this.userIdentifier = userIdentifier;
        this.eventId = eventId;
        this.tickets = tickets == null ? new ArrayList<>() : new ArrayList<>(tickets);
        this.version = 0;
        this.createdAt = LocalDateTime.now();
        this.checkoutStartedAt = null;
        this.stage = STAGE.SELECTING_TICKETS;
        this.approvedCheckoutPrice = null;
    }

    public ActiveOrder(int orderId, String userIdentifier, Integer eventId, List<Integer> tickets) {
        this(userIdentifier, eventId, tickets);
        this.orderId = orderId;
    }

    public ActiveOrder(ActiveOrder activeOrder) {
        this.orderId = activeOrder.orderId;
        this.userIdentifier = activeOrder.userIdentifier;
        this.eventId = activeOrder.eventId;
        this.tickets = activeOrder.tickets == null ? new ArrayList<>() : new ArrayList<>(activeOrder.tickets);
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

    public Integer getId() {
        return orderId;
    }

    public void setId(Integer orderId) {
        this.orderId = orderId;
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
        return Objects.equals(orderId, other.orderId) && version == other.getVersion();
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, version);
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
    }

    public void confirmEdit() {
        if (stage == STAGE.EDITING) {
            stage = STAGE.CHECKING_OUT;
        }
    }

    public LocalDateTime getCheckoutWarningTime(int checkoutTimeoutMinutes, int warningBeforeExpiryMinutes) {
        if ((stage == STAGE.CHECKING_OUT || stage == STAGE.EDITING) && checkoutStartedAt != null) {
            return checkoutStartedAt.plusMinutes(checkoutTimeoutMinutes - warningBeforeExpiryMinutes);
        }
        return null;
    }

    public boolean isExpired(LocalDateTime now, int selectingTimeoutMinutes, int checkoutTimeoutMinutes) {
        if (stage == STAGE.SELECTING_TICKETS) {
            return createdAt.plusMinutes(selectingTimeoutMinutes).isBefore(now);
        }
        if (stage == STAGE.CHECKING_OUT || stage == STAGE.EDITING) {
            return checkoutStartedAt.plusMinutes(checkoutTimeoutMinutes).isBefore(now);
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

    public void forceExpireForTest(LocalDateTime now, int checkoutTimeoutMinutes) {
        if (stage == STAGE.SELECTING_TICKETS) {
            this.createdAt = now.minusMinutes(6);
        } else if (stage == STAGE.CHECKING_OUT || stage == STAGE.EDITING) {
            this.checkoutStartedAt = now.minusMinutes(checkoutTimeoutMinutes + 1);
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

    public long getRemainingCheckoutSeconds(LocalDateTime now, int checkoutTimeoutMinutes) {
        if (checkoutStartedAt == null) {
            return 0;
        }

        if (stage != STAGE.CHECKING_OUT && stage != STAGE.EDITING) {
            return 0;
        }

        LocalDateTime expiresAt = checkoutStartedAt.plusMinutes(checkoutTimeoutMinutes);

        return Math.max(0, Duration.between(now, expiresAt).getSeconds());
    }
}
