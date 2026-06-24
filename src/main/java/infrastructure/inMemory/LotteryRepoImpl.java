package infrastructure.inMemory;

import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import Exception.OptimisticLockingFailureException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory & !db")
public class LotteryRepoImpl implements ILotteryRepo {
    // Thread-safe map for storing lotteries
    private final ConcurrentHashMap<Integer, Lottery> lotteries;

    public LotteryRepoImpl() {
        lotteries = new ConcurrentHashMap<>();
    }

    @Override
    public Lottery findById(Integer id) {
        Lottery dbLottery = lotteries.get(id);
        if (dbLottery != null) {
            return new Lottery(dbLottery); // Return a detached copy
        }
        throw new NoSuchElementException("Lottery not found with ID: " + id);
    }

    @Override
    public List<Lottery> getAll() {
        List<Lottery> copies = new ArrayList<>();
        for (Lottery l : lotteries.values()) {
            copies.add(new Lottery(l));
        }
        return copies;
    }

    @Override
    public void delete(Integer id) {
        lotteries.remove(id);
    }

    @Override
    public synchronized void delete(Lottery lottery) {
        Lottery currentLottery = lotteries.get(lottery.getId());

        // already gone — nothing to delete
        if (currentLottery == null) {
            return;
        }

        if (currentLottery.getVersion() != lottery.getVersion()) {
            throw new OptimisticLockingFailureException(
                    "Lottery " + lottery.getId() + " version mismatch. Expected: " +
                            lottery.getVersion() + ", but found: " + currentLottery.getVersion()
            );
        }

        // atomic remove: only succeeds if currentLottery is still exactly what's in the map
        boolean removed = lotteries.remove(lottery.getId(), currentLottery);

        if (!removed) {
            throw new OptimisticLockingFailureException(
                    "Lottery " + lottery.getId() + " was modified concurrently"
            );
        }
    }

    @Override
    public synchronized void store(Lottery lottery) {
        Lottery currentLottery = lotteries.get(lottery.getId());

        // Handle initial creation
        if (currentLottery == null) {
            Lottery newEntry = new Lottery(lottery);
            lotteries.put(newEntry.getId(), newEntry);
            return;
        }

        if (currentLottery.getVersion() != lottery.getVersion()) {
            throw new OptimisticLockingFailureException(
                    "Event " + lottery.getId() + " version mismatch. Expected: " +
                            lottery.getVersion() + ", but found: " + currentLottery.getVersion()
            );
        }

        // OPTIMISTIC LOCKING C
        Lottery updatedLottery = new Lottery(lottery); // Copy incoming state
        updatedLottery.setVersion(lottery.getVersion() + 1); // Increment version for the NEW state

        // Atomic replace: only succeeds if currentLottery is exactly what's in the map
        boolean replaced = lotteries.replace(lottery.getId(), currentLottery, updatedLottery);

        if (!replaced) {
            throw new OptimisticLockingFailureException(
                    "Event " + lottery.getId() + " was modified concurrently"
            );
        }
    }
}