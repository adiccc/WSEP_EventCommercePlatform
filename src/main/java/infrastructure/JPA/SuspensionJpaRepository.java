package infrastructure.JPA;
import domain.Suspension.Suspension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SuspensionJpaRepository extends JpaRepository<Suspension, Long> {

    Optional<Suspension> findFirstByUserIdOrderByStartTimeDesc(Integer userId);
}