package domain.event;

import DTO.PurchaseHistoryDTO;
import DTO.PurchasedTicketDTO;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @Column(name = "order_id", nullable = false)
    private int orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "user_identifier", nullable = false)
    private String userIdentifier;

    @Column(name = "event_id", insertable = false, updatable = false, nullable = false)
    private Integer eventId;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "event_date", nullable = false)
    private String eventDate;

    @Column(name = "event_location", nullable = false)
    private String eventLocation;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "order_purchased_tickets",
            joinColumns = @JoinColumn(name = "order_id")
    )
    private List<PurchasedTicketSnapshot> purchasedTickets;

    @Column(name = "total_sum", nullable = false)
    private double totalSum;

    @Column(name = "payment_confirmation_id", nullable = false)
    private String paymentConfirmationId;

    protected Order() {
        this.purchasedTickets = new ArrayList<>();
    }

    public Order(int orderId,
                 String userIdentifier,
                 Integer eventId,
                 String eventName,
                 String eventDate,
                 String eventLocation,
                 List<PurchasedTicketDTO> purchasedTickets,
                 double totalSum,
                 String paymentConfirmationId) {
        this.orderId = orderId;
        this.userIdentifier = userIdentifier;
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.eventLocation = eventLocation;
        this.purchasedTickets = new ArrayList<>();

        if (purchasedTickets != null) {
            for (PurchasedTicketDTO ticket : purchasedTickets) {
                this.purchasedTickets.add(new PurchasedTicketSnapshot(ticket));
            }
        }

        this.status = OrderStatus.APPROVED;
        this.totalSum = totalSum;
        this.paymentConfirmationId = paymentConfirmationId;
    }

    public Order(Order order) {
        this.orderId = order.orderId;
        this.userIdentifier = order.userIdentifier;
        this.eventId = order.eventId;
        this.eventName = order.eventName;
        this.eventDate = order.eventDate;
        this.eventLocation = order.eventLocation;
        this.purchasedTickets = new ArrayList<>();

        if (order.purchasedTickets != null) {
            for (PurchasedTicketSnapshot ticket : order.purchasedTickets) {
                this.purchasedTickets.add(new PurchasedTicketSnapshot(ticket));
            }
        }

        this.status = order.status;
        this.totalSum = order.totalSum;
        this.paymentConfirmationId = order.paymentConfirmationId;
    }

    public int getOrderId() {
        return orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public boolean canBeRefunded() {
        return status == OrderStatus.REFUND_REQUIRED;
    }

    public void markRefunded() {
        this.status = OrderStatus.REFUNDED;
    }

    public void markRefundRequired() {
        this.status = OrderStatus.REFUND_REQUIRED;
    }

    public double getTotalSum() {
        return totalSum;
    }

    public String getPaymentConfirmationId() {
        return paymentConfirmationId;
    }

    public int getNumOfTickets() {
        return purchasedTickets.size();
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public Integer getEventId() {
        return eventId;
    }

    public List<Integer> getTickets() {
        List<Integer> ticketIds = new ArrayList<>();

        for (PurchasedTicketSnapshot ticket : purchasedTickets) {
            ticketIds.add(ticket.getTicketId());
        }

        return ticketIds;
    }

    public String getEventName() {
        return eventName;
    }

    public PurchaseHistoryDTO toPurchaseHistoryDTO() {
        return new PurchaseHistoryDTO(
                orderId,
                eventName,
                eventDate,
                eventLocation,
                status,
                getPurchasedTickets(),
                totalSum
        );
    }

    public List<PurchasedTicketDTO> getPurchasedTickets() {
        List<PurchasedTicketDTO> result = new ArrayList<>();

        for (PurchasedTicketSnapshot ticket : purchasedTickets) {
            result.add(ticket.toDTO());
        }

        return result;
    }
}