package domain.activeOrder;

import domain.IRepo;

import java.time.LocalDateTime;
import java.util.List;


public interface IActiveOrderRepo extends IRepo<ActiveOrder, Integer> {


    void alreadyHasActiveOrder(Integer value, Integer eventId);

    List<ActiveOrder> findExpired(LocalDateTime now);
}