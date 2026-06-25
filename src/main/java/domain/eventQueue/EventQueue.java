package domain.eventQueue;

import java.util.LinkedList;
import java.util.Queue;

public class EventQueue {
    private Integer eventId;
    private int version;
    private final Queue<String> waitingUsers; // tokens

    public EventQueue(Integer eventId) {
        this.eventId = eventId;
        this.version = 0;
        this.waitingUsers = new LinkedList<>();
    }

    public EventQueue(EventQueue eventQueue) {
        this.eventId = eventQueue.eventId;
        this.version = eventQueue.version;
        this.waitingUsers = new LinkedList<>(eventQueue.waitingUsers);
    }

    public Integer getId() {
        return eventId;
    }

    public void setId(Integer eventId) {
        this.eventId = eventId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void enqueue(String token) {
        if (!waitingUsers.contains(token)) {
            waitingUsers.add(token);
        }
    }

    public String dequeue() {
        return waitingUsers.poll();
    }

    public int position(String token) {
        int pos = 1;

        for (String t : waitingUsers) {
            if (t.equals(token)) {
                return pos;
            }
            pos++;
        }

        return -1;
    }

    public boolean isFirst(String token) {
        return !waitingUsers.isEmpty() && waitingUsers.peek().equals(token);
    }

    public boolean contains(String token) {
        return waitingUsers.contains(token);
    }

    public int size() {
        return waitingUsers.size();
    }

    public boolean isEmpty() {
        return waitingUsers.isEmpty();
    }

    public boolean remove(String token) {
        return waitingUsers.remove(token);
    }
}