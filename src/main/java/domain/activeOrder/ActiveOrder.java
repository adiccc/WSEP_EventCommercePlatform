package domain.activeOrder;

import java.util.List;

public class ActiveOrder {
    private int orderId;
    private int userId;
    private String eventId;
    private List<Integer> tickets;

    public ActiveOrder(int orderId, int userId, String eventId, List<Integer> tickets) {
        this.orderId = orderId; //how to generate orderId? maybe use a static variable that increments with each new order?
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = tickets;
    }

    public int getId() {
        return orderId;
    }

    public String getEventId() {
        return eventId;
    }
}