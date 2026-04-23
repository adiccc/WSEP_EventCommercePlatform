package infrastructure;

import domain.event.Event;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;

import java.util.*;

public class LotteryRepoImpl implements ILotteryRepo {
    Map<Integer, Lottery> lotteries; // key: lotteryId, value: Lottery

    public LotteryRepoImpl() {
        lotteries = new HashMap<>();
    }

    @Override
    public Lottery findById(Integer id) {
        if(lotteries.containsKey(id))
            return lotteries.get(id);
        throw new NoSuchElementException();
    }

    @Override
    public List<Lottery> getAll() {
        return new ArrayList<>(lotteries.values());
    }

    @Override
    public void delete(Integer id) {
        lotteries.remove(id);
    }

    @Override
    public void store(Lottery entity) {
        lotteries.put(entity.getId(), entity);

    }

    @Override
    public void update(Lottery entity) {
        lotteries.put(entity.getId(), entity);
    }
}
