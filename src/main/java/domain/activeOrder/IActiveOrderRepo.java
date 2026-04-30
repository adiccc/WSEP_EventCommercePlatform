package domain.activeOrder;

import domain.IRepo;


public interface IActiveOrderRepo extends IRepo<ActiveOrder, Integer> {


    void alreadyHasActiveOrder(Integer value, Integer eventId);
}