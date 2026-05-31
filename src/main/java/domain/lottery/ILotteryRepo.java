package domain.lottery;

import domain.IRepo;

public interface ILotteryRepo extends IRepo<Lottery, Integer> {

    // version-checked delete: only removes if the supplied lottery matches the stored version
    void delete(Lottery lottery);
}
