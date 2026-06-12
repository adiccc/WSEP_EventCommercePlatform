package infrastructure.inMemory;

import domain.event.Event;
import domain.event.IEventRepo;
import domain.event.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import Exception.OptimisticLockingFailureException;
import domain.event.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory & !event-db")
public class EventRepoImpl implements IEventRepo {
    Map<Integer,Event> events; // key: eventId, value: event
    private final AtomicInteger eventIdGenerator = new AtomicInteger(1);
    private final AtomicInteger ticketIdGenerator = new AtomicInteger(1);


    public EventRepoImpl() {
        events = new ConcurrentHashMap<>();
    }

    @Override
    public Event findById(Integer id) {
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
    public void delete(Integer id) {
        events.remove(id);
    }

    @Override
    public synchronized void store(Event entity) {
        Event currentEvent = entity!=null ? events.get(entity.getId()) : null;
        if (currentEvent == null) {
            int id = eventIdGenerator.getAndIncrement();
            entity.setId(id);
            if(entity.getEventMap()!=null) {
                for (Zone z : entity.getEventMap().getZones()) {
                    for (Ticket t : z.getTickets()) {
                        t.setId(ticketIdGenerator.getAndIncrement());
                    }
                }
            }
            Event newEntry = new Event(entity);
            events.put(id, newEntry);
            return;
        }else if(entity.getEventMap()!=null) {
            for(Zone z: entity.getEventMap().getZones()){
                for(Ticket t : z.getTickets()){
                    if (t.getId() == null) {
                        t.setId(ticketIdGenerator.getAndIncrement());
                    }
                }
            }
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

    @Override
    public List<String> getAllPurchasers() {
        HashSet<String> purchasers = new HashSet<>();
       Collection<Event> allEvents = events.values();
       for(Event e: allEvents){
           List<Order> eventsOrder = e.getOrders();
           for(Order o : eventsOrder){
               purchasers.add(o.getUserIdentifier());
           }
       }
       return purchasers.stream().toList();
    }

    @Override
    public List<String> getAllEventPurchasers(Integer eventId){
        Event event = events.get(eventId);
        if (event == null) {
            throw new NoSuchElementException("Event not found with ID: " + eventId);
        }
        List<Order> eventsOrder = event.getOrders();
        HashSet<String> purchasers = new HashSet<>();
        for(Order o : eventsOrder){
            purchasers.add(o.getUserIdentifier());
        }
        return purchasers.stream().toList();
    }

}