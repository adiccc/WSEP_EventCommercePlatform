package domain.activeOrder;

import domain.event.Event;

import java.util.ArrayList;
import java.util.List;

public class ActiveOrder {
    private int orderId;
    private int userId;
    private String eventId;
    private List<Integer> tickets;
    private long version;

    public ActiveOrder(int orderId, int userId, String eventId, List<Integer> tickets) {
        this.orderId = orderId; //how to generate orderId? maybe use a static variable that increments with each new order?
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = tickets;
        this.version = 0;
    }

    public ActiveOrder(ActiveOrder activeOrder) {
        this.orderId = activeOrder.orderId;
        this.userId = activeOrder.userId;
        this.eventId = activeOrder.eventId;
        this.tickets = new ArrayList<>(activeOrder.tickets);
        this.version = activeOrder.version;

    }

    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }

    public int getId() {
        return orderId;
    }

    public String getEventId() {
        return eventId;
    }

    public int getUserId() {
        return userId;
    }

    public List<Integer> getTickets() {
        return new ArrayList<>(tickets);
    }

    public boolean hasTickets() {
        return tickets != null && !tickets.isEmpty();
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ActiveOrder other = (ActiveOrder) obj;
        return orderId==other.orderId && version == other.getVersion();

    }
}