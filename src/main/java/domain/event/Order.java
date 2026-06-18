package domain.event;

import DTO.PurchaseHistoryDTO;
import DTO.PurchasedTicketDTO;

import java.util.ArrayList;
import java.util.List;

public class Order {
    private OrderStatus status;
    private int orderId;
    private String userIdentifier; //for member will be an email, and for guest will be the generated string when purchased
    private Integer eventId;
    private String eventName;
    private String eventDate;
    private String eventLocation;
    private List<PurchasedTicketSnapshot> purchasedTickets;
    private double totalSum;
    private String paymentConfirmationId;


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