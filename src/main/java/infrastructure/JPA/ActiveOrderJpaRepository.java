package infrastructure.JPA;

import domain.activeOrder.ActiveOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActiveOrderJpaRepository extends JpaRepository<ActiveOrder, Integer> {

    Optional<ActiveOrder> findFirstByUserIdentifier(String userIdentifier);

    Optional<ActiveOrder> findFirstByUserIdentifierAndEventId(String userIdentifier, Integer eventId);

    int countByEventId(Integer eventId);

    boolean existsByUserIdentifierAndEventId(String userIdentifier, Integer eventId);
}
