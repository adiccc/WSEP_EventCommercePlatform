package infrastructure.JPA;

import domain.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventJpaRepository extends JpaRepository<Event, Integer> {

    List<Event> findByCompanyId(int companyId);

    List<Event> findByCreatorId(int creatorId);

    @Query("""
           SELECT DISTINCT o.userIdentifier
           FROM Event e
           JOIN e.orders o
           """)
    List<String> findAllPurchasers();

    @Query("""
           SELECT DISTINCT o.userIdentifier
           FROM Event e
           JOIN e.orders o
           WHERE e.id = :eventId
           """)
    List<String> findAllPurchasersByEventId(@Param("eventId") Integer eventId);
}