package infrastructure.inMemory;

import domain.activeOrder.ActiveOrder;
import domain.activeOrder.IActiveOrderRepo;
import Exception.OptimisticLockingFailureException;
import domain.dto.ActiveOrderDTO;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
@Repository
@Profile("memory")
public class ActiveOrderRepoImpl implements IActiveOrderRepo {
    private ConcurrentHashMap<Integer, ActiveOrder> activeOrders; // key: activeOrderId, value: ActiveOrder

    public ActiveOrderRepoImpl() {
        activeOrders = new ConcurrentHashMap<>();
    }

    @Override
    public ActiveOrder findById(Integer id) {
        ActiveOrder dbOrder = activeOrders.get(id);
        if (dbOrder != null) {
            return new ActiveOrder(dbOrder);
        }
        throw new NoSuchElementException("ActiveOrder not found with ID: " + id);
    }

    @Override
    public List<ActiveOrder> getAll() {
        List<ActiveOrder> copies = new ArrayList<>();
        for (ActiveOrder order : activeOrders.values()) {
            copies.add(new ActiveOrder(order));
        }
        return copies;
    }
    @Override
    public void delete(Integer id) {
        activeOrders.remove(id);
    }

    @Override
    public synchronized void store(ActiveOrder entity) {
        ActiveOrder currentOrder = activeOrders.get(entity.getId());

        if (currentOrder == null) {
            ActiveOrder newEntry = new ActiveOrder(entity);
            activeOrders.put(newEntry.getId(), newEntry);
            return;
        }
        if (currentOrder.getVersion() != entity.getVersion()) {
            throw new OptimisticLockingFailureException(
                    "Event " + entity.getId() + " version mismatch. Expected: " +
                            entity.getVersion() + ", but found: " + currentOrder.getVersion()
            );
        }
        ActiveOrder updatedOrder = new ActiveOrder(entity);
        updatedOrder.setVersion(entity.getVersion() + 1);

        boolean replaced = activeOrders.replace(entity.getId(), currentOrder, updatedOrder);

        if (!replaced) {
            throw new OptimisticLockingFailureException(
                    "Event " + entity.getId() + " was modified concurrently"
            );
        }
    }

    public void alreadyHasActiveOrder(String userId, Integer eventId) {
        for (ActiveOrder order : activeOrders.values()) {
            if (order.getUserIdentifier().equals(userId) && order.getEventId().equals(eventId)) {
                throw new IllegalStateException("User already has an active order for this event");
            }
        }
    }

    @Override
    public List<ActiveOrder> findExpired(LocalDateTime now) {
        List<ActiveOrder> result = new ArrayList<>();

        for (ActiveOrder order : activeOrders.values()) {
            if (order.isExpired(now)) {
                result.add(new ActiveOrder(order));
            }
        }

        return result;
    }

    public ActiveOrderDTO findOrderByUserId(String userId) {
        for (ActiveOrder order : activeOrders.values()) {
            if (order.getUserIdentifier().equals(userId)) {
                return new ActiveOrderDTO(order);
            }
        }
        throw new NoSuchElementException("No active order found for user ID: " + userId);
    }

    @Override
    public int countActiveOrdersForEvent(int eventId) {
        int count = 0;
        for (ActiveOrder order : activeOrders.values()) {
            if (order.getEventId() != null && order.getEventId().equals(eventId)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Optional<ActiveOrder> findActiveOrderByUserAndEvent(String userIdentifier, int eventId) {
        for (ActiveOrder order : activeOrders.values()) {
            if (order.getUserIdentifier().equals(userIdentifier)
                    && order.getEventId() != null
                    && order.getEventId().equals(eventId)) {

                return Optional.of(new ActiveOrder(order));
            }
        }

        return Optional.empty();
    }

}
