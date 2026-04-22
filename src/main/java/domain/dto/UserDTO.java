package domain.dto;

import java.util.HashMap;
import java.util.Map;

public class UserDTO {
    private int userId;
    private int age;
    private Map<Integer, Integer> ticketsBoughtByEvent;

    public UserDTO(int userId, int age, Map<Integer, Integer> ticketsBoughtByEvent) {
        this.userId = userId;
        this.age = age;
        this.ticketsBoughtByEvent = new HashMap<>(ticketsBoughtByEvent);
    }

    public int getTicketsBoughtForEvent(int eventId) {
        if (ticketsBoughtByEvent.containsKey(eventId)) {
            return ticketsBoughtByEvent.get(eventId);
        }
        return 0;
    }

    public int getUserId() { return userId; }
    public int getAge() { return age; }
}
