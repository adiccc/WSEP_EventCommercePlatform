package infrastructure;

import domain.event.Event;
import domain.event.IEventRepo;

import java.util.*;

public class EventRepoImpl implements IEventRepo {
    Map<String,Event> events; // key: eventId, value: event

    public EventRepoImpl() {
        events = new HashMap<>();
    }

    @Override
    public Event findById(String id) {
        if(events.containsKey(id))
            return events.get(id);
        throw new NoSuchElementException();
    }

    @Override
    public List<Event> getAll() {
        return new ArrayList<>(events.values());
    }

    @Override
    public void delete(String id) {
        events.remove(id);
    }

    @Override
    public void store(Event entity) {
        events.put(entity.getId(), entity);
    }
}
