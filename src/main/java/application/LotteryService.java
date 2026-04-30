package application;

import domain.event.Event;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;
import Exception.OptimisticLockingFailureException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;


public class LotteryService {
    ILotteryRepo lotteryRepo;
    IEventRepo eventRepo;
    private final Logger logger;
    private final IAuth auth;
    private final ScheduledExecutorService scheduler; // ScheduledExecutorService is used to schedule tasks to run after a given delay

    public LotteryService(ILotteryRepo lotteryRepo,IEventRepo eventRepo, IAuth auth) {
        this.lotteryRepo = lotteryRepo;
        this.eventRepo = eventRepo;
        this.logger = Logger.getLogger(LotteryService.class.getName());
        this.auth = auth;
        this.scheduler = Executors.newScheduledThreadPool(10);
    }

    public Response<Boolean> createLottery(String token, String eventId, int capacity, LocalDateTime registerWindow, long expirationTime) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "createLottery called");

            // check valid token
            int userId = auth.getUserId(token).getValue();
            if (userId == -1) {
                logger.severe("Invalid token");
                return new Response<>(false, "Invalid token");
            }
            try {
                Event event = eventRepo.findById(eventId);
                if (event.getCreatorId() != userId) {
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
                if (registerWindow.isAfter(event.getSaleStartDate())) {
                    return new Response<>(false, "Register window must be before sale start date");
                }
                if (expirationTime <= 0) {
                    return new Response<>(false, "Expiration time must be greater than 0");
                }
                Lottery lottery = new Lottery(eventId, capacity, registerWindow, expirationTime);
                event.setActive(true);
                eventRepo.store(event);
                lotteryRepo.store(lottery);

                // Schedule the background task to draw winners when the registration window closes
                scheduleLotteryDraw(lottery);


                logger.log(Level.INFO, "Lottery created successfully");
                return new Response<>(true, "Lottery created successfully");

                } catch (NoSuchElementException e) {
                    logger.log(Level.SEVERE, "event not found: " + e.getMessage());
                    return new Response<>(false, "event not found");
                } catch (OptimisticLockingFailureException e) {
                    throw e;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "failed creating lottery : " + e.getMessage());
                    return new Response<>(false, "failed to create lottery : " + e.getMessage());
                }
        });
    }


    //Calculates the time difference and schedules the drawLottery method to execute automatically.
    private void scheduleLotteryDraw(Lottery lottery) {
        // Calculate the delay in seconds between now and the end of the registration window
        long delayInSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), lottery.getRegisterWindow());

        // if the registration window is already closed, we can draw the lottery immediately
        if (delayInSeconds < 0) {
            delayInSeconds = 0;
        }

        // Schedule the task
        scheduler.schedule(() -> drawLottery(lottery.getId()), delayInSeconds, TimeUnit.SECONDS);
        logger.log(Level.INFO, "Scheduled lottery ID: " + lottery.getId() + " to run in " + delayInSeconds + " seconds.");
    }


    //Executes the actual lottery draw. This method is called automatically by the scheduler.
    public void drawLottery(String lotteryId) {
        logger.log(Level.INFO, "Starting draw for lottery ID: " + lotteryId);
        try {
            // Retrieve the lottery from the database
            Lottery lottery = lotteryRepo.findById(lotteryId);
            // Perform the domain logic to select winners
            lottery.drawWinners();
            //Save the updated lottery state (with the populated winners list) back to the database
            lotteryRepo.store(lottery);
            logger.log(Level.INFO, "Successfully drawn winners for lottery ID: " + lotteryId + ". Total winners: " + lottery.getWinners().size());
            // notify winners (not in this version)
        } catch (NoSuchElementException e) {
            logger.log(Level.SEVERE, "Could not draw lottery, ID not found: " + lotteryId);
        } catch (OptimisticLockingFailureException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during lottery draw for ID: " + lotteryId, e);
        }
    }

    // TODO: this implemetation is for test only, this function should be implemented
    public Response<Boolean> registerUserToLottery(String token, String eventId) {
        Lottery lottery=lotteryRepo.findById(eventId);
        int userId = auth.getUserId(token).getValue();
        lottery.registerUserToLottery(userId);
        lotteryRepo.store(lottery);
        return new Response<>(true, "User registered to lottery");
    }
}