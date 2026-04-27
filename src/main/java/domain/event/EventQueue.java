package domain.event;

import java.util.LinkedList;
import java.util.Queue;

public class EventQueue {
    private final Queue<String> waitingUsers; //tokens

    public EventQueue() {
        this.waitingUsers = new LinkedList<>();
    }
    public EventQueue(EventQueue eventQueue) {
        this.waitingUsers = new LinkedList<>(eventQueue.waitingUsers);
    }

    public void enqueue(String token) {
        if (!waitingUsers.contains(token)) {
            waitingUsers.add(token);
        }
    }

    public void dequeue() {
        if (!waitingUsers.isEmpty()) {
            waitingUsers.poll();
        }
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
}