package domain.user;

import java.util.HashMap;
import java.util.Map;

public abstract class User {
    private Map<Integer, Integer> ticketsBoughtByEvent;

    public User() {
        this.ticketsBoughtByEvent = new HashMap<>();
    }

    public void addTicketsPurchased(int eventId, int count) {
        if (ticketsBoughtByEvent.containsKey(eventId)) {
            ticketsBoughtByEvent.put(eventId, ticketsBoughtByEvent.get(eventId) + count);
        } else {
            ticketsBoughtByEvent.put(eventId, count);
        }
    }

    public int getTicketsBoughtForEvent(int eventId) {
        if (ticketsBoughtByEvent.containsKey(eventId)) {
            return ticketsBoughtByEvent.get(eventId);
        }
        return 0;
    }

    public Map<Integer, Integer> getTicketsBoughtByEvent() {
        return new HashMap<>(ticketsBoughtByEvent);
    }
}
