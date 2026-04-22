package domain.event;

import java.util.List;

public class Event {
    private int id;
    private int companyId;
    private int creatorId;
    private EventMap eventMap;
    private EventQueue eventQueue;
    private List<Ticket> tickets;

    public Event(int id, int companyId, int creatorId, EventMap eventMap, EventQueue eventQueue) {
        this.eventMap = eventMap;
        this.eventQueue = eventQueue;
        this.companyId=companyId;
        this.creatorId=creatorId;
        this.id=id;
    }

    public int getCompanyId() {
        return companyId;
    }
    public int getCreatorId(){
        return creatorId;
    }

    public void setMap(EventMap eventMap) {
        this.eventMap = eventMap;
    }

    public int getId() {
        return id;
    }
}