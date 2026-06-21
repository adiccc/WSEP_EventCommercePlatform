package domain.event;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventQueueManager {

    private final Map<Integer, EventQueue> queues = new ConcurrentHashMap<>();

    private EventQueue getQueue(int eventId) {
        return queues.computeIfAbsent(eventId, id -> new EventQueue());
    }

    public void enqueue(int eventId, String token) {
        getQueue(eventId).enqueue(token);
    }

    public String dequeue(int eventId) {
        return getQueue(eventId).dequeue();
    }

    public int position(int eventId, String token) {
        return getQueue(eventId).position(token);
    }

    public boolean contains(int eventId, String token) {
        return getQueue(eventId).contains(token);
    }

    public boolean remove(int eventId, String token) {
        return getQueue(eventId).remove(token);
    }

    public boolean isEmpty(int eventId) {
        return getQueue(eventId).isEmpty();
    }

    public boolean isFirst(int eventId, String s) {
        return getQueue(eventId).isFirst(s);
    }
}