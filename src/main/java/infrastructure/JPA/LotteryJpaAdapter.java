package infrastructure.JPA;

import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.NoSuchElementException;

@Repository
@Profile("lottery-db")
public class LotteryJpaAdapter implements ILotteryRepo {

    private final LotteryJpaRepository lotteryJpaRepository;

    public LotteryJpaAdapter(LotteryJpaRepository lotteryJpaRepository) {
        this.lotteryJpaRepository = lotteryJpaRepository;
    }

    @Override
    public Lottery findById(Integer id) {
        return lotteryJpaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lottery not found with ID: " + id));
    }

    @Override
    public List<Lottery> getAll() {
        return lotteryJpaRepository.findAll();
    }

    @Override
    public void store(Lottery lottery) {
        try {
            lotteryJpaRepository.save(lottery);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockingFailureException(
                    "Event " + lottery.getId() + " was modified concurrently");
        }
    }

    @Override
    public void delete(Integer id) {
        lotteryJpaRepository.deleteById(id);
    }

    @Override
    public void delete(Lottery lottery) {
        try {
            lotteryJpaRepository.delete(lottery);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockingFailureException(
                    "Lottery " + lottery.getId() + " was modified concurrently during deletion");
        }
    }
}