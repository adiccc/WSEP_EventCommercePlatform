package domain.event;

import java.util.List;

public class Order {
    private int orderId;
    private int userId;
    private int eventId;
    private List<Integer> tickets;

    public Order(int orderId, int userId, int eventId, List<Integer> tickets) {
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = tickets;
    }

}