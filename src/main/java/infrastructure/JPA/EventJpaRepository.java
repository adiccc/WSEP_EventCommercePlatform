package infrastructure.JPA;

import domain.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventJpaRepository extends JpaRepository<Event, Integer> {

    List<Event> findByCompanyId(int companyId);

    List<Event> findByCreatorId(int creatorId);
}