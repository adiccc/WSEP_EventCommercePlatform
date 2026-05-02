package domain.activeOrder;

import domain.IRepo;
import domain.dto.ActiveOrderDTO;

import java.time.LocalDateTime;
import java.util.List;


public interface IActiveOrderRepo extends IRepo<ActiveOrder, Integer> {


    void alreadyHasActiveOrder(Integer value, Integer eventId);

    List<ActiveOrder> findExpired(LocalDateTime now);

     ActiveOrderDTO findOrderByUserId(Integer userId);
}