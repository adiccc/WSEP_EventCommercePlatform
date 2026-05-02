package domain.dto;

import domain.activeOrder.ActiveOrder;
import domain.activeOrder.STAGE;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ActiveOrderDTO {
    private final int orderId;
    private final int userId;
    private final Integer eventId;
    private final List<Integer> tickets;
    private final LocalDateTime expireTime;
    private final long version;
    private final STAGE stage;


    public ActiveOrderDTO(int orderId, int userId, Integer eventId, List<Integer> tickets, LocalDateTime expireTime, long version, STAGE stage) {
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = tickets;
        this.expireTime = expireTime;
        this.version = version;
        this.stage = stage;
    }

    public ActiveOrderDTO(ActiveOrder activeOrder) {
        this.orderId = activeOrder.getId();
        this.userId = activeOrder.getUserId();
        this.eventId = activeOrder.getEventId();
        this.tickets = activeOrder.getTickets();
        this.expireTime = activeOrder.getExpireTime();
        this.version = activeOrder.getVersion();
        this.stage = activeOrder.getStage();
    }

    public int getUserId() {
        return userId;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
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
}
