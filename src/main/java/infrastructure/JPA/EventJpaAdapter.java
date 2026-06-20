package infrastructure.JPA;

import domain.event.Event;
import domain.event.IEventRepo;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.NoSuchElementException;

@Repository
@Profile("event-db")
public class EventJpaAdapter implements IEventRepo {

    private final EventJpaRepository eventJpaRepository;

    public EventJpaAdapter(EventJpaRepository eventJpaRepository) {
        this.eventJpaRepository = eventJpaRepository;
    }

    @Override
    public Event findById(Integer id) {
        return eventJpaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Event not found with ID: " + id));
    }

    @Override
    public List<Event> getAll() {
        return eventJpaRepository.findAll();
    }

    @Override
    public void delete(Integer id) {
        eventJpaRepository.deleteById(id);
    }

    @Override
    public void store(Event event) {
        try {
            eventJpaRepository.save(event);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockingFailureException(
                    "Event " + event.getId() + " was modified concurrently"
            );
        }
    }

    @Override
    public List<Event> findByCompany(int companyId) {
        return eventJpaRepository.findByCompanyId(companyId);
    }

    @Override
    public List<Event> findByCreator(int creatorId) {
        return eventJpaRepository.findByCreatorId(creatorId);
    }

    @Override
    public List<String> getAllPurchasers() {
        return eventJpaRepository.findAllPurchasers();
    }

    @Override
    public List<String> getAllEventPurchasers(Integer eventId) {
        if (!eventJpaRepository.existsById(eventId)) {
            throw new NoSuchElementException("Event not found with ID: " + eventId);
        }

        return eventJpaRepository.findAllPurchasersByEventId(eventId);
    }
}