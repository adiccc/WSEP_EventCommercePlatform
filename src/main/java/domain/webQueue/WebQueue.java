package domain.webQueue;

import DTO.QueueEntryResultDTO;
import application.AdmissionCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class WebQueue {

    private static volatile WebQueue instance;

    // max number of users allowed in the system at the same time (changeable by admin)
    private volatile int maxCapacity;

    // number of users currently admitted and active in the system
    private final AtomicInteger activeCount = new AtomicInteger(0);

    // number of users currently waiting in the queue
    private final AtomicInteger waitingCount = new AtomicInteger(0);

    // total number of users ever admitted from the waiting line (monotonically increases)
    // used to calculate a waiting user's current position in O(1)
    private final AtomicInteger admittedFromQueue = new AtomicInteger(0);

    // assigns a unique, ever-increasing number to each user who joins the waiting line
    private final AtomicInteger sequenceGenerator = new AtomicInteger(0);

    // ordered waiting line — used to admit users in FIFO order
    private final ConcurrentLinkedQueue<String> waitingLine = new ConcurrentLinkedQueue<>();

    // maps uuid → sequence number for O(1) position lookup
    // also serves as membership check: if uuid is here, the user is still waiting
    private final ConcurrentHashMap<String, Integer> sequenceMap = new ConcurrentHashMap<>();

    // maps uuid → callback to fire when that user is admitted from the waiting line
    private final ConcurrentHashMap<String, AdmissionCallback> callbacks = new ConcurrentHashMap<>();

    private WebQueue(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    // called once at startup with the initial capacity
    public static WebQueue getInstance(int initialCapacity) {
        if (instance == null) {
            synchronized (WebQueue.class) {
                if (instance == null) {
                    instance = new WebQueue(initialCapacity);
                }
            }
        }
        return instance;
    }

    // for testing only — resets the singleton so each test starts fresh
    public static void resetForTesting() {
        instance = null;
    }

    // called after initialization
    public static WebQueue getInstance() {
        if (instance == null) {
            throw new IllegalStateException("WebQueue not initialized. Call getInstance(capacity) first.");
        }
        return instance;
    }

    public void setMaxCapacity(int maxCapacity) {
        if (maxCapacity <= 0)
            throw new IllegalArgumentException("Capacity must be greater than 0");
        this.maxCapacity = maxCapacity;
    }

    public int getMaxCapacity() { return maxCapacity; }
    public int getActiveCount() { return activeCount.get(); }
    public int getWaitingCount() { return waitingCount.get(); }

    public QueueEntryResultDTO tryEnter(AdmissionCallback callback) {
        String uuid = UUID.randomUUID().toString();
        int current;
        do {
            current = activeCount.get();
            if (current >= maxCapacity) {
                int seq = sequenceGenerator.incrementAndGet();
                sequenceMap.put(uuid, seq);
                waitingLine.add(uuid);
                callbacks.put(uuid, callback);
                waitingCount.incrementAndGet();
                int position = seq - admittedFromQueue.get();
                return new QueueEntryResultDTO(uuid, false, position);
            }
        } while (!activeCount.compareAndSet(current, current + 1));
        return new QueueEntryResultDTO(uuid, true, -1);
    }

    // called by other domain classes when an active user leaves the system
    public void notifyUserLeft() {
        String next;
        while ((next = waitingLine.poll()) != null) {
            if (sequenceMap.remove(next) != null) {
                admittedFromQueue.incrementAndGet();
                waitingCount.decrementAndGet();
                AdmissionCallback callback = callbacks.remove(next);
                if (callback != null) {
                    callback.onAdmitted(next);
                }
                // activeCount unchanged: one left, one from waitingLine entered
                return;
            }
            // this uuid was already claimed by removeFromQueue — try the next one
        }
        activeCount.decrementAndGet();
    }

    // removes a waiting user from the queue by their token; returns false if not found
    public boolean removeFromQueue(String uuid) {
        Integer removedSeq = sequenceMap.remove(uuid);

        if (removedSeq == null) {
            return false;
        }

        waitingLine.remove(uuid);
        callbacks.remove(uuid);
        waitingCount.decrementAndGet();
        sequenceGenerator.decrementAndGet();
        Map<String, Integer> snapshot = new HashMap<>(sequenceMap);

        for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
            if (entry.getValue() > removedSeq) {
                sequenceMap.computeIfPresent(entry.getKey(), (k, v) -> v - 1);
            }
        }

        return true;
    }

    public QueueEntryResultDTO getStatus(String uuid) {
        Integer seq = sequenceMap.get(uuid);
        if (seq == null) {
            return new QueueEntryResultDTO(uuid, true, -1);
        }
        int position = seq - admittedFromQueue.get();
        return new QueueEntryResultDTO(uuid, false, position);
    }
}
