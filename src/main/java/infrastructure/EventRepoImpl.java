package infrastructure;

import domain.event.Event;
import domain.event.IEventRepo;

import java.util.*;

public class EventRepoImpl implements IEventRepo {
    Map<Integer,Event> events; // key: eventId, value: event

    public EventRepoImpl() {
        events = new HashMap<>();
    }

    @Override
    public Event findById(Integer integer) {
        if(events.containsKey(integer))
            return events.get(integer);
        throw new NoSuchElementException();
    }

    @Override
    public List<Event> getAll() {
        return new ArrayList<>(events.values());
    }

    @Override
    public void delete(Integer integer) {
        events.remove(integer);
    }

    @Override
    public void store(Event entity) {
        events.put(events.size(), entity);

    }
}
