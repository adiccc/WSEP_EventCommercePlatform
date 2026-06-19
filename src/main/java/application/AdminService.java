package application;

import DTO.*;
import domain.Suspension.ISuspensionRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.company.Permissions;
import domain.dto.SuspensionDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.event.OrderStatus;
import domain.user.NotificationStatus;
import domain.user.UserNotification;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.event.Order;
import domain.Suspension.Suspension;
import domain.webQueue.WebQueue;
import Exception.OptimisticLockingFailureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static DTO.NotifyType.GENERAL_POPUP;

@Service
public class AdminService {
    private final IEventRepo eventRepo;
    private final IPaymentSystem paymentSystem;
    private final ICompanyRepo companyRepo;
    private final ISuspensionRepo suspensionRepo;
    private static final Logger logger = Logger.getLogger(AdminService.class.getName());

    private final IAuth auth;
    private final IUserRepo userRepo;
    private final ScheduledExecutorService scheduler;
    private final INotifier notifier;
    private final TransactionTemplate transactionTemplate;
    private final ITicketSupply ticketSupply;



    @Autowired
    public AdminService(IAuth auth, IUserRepo userRepo, ICompanyRepo companyRepo, IEventRepo eventRepo, IPaymentSystem paymentSystem, ISuspensionRepo suspensionRepo, INotifier notifier, TransactionTemplate transactionTemplate, ITicketSupply ticketSupply) {
        this.auth = auth;
        this.userRepo = userRepo;
        this.eventRepo = eventRepo;
        this.paymentSystem = paymentSystem;
        this.companyRepo = companyRepo;
        this.suspensionRepo = suspensionRepo;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.notifier = notifier;
        this.transactionTemplate = transactionTemplate;
        this.ticketSupply = ticketSupply;
    }

    //permanent suspension
    public Response<Boolean> SuspendUser(String token, int userId) {
        return RetryHelper.executeWithRetry(()->{
            logger.log(Level.INFO, "SuspendUser called");
            return transactionTemplate.execute(status -> {
                try{
                    if(!isVerifiedAdmin(token)){
                        logger.log(Level.INFO, "SuspendUser failed : user is not admin");
                        return new Response<>(false, "SuspendUser failed : user is not admin");
                    }
                    if(getUserIdFromToken(token) == userId){
                        logger.log(Level.INFO, "SuspendUser failed : admin cannot suspend himself");
                        return new Response<>(false, "SuspendUser failed : admin cannot suspend himself");
                    }
                    Member member=userRepo.findById(userId);
                    if(member.isSuspended()){
                        logger.log(Level.INFO, "SuspendUser failed : user is already suspended");
                        return new Response<>(false, "SuspendUser failed : user is already suspended");
                    }
                    member.suspend();
                    suspensionRepo.store(new Suspension(userId));
                    userRepo.store(member);
                    logger.log(Level.INFO, "SuspendUser succeeded, user "+userId+" suspended");
                    return new Response<>(true, "Suspension succeeded, user "+userId+" suspended");
                }catch(OptimisticLockingFailureException e){
                    status.setRollbackOnly();
                    throw e;
                }catch(NoSuchElementException e){
                    status.setRollbackOnly();
                    logger.log(Level.INFO, "User not found");
                    return new Response<>(false, "User not found");
                }catch(Exception e){
                    status.setRollbackOnly();
                    logger.log(Level.SEVERE, "OptimisticLockingFailureException", e);
                    return new Response<>(false,"SuspendUser faild due to serer error: "+e.getMessage());
                }
            });
        });
    }

