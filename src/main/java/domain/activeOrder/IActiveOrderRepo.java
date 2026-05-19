package domain.activeOrder;

import domain.IRepo;
import domain.dto.ActiveOrderDTO;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;


public interface IActiveOrderRepo extends IRepo<ActiveOrder, Integer> {

    void alreadyHasActiveOrder(String value, Integer eventId);

    List<ActiveOrder> findExpired(LocalDateTime now);

     ActiveOrderDTO findOrderByUserId(String userId);
    Optional<ActiveOrder> findActiveOrderByUserAndEvent(String userIdentifier, int eventId);
    int countActiveOrdersForEvent(int eventId);
}