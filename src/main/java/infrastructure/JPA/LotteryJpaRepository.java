package infrastructure.JPA;

import domain.lottery.Lottery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotteryJpaRepository extends JpaRepository<Lottery, Integer> {
}