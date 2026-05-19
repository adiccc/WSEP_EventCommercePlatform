package application;

import DTO.NotifyDTO;
import DTO.NotifyPayload;
import domain.Suspension.ISuspensionRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.company.Permissions;
import domain.dto.OrderDTO;
import domain.dto.SuspensionDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.event.Order;
import domain.Suspension.Suspension;
import domain.webQueue.WebQueue;
import Exception.OptimisticLockingFailureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static DTO.NotifyType.GENERAL_POPUP;

@Service
public class AdminService {
    private IEventRepo eventRepo;
    private IPaymentSystem paymentSystem;
    private ICompanyRepo companyRepo;
    private ISuspensionRepo suspensionRepo;
    private static final Logger logger = Logger.getLogger(AdminService.class.getName());

    private final IAuth auth;
    private final IUserRepo userRepo;
    private final ScheduledExecutorService scheduler;
    private final INotifier notifier;



    @Autowired
    public AdminService(IAuth auth, IUserRepo userRepo, ICompanyRepo companyRepo, IEventRepo eventRepo, IPaymentSystem paymentSystem, ISuspensionRepo suspensionRepo, INotifier notifier) {
        this.auth = auth;
        this.userRepo = userRepo;
        this.eventRepo = eventRepo;
        this.paymentSystem = paymentSystem;
        this.companyRepo = companyRepo;
        this.suspensionRepo = suspensionRepo;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.notifier = notifier;
    }

