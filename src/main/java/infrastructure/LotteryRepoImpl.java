package infrastructure;

import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;

import java.util.*;

public class LotteryRepoImpl implements ILotteryRepo {
    Map<String, Lottery> lotteries; // key: lotteryId, value: Lottery

    public LotteryRepoImpl() {
        lotteries = new HashMap<>();
    }

    @Override
    public Lottery findById(String id) {
        if (lotteries.containsKey(id))
            return lotteries.get(id);
        throw new NoSuchElementException();
    }

    @Override
    public List<Lottery> getAll() {
        return new ArrayList<>(lotteries.values());
    }

    @Override
    public void delete(String id) {
        lotteries.remove(id);
    }

    @Override
    public void store(Lottery entity) {
        lotteries.put(entity.getId(), entity);
    }

}
