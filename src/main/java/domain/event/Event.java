package domain.event;

import java.util.List;

public class Event {
    private EventMap eventMap;
    private EventQueue eventQueue;
    private List<Ticket> tickets;

    public Event(EventMap eventMap, EventQueue eventQueue) {
        this.eventMap = eventMap;
        this.eventQueue = eventQueue;
        //TODO
    }
}