    //temp suspension , duration in days
    public Response<Boolean> SuspendUser(String token, int userId, int duration) {
        return RetryHelper.executeWithRetry(()->{
            logger.log(Level.INFO, "SuspendUser called");
            return transactionTemplate.execute(status -> {
                try{
                    if(!isVerifiedAdmin(token)){
                        logger.log(Level.INFO, "SuspendUser failed : user is not admin");
                        return new Response<>(false, "SuspendUser failed : user is not admin");
                    }
                    if(getUserIdFromToken(token) == userId){
                        logger.log(Level.INFO, "SuspendUser failed : admin cannot suspend himself");
                        return new Response<>(false, "SuspendUser failed : admin cannot suspend himself");
                    }
                    Member member=userRepo.findById(userId);
                    if(member.isSuspended()){
                        logger.log(Level.INFO, "SuspendUser failed : user is already suspended");
                        return new Response<>(false, "SuspendUser failed : user is already suspended");
                    }
                    if(duration<=0){
                        logger.log(Level.INFO, "SuspendUser failed : duration must be greater than 0");
                        return new Response<>(false, "SuspendUser failed : duration must be greater than 0");
                    }
                    member.suspend();
                    suspensionRepo.store(new Suspension(userId, duration));
                    userRepo.store(member);
                    scheduleActivateAfterSuspension(userId, duration);
                    logger.log(Level.INFO, "SuspendUser succeeded, user "+userId+" suspended");
                    return new Response<>(true, "Suspension succeeded, user "+userId+" suspended");
                }catch(OptimisticLockingFailureException e){
                    status.setRollbackOnly();
                    throw e;
                }catch(NoSuchElementException e){
                    status.setRollbackOnly();
                    logger.log(Level.INFO, "User not found");
                    return new Response<>(false, "User not found");
                }catch(Exception e){
                    status.setRollbackOnly();
                    logger.log(Level.SEVERE, "OptimisticLockingFailureException", e);
                    return new Response<>(false,"SuspendUser faild due to serer error: "+e.getMessage());
                }
            });
        });
    }

    public Response<Boolean> UnsuspendUser(String token, int userId) {
        return RetryHelper.executeWithRetry(()->{
            logger.log(Level.INFO, "UnsuspendUser called");
            return transactionTemplate.execute(status -> {
                try{
                    if(!isVerifiedAdmin(token)){
                        logger.log(Level.INFO, "UnsuspendUser failed : user is not admin");
                        return new Response<>(false, "UnsuspendUser failed : user is not admin");
                    }
                    Member member=userRepo.findById(userId);
                    if(!member.isSuspended()){
                        logger.log(Level.INFO, "UnsuspendUser failed : user is not suspended");
                        return new Response<>(false, "UnsuspendUser failed : user is not suspended");
                    }
                    member.unsuspend();
                    Suspension currentSus = suspensionRepo.findLastSuspensionByUserId(userId);
                    currentSus.unsuspend();
                    suspensionRepo.store(currentSus);
                    userRepo.store(member);
                    logger.log(Level.INFO, "UnsuspendUser succeeded, user "+userId+" not suspended");
                    return new Response<>(true, "UnsuspendUser succeeded, user "+userId+" not suspended");
                }catch(OptimisticLockingFailureException e){
                    status.setRollbackOnly();
                    throw e;
                }catch(NoSuchElementException e){
                    status.setRollbackOnly();
                    logger.log(Level.INFO, "User not found");
                    return new Response<>(false, "User not found");
                }catch(Exception e){
                    status.setRollbackOnly();
                    logger.log(Level.SEVERE, "OptimisticLockingFailureException", e);
                    return new Response<>(false,"UnsuspendUser failed due to serer error: "+e.getMessage());
                }
            });
        });
    }

    public Response<List<SuspensionDTO>> getAllUsersSuspensions(String token) {
        return RetryHelper.executeWithRetry(()-> {
            logger.log(Level.INFO, "getAllUsersSuspensions called");
            if (!isVerifiedAdmin(token)) {
                logger.log(Level.INFO, "getAllUsersSuspensions failed : user is not admin");
                return new Response<>(null, "getAllUsersSuspensions failed : user is not admin");
            }
            List<Suspension> allSuspension = suspensionRepo.getAll();
            List<SuspensionDTO> suspensionDTOList = new ArrayList<>();
            for (Suspension suspension : allSuspension) {
                suspensionDTOList.add(new SuspensionDTO(suspension));
            }
            logger.log(Level.INFO, "getAllUsersSuspensions succeeded");
            return new Response<>(suspensionDTOList, "getAllUsersSuspensions succeeded");
        });
    }

    private void scheduleActivateAfterSuspension(int userId, int duration) {
        scheduler.schedule( ()-> RetryHelper.executeWithRetry(()->{
                    logger.log(Level.INFO, "ScheduleActivateAfterSuspension called");
                    return transactionTemplate.execute(status -> {
                        try {
                            Member member = userRepo.findById(userId);
                            member.unsuspend();
                            userRepo.store(member);
                            logger.log(Level.INFO, "activate after suspension succeeded, user "+userId+" not suspended");
                            return new Response<>(true, "Suspension succeeded, user "+userId+" suspended");
                        }catch (OptimisticLockingFailureException e) {
                            status.setRollbackOnly();
                            throw e;
                        }catch (NoSuchElementException e){
                            status.setRollbackOnly();
                            logger.log(Level.INFO, "User not found");
                            return new Response<>(false,"User not found");
                        }catch (Exception e){
                            status.setRollbackOnly();
                            logger.log(Level.SEVERE, "SuspendUser faild due to serer error: ", e.getMessage());
                            return new Response<>(false,"SuspendUser faild due to serer error: "+e.getMessage());
                        }
                    });
                })
                , duration, TimeUnit.DAYS);
    }

