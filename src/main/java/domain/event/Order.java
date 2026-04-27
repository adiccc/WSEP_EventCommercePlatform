package domain.event;

import java.util.ArrayList;
import java.util.List;

public class Order {
    private int orderId;
    private int userId;
    private String eventId;
    private List<Integer> tickets;

    public Order(int orderId, int userId, String eventId, List<Integer> tickets) {
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = tickets;
    }
    public Order(Order order) {
        this.orderId = order.orderId;
        this.userId = order.userId;
        this.eventId = order.eventId;
        this.tickets=new ArrayList<>(order.tickets);
    }

}