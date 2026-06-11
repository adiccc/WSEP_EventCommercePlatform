package infrastructure.JPA;

import domain.user.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<Member, Integer> {
    Optional<Member> findByIdentifier(String identifier);
}