package infrastructure.inMemory;

import Exception.OptimisticLockingFailureException;
import domain.eventQueue.EventQueue;
import domain.eventQueue.IEventQueueRepo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class EventQueueRepoImpl implements IEventQueueRepo {
    private final ConcurrentHashMap<Integer, EventQueue> queues = new ConcurrentHashMap<>();

    @Override
    public EventQueue findById(Integer eventId) {
        EventQueue queue = queues.computeIfAbsent(eventId, EventQueue::new);
        return new EventQueue(queue);
    }

    @Override
    public List<EventQueue> getAll() {
        List<EventQueue> copies = new ArrayList<>();
        for (EventQueue queue : queues.values()) {
            copies.add(new EventQueue(queue));
        }
        return copies;
    }

    @Override
    public void delete(Integer eventId) {
        queues.remove(eventId);
    }

    @Override
    public void store(EventQueue entity) {
        if (entity == null || entity.getId() == null) {
            throw new IllegalArgumentException("EventQueue must have an eventId");
        }

        Integer eventId = entity.getId();
        EventQueue currentQueue = queues.get(eventId);

        if (currentQueue == null) {
            EventQueue newEntry = new EventQueue(entity);
            newEntry.setVersion(0);

            EventQueue existing = queues.putIfAbsent(eventId, newEntry);

            if (existing != null) {
                throw new OptimisticLockingFailureException(
                        "EventQueue " + eventId + " was created concurrently"
                );
            }

            return;
        }

        if (currentQueue.getVersion() != entity.getVersion()) {
            throw new OptimisticLockingFailureException(
                    "EventQueue " + eventId + " version mismatch. Expected: "
                            + entity.getVersion()
                            + ", but found: "
                            + currentQueue.getVersion()
            );
        }

        EventQueue updatedQueue = new EventQueue(entity);
        updatedQueue.setVersion(entity.getVersion() + 1);

        boolean replaced = queues.replace(eventId, currentQueue, updatedQueue);

        if (!replaced) {
            throw new OptimisticLockingFailureException(
                    "EventQueue " + eventId + " was modified concurrently"
            );
        }
    }
}