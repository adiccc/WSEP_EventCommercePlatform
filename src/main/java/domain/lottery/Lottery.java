package domain.lottery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;

import java.util.List;

public class Lottery {
    private String id; // this value is the same as the eventId, because each event can have only one
                       // lottery
    private int capacity;
    private List<Integer> registered;
    private List<Integer> winners; // the code of each user who won the lottery is his ID because there ara no notifications in the system
    private LocalDateTime registerWindow; // the time window for users to register for the lottery
    private long expirationTime; // after this time all the users will be able to buy tickets for the event, and
                                   // the lottery will be closed

    public Lottery(String eventId, int capacity, LocalDateTime registerWindow, long expirationTime) {
        this.id = eventId;
        this.capacity = capacity;
        this.registered = new ArrayList<>();
        this.winners = new ArrayList<>();
        this.registerWindow = registerWindow;
        this.expirationTime = expirationTime;
    }

    public String getId() {
        return id;
    }

    public int getCapacity() {
        return capacity;
    }

    public List<Integer> getRegistered() {
        return registered;
    }

    public List<Integer> getWinners() {
        return winners;
    }

    public LocalDateTime getRegisterWindow() {
        return registerWindow;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void drawWinners() {
        if (registered.isEmpty()) {
            return; // No one registered
        }

        if (registered.size() <= capacity) {
            winners.addAll(registered);
        } else {
            // Shuffle the registered list and pick the first 'capacity' users
            List<Integer> shuffledUsers = new ArrayList<>(registered);
            Collections.shuffle(shuffledUsers);
            winners.addAll(shuffledUsers.subList(0, capacity));
        }
    }

}