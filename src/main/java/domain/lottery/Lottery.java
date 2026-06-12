package domain.lottery;

import domain.dto.LotteryDTO;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Entity
@Table(name = "lotteries")
public class Lottery {
    @Id
    @Column(name = "event_id", nullable = false)
    private int id; // this value is the same as the eventId, because each event can have only one
    // lottery
    @Column(nullable = false)
    private int capacity;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "lottery_registered_users",
            joinColumns = @JoinColumn(
                    name = "event_id",
                    referencedColumnName = "event_id"
            ),
            uniqueConstraints = {
                    @UniqueConstraint(columnNames = {"event_id", "user_id"})
            }
    )
    @Column(name = "user_id", nullable = false)
    private List<Integer> registered;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "lottery_winners",
            joinColumns = @JoinColumn(
                    name = "event_id",
                    referencedColumnName = "event_id"
            ),
            uniqueConstraints = {
                    @UniqueConstraint(columnNames = {"event_id", "user_id"}),
                    @UniqueConstraint(columnNames = {"event_id", "access_code"})
            }
    )
    @MapKeyColumn(name = "user_id")
    @Column(name = "access_code", nullable = false)
    private Map<Integer, String> winners; // userId -> code
    @Column(name = "register_window", nullable = false)
    private LocalDateTime registerWindow; // the time window for users to register for the lottery
    @Column(name = "expiration_time", nullable = false)
    private long expirationTime; // after this time all the users will be able to buy tickets for the event, and
    // the lottery will be closed

    // Field for optimistic locking
    @Version
    private long version;

    protected Lottery() {
        // for JPA
    }

    public Lottery(int eventId, int capacity, LocalDateTime registerWindow, long expirationTime) {
        this.id = eventId;
        this.capacity = capacity;
        this.registered = new ArrayList<>();
        this.winners = new HashMap<>();
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
        this.registered = other.registered == null
                ? new ArrayList<>()
                : new ArrayList<>(other.registered);

        this.winners = other.winners == null
                ? new HashMap<>()
                : new HashMap<>(other.winners);
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

    public void updateLottery(LotteryDTO dto, LocalDateTime eventSaleStartDate) {
        if (!winners.isEmpty())
            throw new IllegalStateException("Cannot update lottery after winners have been notified");
        if (dto.getCapacity() <= 0 || dto.getRegisterWindow() == null || dto.getExpirationTime() <= 0)
            throw new IllegalArgumentException("Please complete all lottery details: capacity, register window, and expiration time");
        if (dto.getRegisterWindow().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Register window must be in the future");
        if (dto.getRegisterWindow().isAfter(eventSaleStartDate))
            throw new IllegalArgumentException("Register window must be before sale start date");
        this.capacity = dto.getCapacity();
        this.registerWindow = dto.getRegisterWindow();
        this.expirationTime = dto.getExpirationTime();
        winners.clear();
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

}
