package domain.dto;

import java.time.LocalDateTime;

public class LotteryDTO {
    private final int eventId;
    private final int capacity;
    private final LocalDateTime registerWindow;
    private final long expirationTime;

    public LotteryDTO(int eventId, int capacity, LocalDateTime registerWindow, long expirationTime) {
        this.eventId = eventId;
        this.capacity = capacity;
        this.registerWindow = registerWindow;
        this.expirationTime = expirationTime;
    }

    public int getEventId() { return eventId; }
    public int getCapacity() { return capacity; }
    public LocalDateTime getRegisterWindow() { return registerWindow; }
    public long getExpirationTime() { return expirationTime; }
}
