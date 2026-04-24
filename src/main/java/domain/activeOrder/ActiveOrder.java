package domain.activeOrder;

import java.util.List;

public class ActiveOrder {
    private int orderId;
    private int userId;
    private String eventId;
    private List<Integer> tickets;

    public ActiveOrder() {
        //TODO
    }

    public int getId() {
        return orderId;
    }

    public String getEventId() {
        return eventId;
    }
}