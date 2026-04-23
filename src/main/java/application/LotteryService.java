package application;

import domain.lottery.ILotteryRepo;

public class LotteryService {
    ILotteryRepo lotteryRepo;

    public LotteryService(ILotteryRepo lotteryRepo) {
        this.lotteryRepo = lotteryRepo;
    }

    public void createLottery(int eventId, int capacity, String registerWindow, double expirationTime) {
        // create a new lottery and store it in the repository
    }

    public void drawLottery(int lotteryId) {
        // perform the lottery and update the winners in the repository
    }
}