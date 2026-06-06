package infrastructure.JPA;
import domain.Suspension.Suspension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuspensionJpaRepository extends JpaRepository<Suspension, Long> {

    List<Suspension> findByUserIdOrderByStartTimeDesc(Integer userId);
}