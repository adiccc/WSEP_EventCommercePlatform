package application;

import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.company.Permissions;
import domain.dto.HierarchyDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.event.Order;
import domain.webQueue.WebQueue;
import Exception.OptimisticLockingFailureException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminService {
    private IEventRepo eventRepo;
    private IPaymentSystem paymentSystem;
    private ICompanyRepo companyRepo;
    private static final Logger logger = Logger.getLogger(AdminService.class.getName());

    private final IAuth auth;
    private final IUserRepo userRepo;


    public AdminService(IAuth auth, IUserRepo userRepo, ICompanyRepo companyRepo, IEventRepo eventRepo, IPaymentSystem paymentSystem) {
        this.auth = auth;
        this.userRepo = userRepo;
        this.eventRepo = eventRepo;
        this.paymentSystem = paymentSystem;
        this.companyRepo = companyRepo;
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
                        for (HierarchyDTO dto : perms.getCompanyTree().values()) {
                            if (dto.getMyManager() == userIdToRemove)
                                dto.setMyManager(perms.getFounderId());
                        }
                        changed = true;

                    } else if (perms.getCompanyTree().containsKey(userIdToRemove)) {
                        // Manager removed → clean up appointer's list + reassign sub-managers to
                        // founder
                        HierarchyDTO removed = perms.getCompanyTree().remove(userIdToRemove);
                        HierarchyDTO appointer = perms.getCompanyTree().get(removed.getMyManager());
                        if (appointer != null)
                            appointer.getMyAppointees().remove(Integer.valueOf(userIdToRemove));
                        for (int subId : removed.getMyAppointees()) {
                            HierarchyDTO sub = perms.getCompanyTree().get(subId);
                            if (sub != null)
                                sub.setMyManager(perms.getFounderId());
                        }
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

    public Response<Boolean> processRefundAdmin(String token, Integer eventId, int orderId) {
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
                    return new Response<>(true, "Refund completed successfully");
                }

                order.markRefundRequired();
                eventRepo.store(event);

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
}
