package domain.lottery;

import java.time.LocalDateTime;
import java.util.*;

import java.util.concurrent.ConcurrentHashMap;

public class Lottery {
    private int id; // this value is the same as the eventId, because each event can have only one
    // lottery
    private int capacity;
    private List<Integer> registered;
    private Map<Integer, String> winners; // userId -> code
    private LocalDateTime registerWindow; // the time window for users to register for the lottery
    private long expirationTime; // after this time all the users will be able to buy tickets for the event, and
    // the lottery will be closed

    // Field for optimistic locking
    private long version;
    private Set<Integer> notifiedWinners;

    public Lottery(int eventId, int capacity, LocalDateTime registerWindow, long expirationTime) {
        this.id = eventId;
        this.capacity = capacity;
        this.registered = new ArrayList<>();
        this.winners = new ConcurrentHashMap<>();
        this.registerWindow = registerWindow;
        this.expirationTime = expirationTime;
        this.version = 0; // Initial version
        this.notifiedWinners = ConcurrentHashMap.newKeySet();
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
        this.winners = new ConcurrentHashMap<>(other.winners);
        this.notifiedWinners = ConcurrentHashMap.newKeySet();
        this.notifiedWinners.addAll(other.notifiedWinners);
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


    public LocalDateTime getRegisterWindow() {
        return registerWindow;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public Map<Integer, String> drawWinners() {
        if (registered.isEmpty()) {
            return Collections.emptyMap();
        }
        // If winners have already been drawn, return the existing winners.
        // This ensures that the same winners are returned if drawWinners is called multiple times.
        if (!winners.isEmpty()) {
            return Collections.unmodifiableMap(winners);
        }

        List<Integer> selectedUsers;

        if (registered.size() <= capacity) {
            selectedUsers = new ArrayList<>(registered);
        } else {
            List<Integer> shuffledUsers = new ArrayList<>(registered);
            Collections.shuffle(shuffledUsers);
            selectedUsers = shuffledUsers.subList(0, capacity);
        }

        for (Integer userId : selectedUsers) {
            String accessCode = AccessCodeGenerator.generate();

            while (winners.containsValue(accessCode)) {
                accessCode = AccessCodeGenerator.generate();
            }

            winners.put(userId, accessCode);
        }
        return Collections.unmodifiableMap(winners);
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

    public boolean codeMatchesUser(int userId, String code) {
        return winners.containsKey(userId) && winners.get(userId).equals(code);
    }

    public List<Integer> getWinners() {
        return new ArrayList<>(winners.keySet());
    }

    public Map<Integer, String> getWinnerCodes() {
        return Collections.unmodifiableMap(winners);
    }

    public boolean isWinnerNotified(int userId) {
        return notifiedWinners.contains(userId);
    }

    public void markWinnerNotified(int userId) {
        if (!winners.containsKey(userId)) {
            throw new IllegalArgumentException("Cannot mark non-winner as notified");
        }

        notifiedWinners.add(userId);
    }

    public Set<Integer> getNotifiedWinners() {
        return Collections.unmodifiableSet(notifiedWinners);
    }

}
