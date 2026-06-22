package infrastructure.JPA;

import domain.activeOrder.ActiveOrder;
import domain.activeOrder.IActiveOrderRepo;
import DTO.ActiveOrderDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Profile("activeorder-db")
public class ActiveOrderRepoJpaAdapter implements IActiveOrderRepo {

    private final ActiveOrderJpaRepository activeOrderJpaRepository;

    public ActiveOrderRepoJpaAdapter(ActiveOrderJpaRepository activeOrderJpaRepository) {
        this.activeOrderJpaRepository = activeOrderJpaRepository;
    }

    @Override
    public ActiveOrder findById(Integer id) {
        return activeOrderJpaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("ActiveOrder not found with ID: " + id));
    }

    @Override
    public List<ActiveOrder> getAll() {
        return activeOrderJpaRepository.findAll();
    }

    @Override
    public void store(ActiveOrder entity) {
        try {
            activeOrderJpaRepository.save(entity);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockingFailureException(
                    "ActiveOrder " + entity.getId() + " was modified concurrently");
        }
    }

    @Override
    public void delete(Integer id) {
        activeOrderJpaRepository.deleteById(id);
    }

    @Override
    public void alreadyHasActiveOrder(String userId, Integer eventId) {
        if (activeOrderJpaRepository.existsByUserIdentifierAndEventId(userId, eventId)) {
            throw new IllegalStateException("User already has an active order for this event");
        }
    }

    @Override
    public List<ActiveOrder> findExpired(LocalDateTime now, int selectingTimeoutMinutes, int checkoutTimeoutMinutes) {
        return activeOrderJpaRepository.findAll().stream()
                .filter(order -> order.isExpired(now, selectingTimeoutMinutes, checkoutTimeoutMinutes))
                .collect(Collectors.toList());
    }

    @Override
    public ActiveOrderDTO findOrderByUserId(String userId) {
        return activeOrderJpaRepository.findFirstByUserIdentifier(userId)
                .map(ActiveOrderDTO::new)
                .orElseThrow(() -> new NoSuchElementException("No active order found for user ID: " + userId));
    }

    @Override
    public Optional<ActiveOrder> findActiveOrderByUserAndEvent(String userIdentifier, int eventId) {
        return activeOrderJpaRepository.findFirstByUserIdentifierAndEventId(userIdentifier, eventId);
    }

    @Override
    public int countActiveOrdersForEvent(int eventId) {
        return activeOrderJpaRepository.countByEventId(eventId);
    }
}
