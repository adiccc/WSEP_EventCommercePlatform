package domain.event;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventQueueManager {

    private final Map<Integer, EventQueue> queues = new ConcurrentHashMap<>();
    private final Map<Integer, Object> locks = new ConcurrentHashMap<>();

    private EventQueue getQueue(int eventId) {
        return queues.computeIfAbsent(eventId, id -> new EventQueue());
    }

    private Object getLock(int eventId) {
        return locks.computeIfAbsent(eventId, id -> new Object());
    }

    public void enqueue(int eventId, String token) {
        synchronized (getLock(eventId)) {
            getQueue(eventId).enqueue(token);
        }
    }

    public String dequeue(int eventId) {
        synchronized (getLock(eventId)) {
            return getQueue(eventId).dequeue();
        }
    }

    public int position(int eventId, String token) {
        synchronized (getLock(eventId)) {
            return getQueue(eventId).position(token);
        }
    }

    public boolean contains(int eventId, String token) {
        synchronized (getLock(eventId)) {
            return getQueue(eventId).contains(token);
        }
    }

    public boolean remove(int eventId, String token) {
        synchronized (getLock(eventId)) {
            return getQueue(eventId).remove(token);
        }
    }

    public boolean isFirst(int eventId, String token) {
        synchronized (getLock(eventId)) {
            return getQueue(eventId).isFirst(token);
        }
    }

    public boolean isEmpty(int eventId) {
        synchronized (getLock(eventId)) {
            return getQueue(eventId).isEmpty();
        }
    }
}