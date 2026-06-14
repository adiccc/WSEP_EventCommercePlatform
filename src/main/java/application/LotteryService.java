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
import domain.user.NotificationStatus;
import domain.user.UserNotification;
import domain.user.IUserRepo;
import domain.user.Member;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.support.TransactionTemplate;

import static DTO.NotifyType.GENERAL_POPUP;

@Service

public class LotteryService {
    private final ILotteryRepo lotteryRepo;
    private final IEventRepo eventRepo;
    private final Logger logger;
    private final IAuth auth;
    private final ScheduledExecutorService scheduler; // ScheduledExecutorService is used to schedule tasks to run after
                                                      // a given delay
    private final ICompanyRepo companyRepo;
    private final ISuspensionRepo suspensionRepo;
    private final INotifier notifier;
    private final IUserRepo userRepo;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public LotteryService(ILotteryRepo lotteryRepo, IEventRepo eventRepo, IAuth auth, ICompanyRepo companyRepo,
            ISuspensionRepo suspensionRepo, INotifier notifier, IUserRepo userRepo,
            TransactionTemplate transactionTemplate) {
        this.lotteryRepo = lotteryRepo;
        this.eventRepo = eventRepo;
        this.logger = Logger.getLogger(LotteryService.class.getName());
        this.auth = auth;
        this.scheduler = Executors.newScheduledThreadPool(10);
        this.companyRepo = companyRepo;
        this.suspensionRepo = suspensionRepo;
        this.notifier = notifier;
        this.userRepo = userRepo;
        this.transactionTemplate = transactionTemplate;
    }

    // needed because each user has its own code and message
    private record NotificationDeliveryTask(
            String userIdentifier,
            Long notificationId,
            NotifyDTO notifyDTO) {
    }

