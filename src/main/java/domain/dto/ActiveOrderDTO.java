package domain.dto;

import domain.activeOrder.ActiveOrder;
import domain.activeOrder.STAGE;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public class ActiveOrderDTO {
    private int orderId;
    private int userId;
    private Integer eventId;
    private List<Integer> tickets;
    private LocalDateTime expireTime;
    private long version;
    private STAGE stage;


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
}
