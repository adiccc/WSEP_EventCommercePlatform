package infrastructure;

import domain.event.Event;
import domain.event.EventQueue;
import domain.event.IEventRepo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import Exception.OptimisticLockingFailureException;

public class EventRepoImpl implements IEventRepo {
    Map<String,Event> events; // key: eventId, value: event

    public EventRepoImpl() {
        events = new ConcurrentHashMap<>();
    }

    public Event findById(String id) {
        Event dbEvent = events.get(id);
        if (dbEvent != null) {
            return new Event(dbEvent);
        }
        throw new NoSuchElementException("Event not found with ID: " + id);
    }
    @Override
    public List<Event> getAll() {
        List<Event> copies = new ArrayList<>();
        for (Event e : events.values()) {
            copies.add(new Event(e));
        }
        return copies;
    }
    @Override
    public void delete(String id) {
        events.remove(id);
    }

    @Override
    public synchronized void store(Event entity) {
        Event currentEvent = events.get(entity.getId());

        if (currentEvent == null) {
            Event newEntry = new Event(entity);
            events.put(newEntry.getId(), newEntry);
            return;
        }

        if (currentEvent.getVersion() != entity.getVersion()) {
            throw new OptimisticLockingFailureException(
                    "Event " + entity.getId() + " version mismatch. Expected: " +
                            entity.getVersion() + ", but found: " + currentEvent.getVersion()
            );
        }

        Event updatedEvent = new Event(entity);
        updatedEvent.setVersion(entity.getVersion() + 1);

        boolean replaced = events.replace(entity.getId(), currentEvent, updatedEvent);

        if (!replaced) {
            throw new OptimisticLockingFailureException(
                    "Event " + entity.getId() + " was modified concurrently"
            );
        }
    }

    @Override
    public List<Event> findByCompany(int companyId) {
        return events.values().stream()
                .filter(e -> e.getCompanyId() == companyId)
                .map(Event::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<Event> findByCreator(int creatorId) {
        return events.values().stream()
                .filter(e -> e.getCreatorId() == creatorId)
                .map(Event::new)
                .collect(Collectors.toList());
    }
}