    public Response<Boolean> createLottery(String token, int eventId, int capacity, LocalDateTime registerWindow,
            long expirationTime) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "createLottery called");
            if (getValidatedRole(token) == null)
                return new Response<>(false, "Invalid token");
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
                    return new Response<>(false,
                            "User id " + userId + " does not have permission to create lottery for this event");
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

                // Schedule the background task to draw winners when the registration window
                // closes
                scheduleLotteryDraw(lottery);

                logger.log(Level.INFO, "Lottery created successfully");
                return new Response<>(true, "Lottery created successfully");

            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "event not found: " + e.getMessage());
                return new Response<>(false, "event not found");

            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;

            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error while creating lottery: " + e.getMessage());
                throw e;

            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "failed creating lottery : " + e.getMessage(), e);
                return new Response<>(false, "failed to create lottery : " + e.getMessage());
            }
        }));
    }

    // getting lotteryDTO as null means we want to switch to regular sell
    public Response<Boolean> updateLottery(String token, int eventId, LotteryDTO lotteryDTO) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "updateLottery called");
            if (getValidatedRole(token) == null)
                return new Response<>(false, "Invalid token");
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
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                return new Response<>(false, "Event does not exist");
            }

            Company company;
            try {
                company = companyRepo.findById(event.getCompanyId());
            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
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
                    if (lottery.getWinners().isEmpty()) {
                        lotteryRepo.delete(lottery);
                        event.setHasLottery(false);
                        eventRepo.store(event);
                        logger.log(Level.INFO, "Lottery cancelled, event sales method set to regular purchase");
                        return new Response<>(true, "Sales method updated to regular purchase");
                    } else {
                        logger.log(Level.WARNING, "Lottery can't be cancelled, winners were already drawn");
                        return new Response<>(false, "Lottery can't be cancelled, winners were already drawn");
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
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Invalid lottery update: " + e.getMessage());
                return new Response<>(false, e.getMessage());

            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;

            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error while updating lottery: " + e.getMessage());
                throw e;

            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "failed updating lottery: " + e.getMessage(), e);
                return new Response<>(false, "failed to update lottery: " + e.getMessage());
            }
        }));
    }

    // Calculates the time difference and schedules the drawLottery method to
    // execute automatically.
    private void scheduleLotteryDraw(Lottery lottery) {
        // Calculate the delay in seconds between now and the end of the registration
        // window
        long delayInSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), lottery.getRegisterWindow());

        // if the registration window is already closed, we can draw the lottery
        // immediately
        if (delayInSeconds < 0) {
            delayInSeconds = 0;
        }

        // Schedule the task
        int lotteryId = lottery.getId();

        scheduler.schedule(() -> {
            try {
                lotteryRepo.findById(lotteryId);
                drawLottery(lotteryId);

            } catch (NoSuchElementException e) {
                logger.info("Skipping scheduled lottery draw because lottery no longer exists: " + lotteryId);
            }
        }, delayInSeconds, TimeUnit.SECONDS);

        logger.log(Level.INFO,
                "Scheduled lottery ID: " + lotteryId
                        + " to run in " + delayInSeconds + " seconds.");
    }

    // Executes the actual lottery draw. This method is called automatically by the
    // scheduler.
    public void drawLottery(int lotteryId) {
        Response<List<NotificationDeliveryTask>> res = RetryHelper.executeWithRetry(() -> {
            Response<List<NotificationDeliveryTask>> transactionRes = transactionTemplate.execute(status -> {
                try {
                    return drawLotteryAndSavePendingNotifications(lotteryId);

                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;

                } catch (TransientDataAccessException e) {
                    status.setRollbackOnly();
                    logger.warning("Transient DB error while drawing lottery "
                            + lotteryId + ": " + e.getMessage());
                    throw e;

                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.log(Level.SEVERE,
                            "Failed drawing lottery " + lotteryId + ": " + e.getMessage(),
                            e);
                    return new Response<>(null, "Failed drawing lottery");
                }
            });

            if (transactionRes == null || transactionRes.getValue() == null) {
                return new Response<>(null,
                        transactionRes == null ? "Lottery draw failed" : transactionRes.getMessage());
            }

            return transactionRes;
        });

        if (res == null || res.getValue() == null) {
            logger.warning("Lottery draw failed for lottery ID: " + lotteryId);
            return;
        }

        deliverLotteryNotifications(res.getValue());
    }

    private Response<List<NotificationDeliveryTask>> drawLotteryAndSavePendingNotifications(int lotteryId) {
        logger.log(Level.INFO, "drawLotteryAndSavePendingNotifications called");

        Lottery lottery = lotteryRepo.findById(lotteryId);

        if (!lottery.getWinners().isEmpty()) {
            logger.info("Lottery " + lotteryId + " was already drawn. Skipping.");
            return new Response<>(new ArrayList<>(), "Lottery already drawn");
        }

        Event event = eventRepo.findById(lotteryId);

        Map<Integer, String> winners = new HashMap<>(lottery.drawWinners());
        lotteryRepo.store(lottery);

        logger.info("Lottery winners drawn successfully for lottery " + lotteryId);

        List<NotificationDeliveryTask> tasks = new ArrayList<>();

        for (Map.Entry<Integer, String> entry : winners.entrySet()) {
            Integer winnerUserId = entry.getKey();
            String code = entry.getValue();

            Member member = userRepo.findById(winnerUserId);

            if (member == null) {
                throw new IllegalStateException("Winner member not found for user id: " + winnerUserId);
            }
            String userEmail = member.getIdentifier();

            NotifyDTO notifyDTO = buildWinnerNotification(event, code);

            Response<Long> msgIdRes = saveDelayedNotificationAsPending(userEmail, notifyDTO);

            if (msgIdRes == null || msgIdRes.getValue() == null || msgIdRes.getValue() == -1L) {
                throw new IllegalStateException("Failed saving pending notification for: " + userEmail);
            }
            logger.info("Pending winner notification saved for user: " + userEmail);

            tasks.add(new NotificationDeliveryTask(
                    userEmail,
                    msgIdRes.getValue(),
                    notifyDTO));

        }

        return new Response<>(tasks, "Lottery drawn and pending notifications saved");
    }

    private NotifyDTO buildWinnerNotification(Event event, String code) {
        NotifyPayload payload = new NotifyPayload(
                "Congratulations! You have won the lottery for event "
                        + event.getName()
                        + ". Your code is: "
                        + code,
                event.getId(),
                null);

        return new NotifyDTO(GENERAL_POPUP, payload);
    }

    private void deliverLotteryNotifications(List<NotificationDeliveryTask> tasks) {
        for (NotificationDeliveryTask task : tasks) {
            try {
                boolean isDelivered = notifier.notifyUser(
                        task.userIdentifier(),
                        task.notifyDTO());

                if (!isDelivered) {
                    logger.log(Level.INFO,
                            "Lottery notification could not be sent live to: "
                                    + task.userIdentifier()
                                    + ", the message is pending and will be sent after login");
                    continue;
                }

                logger.log(Level.INFO,
                        "Lottery notification sent to: " + task.userIdentifier());

                markNotificationAsDelivered(task.userIdentifier(), task.notificationId());

                logger.log(Level.INFO,
                        "Lottery notification to: "
                                + task.userIdentifier()
                                + " marked as DELIVERED");

            } catch (Exception e) {
                logger.log(Level.WARNING,
                        "Failed to send live lottery notification to "
                                + task.userIdentifier()
                                + ". Notification remains PENDING.",
                        e);
            }
        }
    }

    public Response<Boolean> registerUserToLottery(String token, int eventId) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "registerUserToLottery called");
            if (getValidatedRole(token) == null)
                return new Response<>(false, "Invalid token");
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
                status.setRollbackOnly();
                throw e;

            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error while registering user to lottery: " + e.getMessage());
                throw e;

            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Failed to register user to lottery: " + e.getMessage(), e);
                return new Response<>(false, "Failed to register user to lottery: " + e.getMessage());
            }
        }));
    }

    public Response<Boolean> canRegisterToLottery(String token, int eventId) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {

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
                status.setRollbackOnly();
                throw e;
            } catch (NoSuchElementException e) {
                return new Response<>(null, "Lottery not found");
            } catch (Exception e) {
                return new Response<>(
                        null,
                        "Failed to check lottery registration availability: "
                                + e.getMessage());
            }
        }));
    }

    private int getUserIdFromToken(String token) {
        String email = auth.getUserEmail(token).getValue();
        if (email != null) {
            Member m = userRepo.findUserByEmail(email);
            if (m != null)
                return m.getUserId();
        }
        return -1;
    }

    private void notifyTokenExpired(String token) {
        try {
            NotifyPayload payload = new NotifyPayload("Your session has expired");
            NotifyDTO expiredNotify = new NotifyDTO(NotifyType.TOKEN_EXPIRED, payload);
            notifier.notifyTab(token, expiredNotify);
        } catch (Exception e) {
            logger.warning("Failed to send TOKEN_EXPIRED");
        }
    }

    private String getValidatedRole(String token) {
        Response<String> roleRes = auth.getRole(token);
        if (roleRes.getValue() == null) {
            notifyTokenExpired(token);
            return null;
        }
        return roleRes.getValue();
    }

    // for saving the notifications as pending in order to handle Persistence before
    // trying to send in real-time
    private Response<Long> saveDelayedNotificationAsPending(String userIdentifier, NotifyDTO notifyDTO) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            try {
                Member member = userRepo.findUserByEmail(userIdentifier);

                if (member == null) {
                    logger.warning("User not found for identifier: " + userIdentifier);
                    return new Response<>(null, "User not found");
                }
                for (UserNotification existing : member.getPendingNotifications()) {
                    if (existing.getStatus() == NotificationStatus.PENDING
                            && existing.getType() == notifyDTO.getType()
                            && existing.getPayload() != null
                            && notifyDTO.getPayload() != null
                            && existing.getPayload().getMessage() != null
                            && existing.getPayload().getMessage().equals(notifyDTO.getPayload().getMessage())) {

                        logger.info("Pending notification already exists for: " + member.getIdentifier());
                        return new Response<>(existing.getNotificationId(), "Notification already saved as pending");
                    }
                }
                UserNotification userNotification = new UserNotification(notifyDTO.getType(), notifyDTO.getPayload());
                member.addPendingNotification(userNotification);
                userRepo.store(member);
                member = userRepo.findUserByEmail(userIdentifier);
                Long msgId = member.getMessageId(userNotification);

                logger.info("Pending notification saved successfully for: " + member.getIdentifier());
                return new Response<>(msgId, "Notification saved as pending");

            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error detected, retrying... " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.severe("Fatal error during notification save: " + e.getMessage());
                return new Response<>(-1L, "Fatal error");
            }
        }));
    }

    private Response<Boolean> markNotificationAsDelivered(String userIdentifier, Long notificationId) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            try {
                Member member = userRepo.findUserByEmail(userIdentifier);
                if (member != null) {
                    member.setMessageStatus(notificationId, NotificationStatus.DELIVERED);
                    userRepo.store(member);
                }
                return new Response<>(true, "Notification marked as DELIVERED");

            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;

            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error while marking notification as delivered: " + e.getMessage());
                throw e;

            } catch (Exception e) {
                status.setRollbackOnly();
                logger.warning("Failed to mark notification as delivered: " + e.getMessage());
                return new Response<>(false, "Failed to mark notification as delivered");
            }
        }));
    }

    @PostConstruct
    public void reschedulePendingLotteriesOnStartup() {
        Response<List<Lottery>> response = RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            List<Lottery> result = new ArrayList<>();

            for (Lottery lottery : lotteryRepo.getAll()) {
                if (shouldScheduleLotteryOnStartup(lottery)) {
                    result.add(new Lottery(lottery));
                }
            }

            return new Response<>(result, "Pending lotteries loaded successfully");
        }));

        if (response == null || response.getValue() == null) {
            logger.severe("Failed to load pending lotteries on startup");
            return;
        }

        for (Lottery lottery : response.getValue()) {
            scheduleLotteryDraw(lottery);
        }

        logger.info("Pending lotteries were rescheduled successfully on startup");
    }

    // draw winners + create pending notifications is in the same transaction, so we
    // can be sure that if the lottery was drawn, the notifications are saved and
    // will be sent when the user logs in (in case of failure in real-time
    // notification)
    private boolean shouldScheduleLotteryOnStartup(Lottery lottery) {
        if (!lottery.getWinners().isEmpty()) {
            return false;
        }

        if (lottery.getRegistered().isEmpty()
                && LocalDateTime.now().isAfter(lottery.getRegisterWindow())) {
            return false;
        }

        return true;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}