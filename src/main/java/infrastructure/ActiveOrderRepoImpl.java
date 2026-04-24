package infrastructure;

import domain.activeOrder.ActiveOrder;
import domain.activeOrder.IActiveOrderRepo;

import java.util.*;

public class ActiveOrderRepoImpl implements IActiveOrderRepo {
    private Map<Integer, ActiveOrder> activeOrders; // key: activeOrderId, value: ActiveOrder

    public ActiveOrderRepoImpl() {
        activeOrders = new HashMap<>();
    }

    @Override
    public ActiveOrder findById(Integer id) {
        if(activeOrders.containsKey(id))
            return activeOrders.get(id);
        throw new NoSuchElementException();
    }

    @Override
    public List<ActiveOrder> getAll() {
        return new ArrayList<>(activeOrders.values());
    }

    @Override
    public void delete(Integer id) {
        activeOrders.remove(id);
    }

    @Override
    public void store(ActiveOrder entity) {
        activeOrders.put(entity.getId(), entity);
    }

}
