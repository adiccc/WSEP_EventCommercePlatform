package domain.activeOrder;

import domain.IRepo;
import domain.event.Event;

public interface IActiveOrderRepo extends IRepo<ActiveOrder, Integer> {

        ActiveOrder getActiveOrderByEventId(String eventId);
        void removeActiveOrderByEventId(String eventId);
        boolean addActiveOrder(ActiveOrder order);
         boolean updateActiveOrder(ActiveOrder order);

}