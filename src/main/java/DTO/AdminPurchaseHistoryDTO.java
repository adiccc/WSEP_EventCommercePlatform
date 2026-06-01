package DTO;

import domain.event.OrderStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdminPurchaseHistoryDTO {

    private final int orderId;
    private final String userIdentifier;

    private final int companyId;
    private final String companyName;

    private final int eventId;
    private final String eventName;
    private final String eventDate;
    private final String eventLocation;

    private final OrderStatus status;

    private final List<Integer> purchasedTickets;

    private final double totalSum;

    public AdminPurchaseHistoryDTO(
            int orderId,
            String userIdentifier,
            int companyId,
            String companyName,
            int eventId,
            String eventName,
            String eventDate,
            String eventLocation,
            OrderStatus status,
            List<Integer> purchasedTickets,
            double totalSum
    ) {
        this.orderId = orderId;
        this.userIdentifier = userIdentifier;

        this.companyId = companyId;
        this.companyName = companyName;

        this.eventId = eventId;
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

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public int getCompanyId() {
        return companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public int getEventId() {
        return eventId;
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

    public List<Integer> getPurchasedTickets() {
        return Collections.unmodifiableList(purchasedTickets);
    }

    public int getTicketCount() {
        return purchasedTickets.size();
    }

    public double getTotalSum() {
        return totalSum;
    }
}