    //permanent suspension
    public Response<Boolean> SuspendUser(String token, int userId) {
        return RetryHelper.executeWithRetry(()->{
            logger.log(Level.INFO, "SuspendUser called");
            try{
                if(!auth.isAdmin(token).getValue()){
                    logger.log(Level.INFO, "SuspendUser failed : user is not admin");
                    return new Response<>(false, "SuspendUser failed : user is not admin");
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
                throw e;
            }catch(NoSuchElementException e){
                logger.log(Level.INFO, "User not found");
                return new Response<>(false, "User not found");
            }catch(Exception e){
                logger.log(Level.SEVERE, "OptimisticLockingFailureException", e);
                return new Response<>(false,"SuspendUser faild due to serer error: "+e.getMessage());
            }
        });
    }

    //temp suspension , duration in days
    public Response<Boolean> SuspendUser(String token, int userId, int duration) {
        return RetryHelper.executeWithRetry(()->{
            logger.log(Level.INFO, "SuspendUser called");
            try{
                if(!auth.isAdmin(token).getValue()){
                    logger.log(Level.INFO, "SuspendUser failed : user is not admin");
                    return new Response<>(false, "SuspendUser failed : user is not admin");
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
                throw e;
            }catch(NoSuchElementException e){
                logger.log(Level.INFO, "User not found");
                return new Response<>(false, "User not found");
            }catch(Exception e){
                logger.log(Level.SEVERE, "OptimisticLockingFailureException", e);
                return new Response<>(false,"SuspendUser faild due to serer error: "+e.getMessage());
            }
        });
    }

    public Response<Boolean> UnsuspendUser(String token, int userId) {
        return RetryHelper.executeWithRetry(()->{
            logger.log(Level.INFO, "UnsuspendUser called");
            try{
                if(!auth.isAdmin(token).getValue()){
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
                throw e;
            }catch(NoSuchElementException e){
                logger.log(Level.INFO, "User not found");
                return new Response<>(false, "User not found");
            }catch(Exception e){
                logger.log(Level.SEVERE, "OptimisticLockingFailureException", e);
                return new Response<>(false,"UnsuspendUser faild due to serer error: "+e.getMessage());
            }
        });
    }

    public Response<List<SuspensionDTO>> getAllUsersSuspensions(String token) {
        return RetryHelper.executeWithRetry(()-> {
            logger.log(Level.INFO, "getAllUsersSuspensions called");
            if (!auth.isAdmin(token).getValue()) {
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
                try {
                    Member member = userRepo.findById(userId);
                    member.unsuspend();
                    userRepo.store(member);
                    logger.log(Level.INFO, "activate after suspension succeeded, user "+userId+" not suspended");
                    return new Response<>(true, "Suspension succeeded, user "+userId+" suspended");
                }catch (OptimisticLockingFailureException e) {
                    throw e;
                }catch (NoSuchElementException e){
                    logger.log(Level.INFO, "User not found");
                    return new Response<>(false,"User not found");
                }catch (Exception e){
                    logger.log(Level.SEVERE, "SuspendUser faild due to serer error: ", e.getMessage());
                    return new Response<>(false,"SuspendUser faild due to serer error: "+e.getMessage());
                }
            })
        , duration, TimeUnit.DAYS);
    }

    public Response<Boolean> setMaxCapacity(String token, int capacity) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("setMaxCapacity attempt");
            try {
                if (!auth.isAdmin(token).getValue()) {
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
                if (!auth.isAdmin(token).getValue()) {
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
                if (!auth.isAdmin(token).getValue()) {
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
     *
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
            try {
                if (!auth.isAdmin(adminToken).getValue()) {
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

                // Step 1: cascade company permissions
                for (Company company : companyRepo.getAll()) {
                    Permissions perms = company.getCompanyPermission();
                    boolean changed = false;

                    if (perms.getFounderId() == userIdToRemove) {
                        logger.warning("removeUser blocked: userId " + userIdToRemove + " is the founder of company "
                                + company.getCompanyId());
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

                logger.info("removeUser succeeded for userId: " + userIdToRemove);
                return Response.ok(true);

            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("removeUser failed for userId: " + userIdToRemove + ". Error: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Integer> getWaitingCount(String token) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("getWaitingCount attempt");
            try {
                if (!auth.isAdmin(token).getValue()) {
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
            try {
                if (!auth.isAdmin(token).getValue()) {
                    logger.warning("closeCompanyByAdmin failed: unauthorized");
                    return new Response<>(false, "Unauthorized: admin access required");
                }
                Company company = companyRepo.findById(companyId);
                if (!company.isActive()) {
                    logger.warning("closeCompanyByAdmin failed: company already closed with id " + companyId);
                    return Response.error("Company is already closed");
                }

                Set<Integer> recipients = new HashSet<>(company.getCompanyPermission().getOwnerIds());
                recipients.addAll(company.getCompanyPermission().getCompanyTree().keySet());

                company.deactivate();
                companyRepo.store(company);
                List<Event> events = eventRepo.findByCompany(companyId);
                for (Event event : events) {
                    event.setActive(false);
                    List<Order> orders = event.getOrders();
                    for (Order order : orders) {
                        order.markRefundRequired();
                    }
                    eventRepo.store(event);
                    event = eventRepo.findById(event.getId());
                    orders = event.getOrders();
                    for (Order order : orders) {
                        try {
                            processRefundAdmin(token, event.getId(), order.getOrderId());
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Failed to process automatic refund for order " +
                                    order.getOrderId() + " in event " + event.getId() + ": " + e.getMessage());
                        }
                    }
                }

                //notify all company members about the closure
                NotifyPayload payload= new NotifyPayload("Company " + company.getCompanyName() + " has been closed by admin, all events are cancelled and refunds are being processed", null,companyId);
                for (Integer userId : recipients) {
                    try {
                        notifier.notifyMemberById(userId, new NotifyDTO(GENERAL_POPUP,payload));
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to notify user " + userId + " about company closure: " + e.getMessage());
                    }
                }
                logger.info("Company with id " + companyId + " has been closed by admin");
                return new Response<>(true, "Company closed successfully");
            } catch (NoSuchElementException e) {
                logger.warning("closeCompanyByAdmin failed: company not found with id " + companyId);
                return new Response<>(false, "Company not found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("closeCompanyByAdmin failed due to server error: " + e.getMessage());
                return new Response<>(false, e.getMessage());
            }
        });
    }
    // just for admin to process refunds when closing company
    private Response<Boolean> processRefundAdmin(String token, Integer eventId, int orderId) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "processRefund called");

            int userId = auth.getUserId(token).getValue();
            if (userId == -1) {
                logger.severe("Invalid token");
                return new Response<>(false, "Invalid token");
            }
            try{
                Event event = eventRepo.findById(eventId);
                Order order = event.findOrderById(orderId);

                if (order == null) {
                    logger.log(Level.SEVERE, "Order not found for refund");
                    return new Response<>(false, "No matching order found for refund");
                }

                if (!order.canBeRefunded()) {
                    logger.log(Level.SEVERE, "Order cannot be refunded");
                    return new Response<>(false, "Order cannot be refunded");
                }

                boolean refundApproved = paymentSystem.refund(
                        order.getPaymentConfirmationId(),
                        order.getTotalSum());

                if (refundApproved) {
                    order.markRefunded();
                    eventRepo.store(event);
                    logger.log(Level.INFO, "Refund completed successfully");
                    // Notify the user about the refund and the company closure
                    String userIdentifier = order.getUserIdentifier();
                    NotifyPayload payload= new NotifyPayload("Refund processed for order " + order.getOrderId() + " in event " + event.getId() + "because of closing the company", event.getId(),null);
                    notifier.notifyUser(userIdentifier, new NotifyDTO(GENERAL_POPUP,payload));

                    return new Response<>(true, "Refund completed successfully");
                }
                order.markRefundRequired();
                eventRepo.store(event);

                // Notify the user about the refund failure and the company closure
                String userIdentifier = order.getUserIdentifier();
                NotifyPayload payload= new NotifyPayload("Refund failed for order " + order.getOrderId() + " in event " + event.getId() + " because of closing the company, please contact support", event.getId(),null);
                notifier.notifyUser(userIdentifier, new NotifyDTO(GENERAL_POPUP,payload));

                logger.log(Level.SEVERE, "Refund rejected by external payment service");
                return new Response<>(false, "Refund rejected by external payment service");

            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                return new Response<>(false, "Event not found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to process refund: " + e.getMessage());
                return new Response<>(false, "Failed to process refund: " + e.getMessage());
            }
        });
    }
    public Response<List<OrderDTO>> getGlobalOrders(String token,List<String> usersFilter, List<Integer> eventsFilter,List<Integer> companiesFilter) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("getGlobalOrders attempt for admin");
            try {
                if (!auth.isAdmin(token).getValue()) {
                    logger.warning("closeCompanyByAdmin failed: unauthorized");
                    return new Response<>(null, "Unauthorized: admin access required");
                }
                boolean hasUsers = (usersFilter != null && !usersFilter.isEmpty());
                boolean hasCompanies = (companiesFilter != null && !companiesFilter.isEmpty());
                boolean hasEvents = (eventsFilter != null && !eventsFilter.isEmpty());
                if(!hasUsers && !hasCompanies && !hasEvents) {
                    logger.warning("getGlobalOrders failed: no users or companies found");
                    return new Response<>(null, "No users or companies to filter");
                }
                if(hasUsers && (hasCompanies || hasEvents)) {
                    logger.warning("getGlobalOrders failed: cannot filter both users and companies");
                    return new Response<>(null, "Cannot filter both users and companies");
                }
                List<Event> allEvents = eventRepo.getAll();
                List<OrderDTO> historyOrders = new ArrayList<>();
                if(hasUsers){
                   for (Event event : allEvents) {
                       List<Order> orders = event.getOrders();
                       for (Order order : orders) {
                           if(usersFilter.contains(order.getUserIdentifier()))
                                historyOrders.add(new OrderDTO(order));
                       }
                   }
                }
                else { // in case we have filter on companies or events
                    for (Event event : allEvents) {
                        boolean toAdd = false;
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
                            List<Order> orders = event.getOrders();
                            for (Order order : orders) {
                                historyOrders.add(new OrderDTO(order));
                            }
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
                throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to retrieve history orders: " + e.getMessage());
            return new Response<>(null, "Failed to retrieve history orders: " + e.getMessage());
        }
        });
    }
    public Response<List<String>> getAllPurchasers(String token) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("getAllPurchasers attempt for admin");
                if (!auth.isAdmin(token).getValue()) {
                    logger.warning("getAllPurchasers failed: unauthorized");
                    return new Response<>(null, "Unauthorized: Only admin can access");
                }
                try{
                List<String> purchasers = eventRepo.getAllPurchasers();
                if (purchasers.isEmpty()) {
                    logger.info("No purchasers found in the system");
                    return new Response<>(new ArrayList<>(), "No purchasers found");
                }
                logger.info("Retrieved all unique purchasers successfully. Total: " + purchasers.size());
                return new Response<>(purchasers, "Retrieved purchasers successfully");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Failed to retrieve purchasers: " + e.getMessage());
                return new Response<>(null, "Failed to retrieve purchasers: " + e.getMessage());
            }
        });
    }
}
