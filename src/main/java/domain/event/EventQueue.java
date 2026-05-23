package domain.event;

import java.util.LinkedList;
import java.util.Queue;

public class EventQueue {
    private final Queue<String> waitingUsers; //tokens

    public EventQueue() {
        this.waitingUsers = new LinkedList<>();
    }
    public EventQueue(EventQueue eventQueue) {
        this.waitingUsers = new LinkedList<>();
        for (String s : eventQueue.waitingUsers) {
            this.waitingUsers.add(s);
        }
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

        return -1; // not found
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