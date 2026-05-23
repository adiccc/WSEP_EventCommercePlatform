package DTO;

import domain.event.OrderStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PurchaseHistoryDTO {
    private final int orderId;
    private final String eventName;
    private final String eventDate;
    private final String eventLocation;
    private final OrderStatus status;
    private final List<PurchasedTicketDTO> purchasedTickets;
    private final double totalSum;

    public PurchaseHistoryDTO(
            int orderId,
            String eventName,
            String eventDate,
            String eventLocation,
            OrderStatus status,
            List<PurchasedTicketDTO> purchasedTickets,
            double totalSum
    ) {
        this.orderId = orderId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.eventLocation = eventLocation;
        this.status = status;
        this.purchasedTickets = purchasedTickets == null
                ? new ArrayList<>()
                : new ArrayList<>(purchasedTickets);
        this.totalSum = totalSum;
    }

    public int getOrderId() {
        return orderId;
    }

    public String getEventName() {
        return eventName;
    }

    public String getEventDate() {
        return eventDate;
    }

    public String getEventLocation() {
        return eventLocation;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<PurchasedTicketDTO> getPurchasedTickets() {
        return Collections.unmodifiableList(purchasedTickets);
    }

    public int getTicketCount() {
        return purchasedTickets.size();
    }

    public double getTotalSum() {
        return totalSum;
    }
}