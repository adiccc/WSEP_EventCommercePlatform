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

    // tickets is a small collection that is read in almost every ActiveOrder code
    // path (including the detached copies the repository hands back), so EAGER is a
    // justified exception to the LAZY-collections default.
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

    // Optimistic locking. Kept as a primitive long to stay consistent with the rest
    // of the domain (Lottery/Suspension) and the existing DTO/equals/in-memory repo.
    @Version
    @Column(name = "version")
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private STAGE stage;

    @Column(name = "approved_checkout_price")
    private Double approvedCheckoutPrice;

    private static int selectingTicketsTimeoutMinutes = 5;
    private static int checkoutTimeoutMinutes = 10;
    private static int warningBeforeCheckoutExpiryMinutes = 1;

    public static void configure(int selectingTimeout, int checkoutTimeout, int warningBeforeExpiry) {
        if (selectingTimeout > 0) {
            selectingTicketsTimeoutMinutes = selectingTimeout;
        }
        if (checkoutTimeout > 0) {
            checkoutTimeoutMinutes = checkoutTimeout;
        }
        if (warningBeforeExpiry > 0 && warningBeforeExpiry < checkoutTimeoutMinutes) {
            warningBeforeCheckoutExpiryMinutes = warningBeforeExpiry;
        }
    }

    // Required by JPA.
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

    // Explicit-id constructor, used by unit tests that need a deterministic id.
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

    // Used by the in-memory repository to assign a generated id on insert.
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
                    checkoutTimeoutMinutes - warningBeforeCheckoutExpiryMinutes);
        }
        return null;
    }

    public boolean isExpired(LocalDateTime now) {
        if (stage == STAGE.SELECTING_TICKETS) {
            return createdAt.plusMinutes(selectingTicketsTimeoutMinutes).isBefore(now);
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

    public void forceExpireForTest(LocalDateTime now) {
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

    public long getRemainingCheckoutSeconds(LocalDateTime now) {
        if (checkoutStartedAt == null) {
            return 0;
        }

        if (stage != STAGE.CHECKING_OUT && stage != STAGE.EDITING) {
            return 0;
        }

        LocalDateTime expiresAt =
                checkoutStartedAt.plusMinutes(checkoutTimeoutMinutes);

        return Math.max(0, Duration.between(now, expiresAt).getSeconds());
    }
}
