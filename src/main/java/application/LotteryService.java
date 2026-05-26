package application;

import DTO.NotifyDTO;
import DTO.NotifyPayload;
import DTO.NotifyType;
import domain.Suspension.ISuspensionRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dataType.PermissionType;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;
import Exception.OptimisticLockingFailureException;
import domain.user.IUserRepo;
import domain.user.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;
@Service

public class LotteryService {
    private final ILotteryRepo lotteryRepo;
    private final IEventRepo eventRepo;
    private final Logger logger;
    private final IAuth auth;
    private final ScheduledExecutorService scheduler; // ScheduledExecutorService is used to schedule tasks to run after a given delay
    private final ICompanyRepo companyRepo;
    private final ISuspensionRepo suspensionRepo;
    private final INotifier notifier;
    private final IUserRepo userRepo;

    @Autowired
    public LotteryService(ILotteryRepo lotteryRepo,IEventRepo eventRepo, IAuth auth, ICompanyRepo companyRepo, ISuspensionRepo suspensionRepo, INotifier notifier, IUserRepo userRepo) {
        this.lotteryRepo = lotteryRepo;
        this.eventRepo = eventRepo;
        this.logger = Logger.getLogger(LotteryService.class.getName());
        this.auth = auth;
        this.scheduler = Executors.newScheduledThreadPool(10);
        this.companyRepo = companyRepo;
        this.suspensionRepo=suspensionRepo;
        this.notifier = notifier;
        this.userRepo = userRepo;
    }

    public Response<Boolean> createLottery(String token, int eventId, int capacity, LocalDateTime registerWindow, long expirationTime) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "createLottery called");

            // check valid token
            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("Invalid token");
                return new Response<>(false, "Invalid token");
            }
            if (suspensionRepo.haveActiveSuspension(userId)) {
                logger.severe("User does not have write access caused by suspension");
                return new Response<>(null, "user does not have write access caused by suspension.");
            }
            try {
                Event event = eventRepo.findById(eventId);
                Company company = companyRepo.findById(event.getCompanyId());
                if (!company.checkPermission(userId, PermissionType.CREATE_EVENT)) {
                    return new Response<>(false, "User id " + userId + " does not have permission to create lottery for this event");
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
    public void drawLottery(int lotteryId) {
        logger.log(Level.INFO, "Starting draw for lottery ID: " + lotteryId);
        try {
            // Retrieve the lottery from the database
            Lottery lottery = lotteryRepo.findById(lotteryId);
            if (!lottery.getWinners().isEmpty()) {
                logger.log(Level.INFO, "Lottery ID " + lotteryId + " was already drawn.");
                return;
            }
            // Perform the domain logic to select winners
            Map<Integer,String> winners = lottery.drawWinners();
            //Save the updated lottery state (with the populated winners list) back to the database
            lotteryRepo.store(lottery);
            logger.log(Level.INFO, "Successfully drawn winners for lottery ID: " + lotteryId);
            // Notify the winners
            // Notify the winners
            for (Integer winner : winners.keySet()) {
                try {
                    int userId = winner;
                    String code = winners.get(winner);
                    String userEmail = userRepo.getUserEmail(userId);

                    NotifyDTO notification = new NotifyDTO(
                            NotifyType.GENERAL_POPUP,
                            new NotifyPayload(
                                    "Congratulations! You have won the lottery for event "
                                            + lotteryId + ". Your code is: " + code
                            )
                    );

                    Response<Void> notificationResponse =
                            sendOrSaveNotification(userEmail, notification);

                    logger.info("Lottery winner notification result for user "
                            + userEmail + ": " + notificationResponse.getMessage());

                } catch (OptimisticLockingFailureException e) {
                    throw e;

                } catch (Exception e) {
                    logger.warning("Failed to notify lottery winner " + winner + ": " + e.getMessage());
                }
            }
        } catch (NoSuchElementException e) {
            logger.log(Level.SEVERE, "Could not draw lottery, ID not found: " + lotteryId);
        } catch (OptimisticLockingFailureException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during lottery draw for ID: " + lotteryId, e);
        }
    }

    public Response<Boolean> registerUserToLottery(String token, int eventId) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "registerUserToLottery called");

            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("User is not logged in");
                return new Response<>(false, "User is not logged in");
            }
            if (suspensionRepo.haveActiveSuspension(userId)) {
                logger.severe("User does not have write access caused by suspension");
                return new Response<>(null, "user does not have write access caused by suspension.");
            }

            try {
                Event event = eventRepo.findById(eventId);

                if (!event.hasLottery()) {
                    logger.severe("This event does not support lottery");
                    return new Response<>(false, "This event does not support lottery");
                }

                Lottery lottery;
                try {
                    lottery = lotteryRepo.findById(eventId);
                } catch (NoSuchElementException e) {
                    logger.log(Level.SEVERE, "Lottery not found: " + e.getMessage());
                    return new Response<>(false, "Lottery not found");
                }

                if (LocalDateTime.now().isAfter(lottery.getRegisterWindow())) {
                    logger.severe("Lottery registration period has expired");
                    return new Response<>(false, "Lottery registration period has expired");
                }

                if (lottery.getRegistered().contains(userId)) {
                    logger.severe("User is already registered to this lottery");
                    return new Response<>(false, "User is already registered to this lottery");
                }

                lottery.registerUserToLottery(userId);
                lotteryRepo.store(lottery);

                logger.log(Level.INFO, "User registered to lottery successfully");
                return new Response<>(true, "User registered to lottery successfully");

            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                return new Response<>(false, "Event not found");

            } catch (OptimisticLockingFailureException e) {
                throw e;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to register user to lottery: " + e.getMessage());
                return new Response<>(false, "Failed to register user to lottery: " + e.getMessage());
            }
        });
    }
    private int getUserIdFromToken(String token) {
        String email = auth.getUserEmail(token).getValue();
        if (email != null) {
            Member m = userRepo.findUserByEmail(email);
            if (m != null) return m.getUserId();
        }
        return -1;
    }

    // Helper method to send a real-time notification or save it as delayed if the user is offline.
    private Response<Void> sendOrSaveNotification(String userIdentifier, NotifyDTO notifyDTO) {
        return RetryHelper.executeWithRetry(() -> {
            try {
                Member member = userRepo.findUserByEmail(userIdentifier);

                if (member == null) {
                    logger.warning("User not found for identifier: " + userIdentifier);
                    return new Response<>(null, "User not found");
                }

                boolean isDelivered = notifier.notifyUser(member.getIdentifier(), notifyDTO);

                if (!isDelivered) {
                    member.addDelayedNotification(notifyDTO);
                    userRepo.store(member);

                    logger.info("Delayed notification saved successfully for: " + member.getIdentifier());
                    return new Response<>(null, "Notification saved as delayed");
                }

                return new Response<>(null, "Notification sent successfully");

            } catch (OptimisticLockingFailureException e) {
                throw e;

            } catch (Exception e) {
                logger.warning("Failed to send or save notification for "
                        + userIdentifier + ": " + e.getMessage());

                return new Response<>(null, "Failed to send or save notification");
            }
        });
    }

}