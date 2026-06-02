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
import domain.dto.LotteryDTO;
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
            if (getValidatedRole(token) == null) return new Response<>(false, "Invalid token");
            // check valid token
            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("Only members can create lottery");
                return new Response<>(false, "Only members can create lottery");
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

    // getting lotteryDTO as null means we want to switch to regular sell
    public Response<Boolean> updateLottery(String token, int eventId, LotteryDTO lotteryDTO) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "updateLottery called");
            if (getValidatedRole(token) == null) return new Response<>(false, "Invalid token");
            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("Only members can update lottery");
                return new Response<>(false, "Only members can update lottery");
            }
            if (suspensionRepo.haveActiveSuspension(userId)) {
                logger.severe("User does not have write access caused by suspension");
                return new Response<>(null, "user does not have write access caused by suspension.");
            }
            Event event;
            try {
                event = eventRepo.findById(eventId);
            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                return new Response<>(false, "Event does not exist");
            }
            Company company;
            try {
                company = companyRepo.findById(event.getCompanyId());
            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "Company not found: " + e.getMessage());
                return new Response<>(false, "Company does not exist");
            }
            try {
                if (!company.isActive()) {
                    return new Response<>(false, "Company is not active");
                }
                if (!event.isActive()) {
                    return new Response<>(false, "Event is not active");
                }
                if (!company.isOwner(userId)) {
                    return new Response<>(false, "User id " + userId + " is not authorized to change the sales method");
                }
                if (lotteryDTO == null && event.hasLottery()) {
                    Lottery lottery = lotteryRepo.findById(eventId);
                    if (lottery.getNotifiedWinners().size() == 0) {
                        lotteryRepo.delete(lottery);
                        event.setHasLottery(false);
                        eventRepo.store(event);
                        logger.log(Level.INFO, "Lottery cancelled, event sales method set to regular purchase");
                        return new Response<>(true, "Sales method updated to regular purchase");
                    }
                    else {
                        logger.log(Level.WARNING, "Lottery can't be cancelled, users were notified");
                        return new Response<>(false, "Lottery can't be cancelled, users where notified");
                    }
                }
                if (lotteryDTO == null) {
                    event.setHasLottery(false);
                    eventRepo.store(event);

                    logger.log(Level.INFO, "Event already uses regular purchase");
                    return new Response<>(true, "Sales method is already regular purchase");
                }
                Lottery lottery;
                boolean isNew;
                try {
                    lottery = lotteryRepo.findById(eventId);
                    isNew = false;
                } catch (NoSuchElementException e) {
                    lottery = new Lottery(eventId, 0, LocalDateTime.now(), 0);
                    isNew = true;
                }
                lottery.updateLottery(lotteryDTO, event.getSaleStartDate());
                if (isNew) {
                    event.setHasLottery(true);
                    event.setActive(true);
                    eventRepo.store(event);
                }
                lotteryRepo.store(lottery);
                scheduleLotteryDraw(lottery);
                String msg = isNew ? "Sales method updated to lottery" : "Lottery updated successfully";
                logger.log(Level.INFO, msg);
                return new Response<>(true, msg);
            } catch (IllegalStateException | IllegalArgumentException e) {
                logger.log(Level.SEVERE, "Invalid lottery update: " + e.getMessage());
                return new Response<>(false, e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "failed updating lottery: " + e.getMessage());
                return new Response<>(false, "failed to update lottery: " + e.getMessage());
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

    // Executes the actual lottery draw. This method is called automatically by the scheduler.
    public void drawLottery(int lotteryId) {
        RetryHelper.executeWithRetry(() -> {
            drawLotteryInternal(lotteryId);
            return new Response<Void>(null, "Lottery draw completed");
        });
    }

    private void drawLotteryInternal(int lotteryId) {
        logger.log(Level.INFO, "Starting draw for lottery ID: " + lotteryId);

        try {
            Lottery lottery = lotteryRepo.findById(lotteryId);

            Map<Integer, String> winners;

            if (lottery.getWinners().isEmpty()) {
                winners = lottery.drawWinners();
                lotteryRepo.store(lottery);

                logger.log(Level.INFO, "Successfully drawn winners for lottery ID: " + lotteryId);
            } else {
                winners = lottery.getWinnerCodes();

                logger.log(Level.INFO,
                        "Lottery ID " + lotteryId + " was already drawn. Retrying missing notifications.");
            }

            notifyLotteryWinners(lotteryId, winners);

        } catch (NoSuchElementException e) {
            logger.log(Level.SEVERE, "Could not draw lottery, ID not found: " + lotteryId);

        } catch (OptimisticLockingFailureException e) {
            throw e;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during lottery draw for ID: " + lotteryId, e);
        }
    }

    private void notifyLotteryWinners(int lotteryId, Map<Integer, String> winners) {
        for (Map.Entry<Integer, String> entry : winners.entrySet()) {
            Integer winnerUserId = entry.getKey();
            String code = entry.getValue();

            try {
                Lottery freshLottery = lotteryRepo.findById(lotteryId);

                if (freshLottery.isWinnerNotified(winnerUserId)) {
                    logger.info("Winner " + winnerUserId
                            + " was already notified for lottery " + lotteryId);
                    continue;
                }

                Event event = eventRepo.findById(lotteryId); //lotteryId is the same as eventId

                String userEmail = userRepo.getUserEmail(winnerUserId);

                NotifyDTO notification = new NotifyDTO(
                        NotifyType.GENERAL_POPUP,
                        new NotifyPayload(
                                "Congratulations! You have won the lottery for event "
                                        + event.getName() + ". Your code is: " + code
                        )
                );

                Response<Void> notificationResponse =
                        sendOrSaveNotification(userEmail, notification);

                if (isNotificationHandledSuccessfully(notificationResponse)) {
                    Response<Void> markResponse =
                            markWinnerNotifiedWithRetry(lotteryId, winnerUserId);

                    if (isWinnerMarkedSuccessfully(markResponse)) {
                        logger.info("Winner " + winnerUserId
                                + " marked as notified for lottery " + lotteryId);
                    } else {
                        logger.warning("Notification was sent/saved, but failed to mark winner "
                                + winnerUserId + " as notified. Result: " + markResponse.getMessage());
                    }
                }

            } catch (OptimisticLockingFailureException e) {
                throw e;

            } catch (Exception e) {
                logger.warning("Failed to notify lottery winner "
                        + winnerUserId + ": " + e.getMessage());
            }
        }
    }

    private Response<Void> markWinnerNotifiedWithRetry(int lotteryId, int winnerUserId) {
        return RetryHelper.executeWithRetry(() -> {
            Lottery freshLottery = lotteryRepo.findById(lotteryId);

            if (freshLottery.isWinnerNotified(winnerUserId)) {
                return new Response<>(null, "Winner already marked as notified");
            }

            freshLottery.markWinnerNotified(winnerUserId);
            lotteryRepo.store(freshLottery);

            return new Response<>(null, "Winner marked as notified");
        });
    }

    private boolean isWinnerMarkedSuccessfully(Response<Void> response) {
        if (response == null || response.getMessage() == null) {
            return false;
        }

        return response.getMessage().equals("Winner marked as notified")
                || response.getMessage().equals("Winner already marked as notified");
    }

    private boolean isNotificationHandledSuccessfully(Response<Void> response) {
        if (response == null || response.getMessage() == null) {
            return false;
        }

        return response.getMessage().equals("Notification sent successfully")
                || response.getMessage().equals("Notification saved as delayed")
                || response.getMessage().equals("Notification already saved as delayed");
    }

    public Response<Boolean> registerUserToLottery(String token, int eventId) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "registerUserToLottery called");
            if (getValidatedRole(token) == null) return new Response<>(false, "Invalid token");
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

    public Response<Boolean> canRegisterToLottery(String token, int eventId) {
        return RetryHelper.executeWithRetry(() -> {

            logger.log(Level.INFO, "canRegisterToLottery called");

            if (getValidatedRole(token) == null) {
                return new Response<>(null, "Invalid token");
            }

            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                return new Response<>(null, "User is not logged in");
            }

            if (suspensionRepo.haveActiveSuspension(userId)) {
                return new Response<>(null, "user does not have write access caused by suspension.");
            }

            try {
                Event event = eventRepo.findById(eventId);

                if (!event.hasLottery()) {
                    return new Response<>(false, "This event does not support lottery");
                }

                Lottery lottery = lotteryRepo.findById(eventId);

                if (LocalDateTime.now().isAfter(lottery.getRegisterWindow())) {
                    return new Response<>(false, "Lottery registration period has expired");
                }

                if (lottery.getRegistered().contains(userId)) {
                    return new Response<>(false, "User is already registered to this lottery");
                }

                return new Response<>(true, "User can register to lottery");

            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (NoSuchElementException e) {
                return new Response<>(null, "Lottery not found");
            } catch (Exception e) {
                return new Response<>(
                        null,
                        "Failed to check lottery registration availability: "
                                + e.getMessage()
                );
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

    private void notifyTokenExpired(String token) {
        try {
            NotifyPayload payload = new NotifyPayload("Your session has expired");
            NotifyDTO expiredNotify = new NotifyDTO(NotifyType.TOKEN_EXPIRED, payload);
            notifier.notifyTab(token, expiredNotify);
        } catch (Exception e) { logger.warning("Failed to send TOKEN_EXPIRED"); }
    }

    private String getValidatedRole(String token) {
        Response<String> roleRes = auth.getRole(token);
        if (roleRes.getValue() == null) {
            notifyTokenExpired(token);
            return null;
        }
        return roleRes.getValue();
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

                if (isDelivered) {
                    return new Response<>(null, "Notification sent successfully");
                }

                boolean alreadySaved = member.getDelayedNotifications().stream()
                        .anyMatch(existing ->
                                existing.getType() == notifyDTO.getType()
                                        && existing.getPayload() != null
                                        && notifyDTO.getPayload() != null
                                        && existing.getPayload().getMessage() != null
                                        && existing.getPayload().getMessage()
                                        .equals(notifyDTO.getPayload().getMessage())
                        );

                if (alreadySaved) {
                    logger.info("Delayed notification already exists for: "
                            + member.getIdentifier());

                    return new Response<>(null, "Notification already saved as delayed");
                }

                member.addDelayedNotification(notifyDTO);
                userRepo.store(member);

                logger.info("Delayed notification saved successfully for: "
                        + member.getIdentifier());

                return new Response<>(null, "Notification saved as delayed");

            } catch (OptimisticLockingFailureException e) {
                throw e;

            } catch (Exception e) {
                logger.warning("Failed to send or save notification for "
                        + userIdentifier + ": " + e.getMessage());

                return new Response<>(null, "Failed to send or save notification");
            }
        });
    }

    //for tests
    public void shutdown() {
        scheduler.shutdownNow();
    }
}