    public Response<Boolean> setMaxCapacity(String token, int capacity) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("setMaxCapacity attempt");
            try {
                if (!isVerifiedAdmin(token)) {
                    logger.warning("setMaxCapacity failed: unauthorized");
                    return Response.error("Unauthorized: admin access required");
                }
                WebQueue.getInstance().setMaxCapacity(capacity);
                logger.info("Max capacity updated to: " + capacity);
                return Response.ok(true);
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("setMaxCapacity failed due to server error: " + e.getMessage());
                return Response.error(e.getMessage());
            }
        });
    }

    public Response<Integer> getMaxCapacity(String token) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("getMaxCapacity attempt");
            try {
                if (!isVerifiedAdmin(token)) {
                    logger.warning("getMaxCapacity failed: unauthorized");
                    return Response.error("Unauthorized: admin access required");
                }
                return Response.ok(WebQueue.getInstance().getMaxCapacity());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("getMaxCapacity failed due to server error: " + e.getMessage());
                return Response.error(e.getMessage());
            }
        });
    }

    public Response<Integer> getActiveCount(String token) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("getActiveCount attempt");
            try {
                if (!isVerifiedAdmin(token)) {
                    logger.warning("getActiveCount failed: unauthorized");
                    return Response.error("Unauthorized: admin access required");
                }
                return Response.ok(WebQueue.getInstance().getActiveCount());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("getActiveCount failed due to server error: " + e.getMessage());
                return Response.error(e.getMessage());
            }
        });
    }

    /**
     * Removes a member from the platform entirely.
     * Effects:
     * - Member account is deactivated (isActive = false)
     * - If the user is the founder of a company → removal is blocked (return error)
     * - If the user is an owner (non-founder) → removed from ownerIds;
     * all managers they appointed cascade to the founder
     * - If the user is a manager → removed from companyTree;
     * all sub-managers they appointed cascade to the founder
     * - If the user is the creator of any event → creator is reassigned to that
     * company's founder
     */
    public Response<Boolean> removeUser(String adminToken, int userIdToRemove) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("removeUser attempt for userId: " + userIdToRemove);
            return transactionTemplate.execute(status -> {
                try {
                    if (!isVerifiedAdmin(adminToken)) {
                        logger.warning("removeUser failed: unauthorized");
                        return Response.error("Unauthorized: admin access required");
                    }

                    Member member = userRepo.findById(userIdToRemove);
                    if (member == null) {
                        logger.warning("removeUser failed: userId " + userIdToRemove + " not found");
                        return Response.error("User not found");
                    }
                    if (!member.isActive()) {
                        logger.warning("removeUser: userId " + userIdToRemove + " already inactive");
                        return Response.error("User is already removed");
                    }
                    int currentId = getUserIdFromToken(adminToken);
                    if(currentId == userIdToRemove) {
                        logger.warning("removeUser: Can't remove userId " + userIdToRemove + " because it is admin");
                        return Response.error("Can't remove user admin");
                    }

                    // Step 1: cascade company permissions
                    for (Company company : companyRepo.getAll()) {
                        Permissions perms = company.getCompanyPermission();
                        boolean changed = false;

                        if (perms.getFounderId() == userIdToRemove) {
                            logger.warning("removeUser blocked: userId " + userIdToRemove + " is the founder of company "
                                    + company.getCompanyId());
                            // abort: undo any company changes already made earlier in this loop
                            status.setRollbackOnly();
                            return Response.error("Cannot remove user: they are the founder of company \""
                                    + company.getCompanyName() + "\"");

                        } else if (perms.isOwner(userIdToRemove)) {
                            // Owner removed → reassign any managers they appointed to the founder
                            perms.removeOwner(userIdToRemove);
                            for (Integer managerId : perms.getCompanyTree().keySet()) {
                                if (perms.getCompanyTree().get(managerId).getMyManager() == userIdToRemove)
                                    perms.changeAppointer(managerId, perms.getFounderId());
                            }
                            changed = true;

                        } else if (perms.isManager(userIdToRemove)) {
                            // Manager removed → clean up appointer's list + reassign sub-managers to founder
                            perms.removeManagerFromTree(userIdToRemove);
                            changed = true;
                        }

                        if (changed)
                            companyRepo.store(company);
                    }

                    // Step 2: reassign event creator to that company's founder
                    List<Event> ownedEvents = eventRepo.findByCreator(userIdToRemove);
                    for (Event event : ownedEvents) {
                        try {
                            Company eventCompany = companyRepo.findById(event.getCompanyId());
                            event.setCreatorId(eventCompany.getFounderId());
                            eventRepo.store(event);
                            logger.info("removeUser: reassigned event " + event.getId() + " creator to founder "
                                    + eventCompany.getFounderId());
                        } catch (NoSuchElementException e) {
                            logger.warning("removeUser: company not found for event " + event.getId()
                                    + ", skipping creator reassignment");
                        }
                    }

                    // Step 3: deactivate the user
                    member.deactivate();
                    userRepo.store(member);
                    //sending notification so the user is removed he can't do anything on the website
                    try {
                        NotifyPayload payload = new NotifyPayload("Your account has been removed by the administrator.");
                        NotifyDTO kickoutNotify = new NotifyDTO(NotifyType.ACCOUNT_REMOVED, payload);

                        notifier.notifyUser(member.getIdentifier(), kickoutNotify); //notify for all tabs just in realtime
                        logger.info("Sent real-time kickout notification to removed user: " + member.getIdentifier());
                    } catch (Exception ex) {
                        logger.warning("Failed to send kickout notification to user: " + member.getIdentifier());
                    }

                    logger.info("removeUser succeeded for userId: " + userIdToRemove);
                    return Response.ok(true);

                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("removeUser failed for userId: " + userIdToRemove + ". Error: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            });
        });
    }

    public Response<Integer> getWaitingCount(String token) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("getWaitingCount attempt");
            try {
                if (!isVerifiedAdmin(token)) {
                    logger.warning("getWaitingCount failed: unauthorized");
                    return Response.error("Unauthorized: admin access required");
                }
                return Response.ok(WebQueue.getInstance().getWaitingCount());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("getWaitingCount failed due to server error: " + e.getMessage());
                return Response.error(e.getMessage());
            }
        });
    }

    public Response<Boolean> closeCompanyByAdmin(String token, int companyId) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("closeCompanyByAdmin attempt for companyId: " + companyId);

            if (!isVerifiedAdmin(token)) {
                logger.warning("closeCompanyByAdmin failed: unauthorized");
                return new Response<>(false, "Unauthorized: admin access required");
            }

            Map<Integer, List<Integer>> ordersToRefund = new HashMap<>(); // eventId -> orderIds
            Map<String, Long> identifierToMsgId = new HashMap<>(); // recipient email -> PENDING notification id

            // Transaction 1: close company + events and save member notifications as PENDING, atomically.
            Response<Boolean> closeRes = transactionTemplate.execute(status -> {
                try {
                    Company company = companyRepo.findById(companyId);
                    if (!company.isActive()) {
                        logger.warning("closeCompanyByAdmin failed: company already closed with id " + companyId);
                        return new Response<>(false, "Company is already closed");
                    }

                    Set<Integer> recipients = new HashSet<>();
                    recipients.addAll(company.getCompanyPermission().getOwnerIds());
                    recipients.addAll(company.getCompanyPermission().getCompanyTree().keySet());

                    company.deactivate();
                    companyRepo.store(company);

                    List<Event> events = eventRepo.findByCompany(companyId);
                    for (Event event : events) {
                        event.setActive(false);
                        for (Order order : event.getOrders()) {
                            order.markRefundRequired();
                        }
                        eventRepo.store(event);
                        // re-fetch so generated order ids are visible
                        Event stored = eventRepo.findById(event.getId());
                        List<Integer> orderIds = new ArrayList<>();
                        for (Order order : stored.getOrders()) {
                            orderIds.add(order.getOrderId());
                        }
                        ordersToRefund.put(stored.getId(), orderIds);
                    }

                    // Save each member's closure notification as PENDING; delivered live later, outside the txn.
                    NotifyPayload payload = new NotifyPayload("Company " + company.getCompanyName() + " has been closed by admin, all events are cancelled and refunds are being processed", null, companyId);
                    NotifyDTO notifyDTO = new NotifyDTO(GENERAL_POPUP, payload);
                    for (Integer userId : recipients) {
                        try {
                            String identifier = userRepo.getUserEmail(userId);
                            Response<Long> msgIdRes = saveDelayedNotificationAsPending(identifier, notifyDTO);
                            if (msgIdRes != null && msgIdRes.getValue() != null) {
                                identifierToMsgId.put(identifier, msgIdRes.getValue());
                            }
                        } catch (Exception e) {
                            logger.log(Level.SEVERE,
                                    "Failed to persist closure notification for user " + userId + ": " + e.getMessage());
                        }
                    }

                    return new Response<>(true, "Company closed successfully");
                } catch (NoSuchElementException e) {
                    status.setRollbackOnly();
                    logger.warning("closeCompanyByAdmin failed: company not found with id " + companyId);
                    return new Response<>(false, "Company not found");
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("closeCompanyByAdmin failed due to server error: " + e.getMessage());
                    return new Response<>(false, e.getMessage());
                }
            });

            // txn 1 rolled back -> nothing committed, stop
            if (closeRes == null || !closeRes.getValue()) {
                return closeRes == null ? new Response<>(false, "Failed to close company") : closeRes;
            }

            // Refunds (outside the closure txn). A failed refund does not undo the closure.
            for (Map.Entry<Integer, List<Integer>> entry : ordersToRefund.entrySet()) {
                int eventId = entry.getKey();
                for (int orderId : entry.getValue()) {
                    try {
                        processRefundAdmin(token, eventId, orderId);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to process automatic refund for order " +
                                orderId + " in event " + eventId + ": " + e.getMessage());
                    }
                }
            }

            // Live delivery (no txn held open): deliver the PENDING notifications and mark
            // delivered ones DELIVERED. An offline member keeps their PENDING message for later.
            String companyName = companyRepo.findById(companyId).getCompanyName();
            NotifyPayload payload = new NotifyPayload("Company " + companyName + " has been closed by admin, all events are cancelled and refunds are being processed", null, companyId);
            NotifyDTO notifyDTO = new NotifyDTO(GENERAL_POPUP, payload);
            for (Map.Entry<String, Long> entry : identifierToMsgId.entrySet()) {
                String identifier = entry.getKey();
                Long msgId = entry.getValue();
                try {
                    boolean isDelivered = notifier.notifyUser(identifier, notifyDTO);
                    if (isDelivered && msgId != null) {
                        markNotificationAsDelivered(identifier, msgId);
                    } else {
                        logger.info("Member " + identifier + " is offline. Closure notification remains PENDING");
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE,
                            "Failed to notify user " + identifier + " about company closure: " + e.getMessage());
                }
            }

            logger.info("Company with id " + companyId + " has been closed by admin");
            return new Response<>(true, "Company closed successfully");
        });
    }
    // just for admin to process refunds when closing company
    private Response<Boolean> processRefundAdmin(String token, Integer eventId, int orderId) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "processRefundAdmin called");

            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("Invalid token");
                return new Response<>(false, "Invalid token");
            }

            // Phase 1 (transaction): validate that the order can be refunded and take a snapshot
            // of the external ticket codes that must be cancelled outside the DB transaction.
            Response<Order> orderResponse = transactionTemplate.execute(status -> {
                try {
                    Event event = eventRepo.findById(eventId);
                    Order order = event.findOrderById(orderId);

                    if (order == null) {
                        logger.log(Level.SEVERE, "Order not found for refund");
                        return new Response<>(null, "No matching order found for refund");
                    }

                    if (order.getStatus() == OrderStatus.REFUNDED) {
                        logger.log(Level.INFO, "Order is already refunded");
                        return new Response<>(order, "Already refunded");
                    }

                    if (!order.canBeRefunded()) {
                        logger.log(Level.SEVERE, "Order cannot be refunded");
                        return new Response<>(null, "Order cannot be refunded");
                    }

                    return new Response<>(order, "Order conditions are valid, the refund can start");
                } catch (NoSuchElementException e) {
                    status.setRollbackOnly();
                    logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                    return new Response<>(null, "Event not found");
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (TransientDataAccessException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.log(Level.SEVERE, "Failed to process refund: " + e.getMessage());
                    return new Response<>(null, "Failed to process refund: " + e.getMessage());
                }
            });

            if (orderResponse == null || orderResponse.getValue() == null) {
                return new Response<>(false, orderResponse == null ? "Failed to process refund" : orderResponse.getMessage());
            }

            Order orderToRefund = orderResponse.getValue();
            if (orderToRefund.getStatus() == OrderStatus.REFUNDED) {
                return new Response<>(true, "Already refunded");
            }

            // External ticket cancellation and payment refund must not run inside an open DB transaction.
            List<String> cancelledCodes = new ArrayList<>();
            if (orderToRefund.getExternalTicketCodes() != null && !orderToRefund.getExternalTicketCodes().isEmpty()) {
                for (String ticketCode : new ArrayList<>(orderToRefund.getExternalTicketCodes())) {
                    try {
                        boolean cancelled = ticketSupply.cancelTicket(ticketCode);
                        if (cancelled) {
                            cancelledCodes.add(ticketCode);
                        } else {
                            logger.log(Level.WARNING, "Failed to cancel ticket " + ticketCode + " in external system.");
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Exception cancelling ticket " + ticketCode, e);
                    }
                }
            }

            boolean refundApproved = false;
            try {
                refundApproved = paymentSystem.refund(
                        orderToRefund.getPaymentConfirmationId(),
                        orderToRefund.getTotalSum());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to process refund through payment system: " + e.getMessage());
            }

            final boolean finalRefundApproved = refundApproved;
            final List<String> finalCancelledCodes = new ArrayList<>(cancelledCodes);

// Phase 2 (transaction): persist cancellation/refund outcome and notify the purchaser.
            return transactionTemplate.execute(status -> {
                try {
                    Event event = eventRepo.findById(eventId);
                    Order order = event.findOrderById(orderId);

                    if (order == null) {
                        logger.log(Level.SEVERE, "Order not found while finalizing refund");
                        return new Response<>(false, "No matching order found for refund");
                    }

                    List<String> remainingCodes = new ArrayList<>(order.getExternalTicketCodes());

                    if (!finalCancelledCodes.isEmpty()) {
                        remainingCodes.removeAll(finalCancelledCodes);
                    }

                    order.setExternalTicketCodes(remainingCodes);

                    String userIdentifier = order.getUserIdentifier();

                    if (finalRefundApproved) {
                        order.markRefunded();
                        eventRepo.store(event);

                        logger.log(Level.INFO, "Refund completed successfully");

                        NotifyPayload payload = new NotifyPayload(
                                "Refund processed for order " + order.getOrderId()
                                        + " in event " + event.getId()
                                        + " because of closing the company",
                                event.getId(),
                                null
                        );

                        sendOrSaveNotification(userIdentifier, new NotifyDTO(GENERAL_POPUP, payload));

                        return new Response<>(true, "Refund completed successfully");
                    }

                    order.markRefundRequired();
                    eventRepo.store(event);

                    NotifyPayload payload = new NotifyPayload(
                            "Refund failed for order " + order.getOrderId()
                                    + " in event " + event.getId()
                                    + " because of closing the company, please contact support",
                            event.getId(),
                            null
                    );

                    sendOrSaveNotification(userIdentifier, new NotifyDTO(GENERAL_POPUP, payload));

                    logger.log(Level.SEVERE, "Refund rejected by external payment service");
                    return new Response<>(false, "Refund rejected by external payment service");

                } catch (NoSuchElementException e) {
                    status.setRollbackOnly();
                    logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                    return new Response<>(false, "Event not found");
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (TransientDataAccessException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.log(Level.SEVERE, "Failed to process refund: " + e.getMessage());
                    return new Response<>(false, "Failed to process refund: " + e.getMessage());
                }
            });
        });
    }
    public Response<List<AdminPurchaseHistoryDTO>> getGlobalOrders(String token, List<String> usersFilter, List<Integer> eventsFilter, List<Integer> companiesFilter) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("getGlobalOrders attempt for admin");
            return transactionTemplate.execute(status -> {
                try {
                    if (!isVerifiedAdmin(token)) {
                        logger.warning("getGlobalOrders failed: unauthorized");
                        return new Response<>(null, "Unauthorized: admin access required");
                    }
                    boolean hasUsers = (usersFilter != null && !usersFilter.isEmpty());
                    boolean hasCompanies = (companiesFilter != null && !companiesFilter.isEmpty());
                    boolean hasEvents = (eventsFilter != null && !eventsFilter.isEmpty());

                    if(hasUsers && (hasCompanies || hasEvents)) {
                        logger.warning("getGlobalOrders failed: cannot filter both users and companies");
                        return new Response<>(null, "Cannot filter both users and companies");
                    }
                    List<Event> allEvents = eventRepo.getAll();
                    List<AdminPurchaseHistoryDTO> historyOrders = new ArrayList<>();
                    if(hasUsers){
                        for (Event event : allEvents) {
                            String companyName = "";
                            try {
                                Company company = companyRepo.findById(event.getCompanyId());
                                companyName = company.getCompanyName();
                            } catch (Exception ignored) {}

                            List<Order> orders = event.getOrders();
                            for (Order order : orders) {
                                if(usersFilter.contains(order.getUserIdentifier()))
                                    historyOrders.add(new AdminPurchaseHistoryDTO(
                                            order.getOrderId(),
                                            order.getUserIdentifier(),
                                            event.getCompanyId(),
                                            companyName,
                                            event.getId(),
                                            event.getName(),
                                            String.valueOf(event.getDate()),
                                            event.getLocation().toString(),
                                            order.getStatus(),
                                            order.getTickets(),
                                            order.getTotalSum()
                                    ));
                            }
                        }
                    }
                    else { // in case we have filter on companies or events
                        for (Event event : allEvents) {
                            boolean toAdd = !hasUsers && !hasCompanies && !hasEvents;
                            if (hasCompanies && !hasEvents) {
                                if (companiesFilter.contains(event.getCompanyId())){
                                    toAdd = true;
                                }
                            } else if (!hasCompanies && hasEvents) {
                                if (eventsFilter.contains(event.getId())){
                                    toAdd = true;
                                }
                            }
                            else if (hasCompanies && hasEvents) {
                                if (companiesFilter.contains(event.getCompanyId()) && eventsFilter.contains(event.getId())){
                                    toAdd = true;
                                }
                            }
                            if (toAdd) {
                                String companyName = "";
                                try {
                                    Company company = companyRepo.findById(event.getCompanyId());
                                    companyName = company.getCompanyName();
                                } catch (Exception ignored) {}
                                List<Order> orders = event.getOrders();
                                for (Order order : orders) {
                                    historyOrders.add(new AdminPurchaseHistoryDTO(
                                            order.getOrderId(),
                                            order.getUserIdentifier(),
                                            event.getCompanyId(),
                                            companyName,
                                            event.getId(),
                                            event.getName(),
                                            String.valueOf(event.getDate()),
                                            event.getLocation().toString(),
                                            order.getStatus(),
                                            order.getTickets(),
                                            order.getTotalSum()
                                    ));                            }
                            }
                        }
                    }
                    if(historyOrders.isEmpty()){
                        logger.warning("getGlobalOrders failed: no history orders found");
                        return new Response<>(new ArrayList<>(), "No history orders found");
                    }
                    logger.info("Retrieved history orders successfully for filter");
                    return new Response<>(historyOrders,"Retrieved history orders successfully for filter");
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.log(Level.SEVERE, "Failed to retrieve history orders: " + e.getMessage());
                    return new Response<>(null, "Failed to retrieve history orders: " + e.getMessage());
                }
            });
        });
    }


    public Response<List<String>> getAllPurchasers(String token) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("getAllPurchasers attempt for admin");
            if (!isVerifiedAdmin(token)) {
                logger.warning("getAllPurchasers failed: unauthorized");
                return new Response<>(null, "Unauthorized: Only admin can access");
            }
            return transactionTemplate.execute(status -> {
                try{
                    List<String> purchasers = eventRepo.getAllPurchasers();
                    if (purchasers.isEmpty()) {
                        logger.info("No purchasers found in the system");
                        return new Response<>(new ArrayList<>(), "No purchasers found");
                    }
                    logger.info("Retrieved all unique purchasers successfully. Total: " + purchasers.size());
                    return new Response<>(purchasers, "Retrieved purchasers successfully");
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Failed to retrieve purchasers: " + e.getMessage());
                    return new Response<>(null, "Failed to retrieve purchasers: " + e.getMessage());
                }
            });
        });
    }
    private int getUserIdFromToken(String token) {
        String email = auth.getUserEmail(token).getValue();
        if (email != null) {
            Member m = userRepo.findUserByEmail(email);
            if (m != null) return m.getUserId();
        }
        return -1; //for guest or invalid token
    }
    // Helper method to send a real-time notification or save it as pending if the
    // user is offline.
    private Response<Void> sendOrSaveNotification(String userIdentifier, NotifyDTO notifyDTO) {
        Member member = userRepo.findUserByEmail(userIdentifier);
        boolean isGuest = (member == null);
        if (isGuest) {
            boolean isDelivered = notifier.notifyUser(userIdentifier, notifyDTO);
            if (isDelivered) {
                return new Response<>(null, "Notification sent successfully to guest");
            } else {
                logger.warning("Guest is offline. Notification dropped for: " + userIdentifier);
                return new Response<>(null, "Guest offline, notification dropped");
            }
        }
        Long savedNotificationId = null;
        boolean dbSaveFailed = false;
        try{
            Response<Long> savedNotificationIdRes = saveDelayedNotificationAsPending(userIdentifier, notifyDTO);
            savedNotificationId = (savedNotificationIdRes != null) ? savedNotificationIdRes.getValue() : null;
            if(savedNotificationId != null && savedNotificationId == -1L){
                dbSaveFailed = true;
            }
        } catch (Exception e){
            logger.severe("Database connection/commit failed outside lambda: " + e.getMessage());
            dbSaveFailed = true;
        }
        boolean isDelivered = notifier.notifyUser(userIdentifier, notifyDTO);
        if (dbSaveFailed && !isDelivered) {
            logger.severe("CRITICAL: DB transaction failed. Notification lost for: " + userIdentifier);
            return new Response<>(null, "Failed to handle notification due to DB error");
        }
        if (isDelivered) {
            markNotificationAsDelivered(userIdentifier, savedNotificationId); //if we succeed sending in real time we need to mark as delivered
            return new Response<>(null, "Notification sent successfully as DELIVERED");
        }
        logger.info("Member is offline. Notification remains PENDING for: " + userIdentifier);
        return new Response<>(null, "Notification saved as PENDING");
    }
    //for saving the notifications as pending in order to handle Persistence before trying to send in real-time
    private Response<Long> saveDelayedNotificationAsPending(String userIdentifier, NotifyDTO notifyDTO) {
        return RetryHelper.executeWithRetry(() ->
                transactionTemplate.execute(status -> {
                    try {
                        Member member = userRepo.findUserByEmail(userIdentifier);

                        if (member == null) {
                            logger.warning("User not found for identifier: " + userIdentifier);
                            return new Response<>(null, "User not found");
                        }
                        UserNotification userNotification = new UserNotification(notifyDTO.getType(),notifyDTO.getPayload());
                        member.addPendingNotification(userNotification);
                        userRepo.store(member);
                        member=userRepo.findUserByEmail(userIdentifier);
                        Long msgId=member.getMessageId(userNotification);

                        logger.info("Pending notification saved successfully for: " + member.getIdentifier());
                        return new Response<>(msgId, "Notification saved as pending");

                    } catch (OptimisticLockingFailureException e) {
                        status.setRollbackOnly();
                        throw e;
                    }catch (TransientDataAccessException e) {
                        status.setRollbackOnly();
                        logger.warning("Transient DB error detected, retrying... " + e.getMessage());
                        throw e;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        logger.severe("Fatal error during notification save: " + e.getMessage());
                        return new Response<>(-1L, "Fatal error");
                    }
                })
        );
    }

    //marking notification as delivered because we succeed in real-time
    private Response<Boolean> markNotificationAsDelivered(String userIdentifier, Long notificationId) {
        return RetryHelper.executeWithRetry(() ->
                transactionTemplate.execute(status -> {
                    try {
                        Member member = userRepo.findUserByEmail(userIdentifier);
                        if (member != null) {
                            member.setMessageStatus(notificationId,NotificationStatus.DELIVERED);
                            userRepo.store(member);
                        }
                        return new Response<>(true, "Notification marked as DELIVERED");

                    } catch (OptimisticLockingFailureException e) {
                        status.setRollbackOnly();
                        throw e;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        logger.warning("Failed to mark notification as delivered: " + e.getMessage());
                        return new Response<>(false, "Failed to mark notification as delivered");
                    }
                })
        );
    }


    private void notifyTokenExpired(String token) {
        try {
            NotifyPayload payload = new NotifyPayload("Your session has expired");
            NotifyDTO expiredNotify = new NotifyDTO(NotifyType.TOKEN_EXPIRED, payload);
            notifier.notifyTab(token, expiredNotify);
            logger.info("Sent TOKEN_EXPIRED notification to tab: " + token);
        } catch (Exception e) {
            logger.warning("Failed to send TOKEN_EXPIRED notification: " + e.getMessage());
        }
    }

    private boolean isVerifiedAdmin(String token) {
        Response<String> roleRes = auth.getRole(token);
        if (roleRes.getValue() == null) {
            notifyTokenExpired(token);
            return false;
        }
        return auth.isAdmin(token).getValue();
    }
}
