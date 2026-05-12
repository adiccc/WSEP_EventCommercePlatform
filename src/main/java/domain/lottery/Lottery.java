package domain.lottery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;

import java.util.List;

public class Lottery {
    private int id; // this value is the same as the eventId, because each event can have only one
                       // lottery
    private int capacity;
    private List<Integer> registered;
    private List<Integer> winners; // the code of each user who won the lottery is his ID because there ara no notifications in the system
    private LocalDateTime registerWindow; // the time window for users to register for the lottery
    private long expirationTime; // after this time all the users will be able to buy tickets for the event, and
                                   // the lottery will be closed

    // Field for optimistic locking
    private long version;

    public Lottery(int eventId, int capacity, LocalDateTime registerWindow, long expirationTime) {
        this.id = eventId;
        this.capacity = capacity;
        this.registered = new ArrayList<>();
        this.winners = new ArrayList<>();
        this.registerWindow = registerWindow;
        this.expirationTime = expirationTime;
        this.version = 0; // Initial version
    }

    // Copy Constructor - essential for returning detached copies from the Repo
    public Lottery(Lottery other) {
        this.id = other.id;
        this.capacity = other.capacity;
        this.registerWindow = other.registerWindow;
        this.expirationTime = other.expirationTime;
        this.version = other.version;
        // Deep copy of lists to ensure memory isolation
        this.registered = new ArrayList<>(other.registered);
        this.winners = new ArrayList<>(other.winners);
    }

    public int getId() {
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

    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }

    public void registerUserToLottery(int userId) {
        registered.add(userId);
    }

}