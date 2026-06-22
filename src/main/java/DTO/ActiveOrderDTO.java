package DTO;

import domain.activeOrder.ActiveOrder;
import domain.activeOrder.STAGE;

import java.time.LocalDateTime;
import java.util.List;

public class ActiveOrderDTO {
    private final int orderId;
    private final String userIdentifier;
    private final Integer eventId;
    private final List<Integer> tickets;
    private final LocalDateTime createdAt;
    private LocalDateTime checkoutStartedAt;
    private final long version;
    private final STAGE stage;


    public ActiveOrderDTO(int orderId, String userIdentifier, Integer eventId, List<Integer> tickets, LocalDateTime createdAt, LocalDateTime checkoutStartedAt, long version, STAGE stage) {
        this.orderId = orderId;
        this.userIdentifier = userIdentifier;
        this.eventId = eventId;
        this.tickets = tickets;
        this.createdAt = createdAt;
        this.checkoutStartedAt = checkoutStartedAt;
        this.version = version;
        this.stage = stage;
    }

    public ActiveOrderDTO(ActiveOrder activeOrder) {
        this.orderId = activeOrder.getId();
        this.userIdentifier = activeOrder.getUserIdentifier();
        this.eventId = activeOrder.getEventId();
        this.tickets = activeOrder.getTickets();
        this.createdAt = activeOrder.getCreatedAt();
        this.checkoutStartedAt = activeOrder.getCheckoutStartedAt();
        this.version = activeOrder.getVersion();
        this.stage = activeOrder.getStage();
    }

    public int getId() {
        return orderId;
    }

    public int getEventId() {
        return eventId;
    }

    public List<Integer> getTickets() {
        return tickets;
    }
    public String getUserIdentifier() {
        return userIdentifier;
    }

    public STAGE getStage() {
        return stage;
    }
}
