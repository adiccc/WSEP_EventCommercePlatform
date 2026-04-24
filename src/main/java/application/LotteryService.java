package application;

import domain.company.Company;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class LotteryService {
    ILotteryRepo lotteryRepo;
    IEventRepo eventRepo;
    private final Logger logger;
    private final TokenService tokenService;

    public LotteryService(ILotteryRepo lotteryRepo,IEventRepo eventRepo, TokenService tokenService) {
        this.lotteryRepo = lotteryRepo;
        this.eventRepo = eventRepo;
        this.logger = Logger.getLogger(LotteryService.class.getName());
        this.tokenService = tokenService;
    }

    public Response<Boolean> createLottery(String token, int userId, String eventId, int capacity, LocalDateTime registerWindow, double expirationTime) {
        logger.log(Level.INFO, "createLottery called");

        // check valid token
        if (!tokenService.validateToken(token)) {
            return new Response<>(false, "Invalid token");
        }
        try {
            Event event = eventRepo.findById(eventId);
            if(event.getCreatorId() != userId) {
                return new Response<>(false, "User id mismatch to the creator of this event");
            }
            if (!event.hasLottery()) {
                return new Response<>(false, "This event does not support lottery");
            }
            if (capacity <= 0) {
                return new Response<>(false, "Capacity must be greater than 0");
            }
            if (registerWindow.isBefore(LocalDateTime.now())) {
                return new Response<>(false, "Register window must be in the future");
            }
            if (registerWindow.isAfter(event.getSaleStartDate())){
                return new Response<>(false, "Register window must be before sale start date");
            }if (expirationTime <= 0) {
                return new Response<>(false, "Expiration time must be greater than 0");
            }
            Lottery lottery = new Lottery(lotteryRepo.getAll().size() + 1, eventId, capacity, registerWindow, expirationTime);
            lotteryRepo.store(lottery);

            //Todo: change the event to active (michal after rebase)

            logger.log(Level.INFO, "Lottery created successfully");
            return new Response<>(true, "Lottery created successfully");

        } catch (NoSuchElementException e) {
            logger.log(Level.SEVERE, "event not found: " + e.getMessage());
            return new Response<>(false, "event not found");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "failed creating lottery : " + e.getMessage());
            return new Response<>(false, "failed to create lottery : " + e.getMessage());
        }
    }

    public void drawLottery(int lotteryId) {
        // perform the lottery and update the winners in the repository
    }
}