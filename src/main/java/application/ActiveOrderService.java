package application;

import DTO.*;

import domain.Suspension.ISuspensionRepo;
import domain.activeOrder.ActiveOrder;
import domain.activeOrder.IActiveOrderRepo;
import domain.activeOrder.STAGE;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dto.*;
import domain.dto.ActiveOrderSelectionDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.event.Order;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;
import Exception.OptimisticLockingFailureException;
import domain.user.NotificationStatus;
import domain.user.UserNotification;
import domain.user.IUserRepo;
import domain.user.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;

import java.util.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service

public class ActiveOrderService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());
    // NOTE: order ids are now assigned by the persistence layer (DB IDENTITY column,
    // or the in-memory repo) — the old in-memory AtomicInteger was removed because it
    // reset to 1 on restart and could collide with already-persisted ids.
    private final IEventRepo eventRepo;
    private final IActiveOrderRepo activeOrderRepo;
    private final ICompanyRepo companyRepo;
    private final ILotteryRepo lotteryRepo;
    private final IUserRepo userRepo;
    private final IAuth auth;
    private final ISuspensionRepo suspensionRepo;
    private final IPaymentSystem paymentSystem;
    private final ITicketSupply ticketSupply;
    private final INotifier notifier;
    private final PreExpirationNotificationScheduler preExpirationScheduler;
    private final int capacity;
    private final ScheduledExecutorService cleanupScheduler;
    private final TransactionTemplate transactionTemplate;
    @Autowired
    public ActiveOrderService(
            IAuth auth,
            IActiveOrderRepo activeOrderRepo,
            IEventRepo eventRepo,
            ICompanyRepo companyRepo,
            ILotteryRepo lotteryRepo,
            IPaymentSystem paymentSystem,
            ITicketSupply ticketSupply,
            ISuspensionRepo suspensionRepo,
            INotifier notifier,
            PreExpirationNotificationScheduler preExpirationScheduler,
            IUserRepo userRepo,
            TransactionTemplate transactionTemplate,
            @Value("${active-order.capacity:20}") int capacity) {
        this.eventRepo = eventRepo;
        this.activeOrderRepo = activeOrderRepo;
        this.companyRepo = companyRepo;
        this.lotteryRepo = lotteryRepo;
        this.auth = auth;
        this.paymentSystem = paymentSystem;
        this.ticketSupply = ticketSupply;
        this.suspensionRepo=suspensionRepo;
        this.notifier = notifier;
        this.preExpirationScheduler = preExpirationScheduler;
        this.capacity = capacity;
        this.userRepo = userRepo;
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "active-order-cleanup");
            t.setDaemon(true);
            return t;
        });

        cleanupScheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        cleanupExpiredOrders();
                    } catch (Throwable ex) {
                        logger.log(Level.SEVERE, "Cleanup failed: " + ex.getMessage());
                    }
                },
                30, 30, TimeUnit.SECONDS);
        this.transactionTemplate = transactionTemplate;
    }

    public int getCapacity() {
        return this.capacity;
    }

    // Crash recovery: an order left in PAYMENT_IN_PROGRESS means the server died during
    // phase 2 (external payment/issuance). Such orders never expire on their own, so on
    // startup we return them to checkout and flag them for manual payment reconciliation.
    @PostConstruct
    public void recoverDanglingPaymentsOnStartup() {
        logger.info("recoverDanglingPaymentsOnStartup called");

        Response<List<Integer>> stuck = RetryHelper.executeWithRetry(() ->
                transactionTemplate.execute(status -> {
            try {
                List<Integer> ids = activeOrderRepo.getAll().stream()
                        .filter(o -> o.getStage() == STAGE.PAYMENT_IN_PROGRESS)
                        .map(ActiveOrder::getId)
                        .collect(Collectors.toList());
                return new Response<>(ids, "Dangling payment orders loaded");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.severe("Failed to scan for dangling payment orders: " + e.getMessage());
                return new Response<>(null, "Failed to scan dangling payment orders");
            }
        }));

        if (stuck == null || stuck.getValue() == null) {
            logger.severe("Could not scan for dangling payment orders on startup");
            return;
        }

        for (Integer orderId : stuck.getValue()) {
            RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
                try {
                    ActiveOrder order = activeOrderRepo.findById(orderId);
                    if (order.getStage() != STAGE.PAYMENT_IN_PROGRESS) {
                        return new Response<>(true, "Order already resolved");
                    }
                    // Payment status at crash time is unknown — flag for manual reconciliation,
                    // then return to checkout so the normal expiry/cleanup lifecycle takes over.
                    logger.severe("Recovered dangling PAYMENT_IN_PROGRESS order " + orderId
                            + " after restart; verify external payment for possible manual refund.");
                    order.returnToCheckout();
                    activeOrderRepo.store(order);
                    return new Response<>(true, "Order returned to checkout");
                } catch (NoSuchElementException e) {
                    return new Response<>(true, "Order no longer exists");
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (TransientDataAccessException e) {
                    status.setRollbackOnly();
                    throw e;
                }
            }));
        }

        logger.info("Dangling payment recovery complete: "
                + stuck.getValue().size() + " order(s) processed");
    }

    public Response<EnterPurchaseDTO> enterEventPurchase(String token, int companyId, int eventId, String code) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "enterEventPurchase called");
            cleanupExpiredOrders();

            String role = getValidatedRole(token);

            if (role == null) {
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(null, "Invalid token");
            }
            int userId = getUserIdFromToken(token);
            if (suspensionRepo.haveActiveSuspension(userId)) {
                logger.severe("User does not have write access caused by suspension");
                return new Response<>(null, "user does not have write access caused by suspension.");
            }

            try {
                Event e = this.eventRepo.findById(eventId);

                if (e.getCompanyId() != companyId) {
                    logger.log(Level.SEVERE, "The selected event does not belong to the company");
                    return new Response<>(null, "The selected event does not belong to the company");
                }

                if (!e.isActive()) {
                    logger.log(Level.SEVERE, "The selected event is not active");
                    return new Response<>(null, "The selected event is not active");
                }

                if (e.getSaleStartDate().isAfter(LocalDateTime.now())) {
                    logger.log(Level.SEVERE, "The sale for this event has not started yet");
                    return new Response<>(null, "The sale for this event has not started yet");
                }
                Company company = companyRepo.findById(companyId);

                String companyPurchasePolicy =
                        company.getPurchasePolicy().describe();

                String eventPurchasePolicy =
                        e.getPurchasePolicy().describe();

                String userIdentifier = auth.getUserIdentifier(token).getValue();

                try {
                    ActiveOrderDTO existingOrderDTO =
                            activeOrderRepo.findOrderByUserId(userIdentifier);

                    ActiveOrder existingOrder =
                            activeOrderRepo.findById(existingOrderDTO.getId());

                    if (!existingOrder.isExpired(LocalDateTime.now())) {

                        if (!existingOrder.getEventId().equals(eventId)) {
                            return new Response<>(
                                    null,
                                    "You already have an active order. Please complete or cancel it before starting a new purchase."
                            );
                        }

                        logger.log(Level.INFO,
                                "Existing active order found for this event. Order ID: "
                                        + existingOrder.getId());

                        return new Response<>(
                                new EnterPurchaseDTO(
                                        new EventMapDTO(e.getMap()),
                                        new ActiveOrderDTO(existingOrder),
                                        true,
                                        false,
                                        null,
                                        companyPurchasePolicy,
                                        eventPurchasePolicy
                                ),
                                "Existing active order found"
                        );
                    }

                } catch (NoSuchElementException ignored) {
                    // No active order for this user, continue normally
                }

                if (e.hasLottery()) {
                    Lottery l = lotteryRepo.findById(eventId);

                    LocalDateTime lotteryEndTime = e.getSaleStartDate().plusHours(l.getExpirationTime());

                    boolean lotteryOnlyPeriod = LocalDateTime.now().isBefore(lotteryEndTime);

                    if (lotteryOnlyPeriod) {
                        if (code == null || code.isBlank()) {
                            return new Response<>(
                                    null,
                                    "Lottery code is required for this event");
                        }

                        if (!l.codeMatchesUser(userId, code)) {
                            return new Response<>(
                                    null,
                                    "Invalid lottery code");
                        }
                    }
                }

                if (capacity <= activeOrderRepo.countActiveOrdersForEvent(eventId)) {
                    if (e.getEventQueue().contains(token)) {
                        int position = e.getEventQueue().position(token);

                        return new Response<>(
                                new EnterPurchaseDTO(
                                        null,
                                        null,
                                        false,
                                        true,
                                        position,
                                        companyPurchasePolicy,
                                        eventPurchasePolicy),
                                "User is still waiting in queue. Position: " + position);
                    }

                    e.getEventQueue().enqueue(token);
                    eventRepo.store(e);

                    int position = e.getEventQueue().position(token);

                    return new Response<>(
                            new EnterPurchaseDTO(
                                    null,
                                    null,
                                    false,
                                    true,
                                    position,
                                    companyPurchasePolicy,
                                    eventPurchasePolicy),
                            "Event is full, user added to waiting queue. Position: " + position);
                }

                ActiveOrder newActiveOrder = new ActiveOrder(userIdentifier, eventId, new ArrayList<>());

                eventRepo.store(e);
                // id is assigned by the persistence layer on store; read it afterwards.
                activeOrderRepo.store(newActiveOrder);

                logger.log(Level.INFO,
                        "Created active order with ID: " + newActiveOrder.getId()
                                + " for user identifier: " + newActiveOrder.getUserIdentifier()
                                + " and event ID: " + newActiveOrder.getEventId());

                logger.log(Level.INFO, "Event map retrieved successfully");

                return new Response<>(
                        new EnterPurchaseDTO(
                                new EventMapDTO(e.getMap()),
                                new ActiveOrderDTO(newActiveOrder),
                                false, false, null, companyPurchasePolicy, eventPurchasePolicy),
                        "Event map retrieved successfully");

            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                return new Response<>(null, "Event not found");
            } catch (IllegalStateException e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, e.getMessage());
                return new Response<>(null, e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during enterEventPurchase: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Failed to enter event purchase : " + e.getMessage());
                return new Response<>(
                        null,
                        "Failed to enter event purchase  : " + e.getMessage());
            }
        }));
    }

    public Response<Boolean> isRequiredLotteryCode(String token, int companyId, int eventId) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "isRequiredLotteryCode called");

            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid token");
                    return new Response<>(null, "Invalid token");
                }

                int userId = getUserIdFromToken(token);
                if (userId!=-1 && suspensionRepo.haveActiveSuspension(userId)) {
                    logger.severe("User does not have write access caused by suspension");
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }

                Event event = eventRepo.findById(eventId);

                if (event.getCompanyId() != companyId) {
                    logger.log(Level.SEVERE, "The selected event does not belong to the company");
                    return new Response<>(
                            null,
                            "The selected event does not belong to the company");
                }

                if (!event.isActive()) {
                    logger.log(Level.SEVERE, "The selected event is not active");
                    return new Response<>(
                            null,
                            "The selected event is not active");
                }

                LocalDateTime now = LocalDateTime.now();

                if (event.getSaleStartDate().isAfter(now)) {
                    logger.log(Level.INFO, "The sale for this event has not started yet");
                    return new Response<>(
                            null,
                            "The sale for this event has not started yet");
                }

                if (!event.hasLottery()) {
                    logger.log(Level.INFO, "Event does not have lottery");
                    return new Response<>(
                            false,
                            "This event does not require a lottery code");
                }

                Lottery lottery = lotteryRepo.findById(eventId);

                LocalDateTime lotteryEndTime = event.getSaleStartDate()
                        .plusHours(lottery.getExpirationTime());

                if (!now.isBefore(lotteryEndTime)) {
                    logger.log(Level.INFO, "Lottery exclusive purchase period has ended");
                    return new Response<>(
                            false,
                            "Lottery period has ended. Everyone can purchase tickets");
                }

                logger.log(Level.INFO, "Lottery code is required for this event");
                return new Response<>(
                        true,
                        "Lottery code is required to purchase tickets for this event");

            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Event or lottery not found: " + e.getMessage());
                return new Response<>(
                        null,
                        "Event or lottery not found");

            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during isRequiredLotteryCode: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(
                        Level.SEVERE,
                        "Failed to check lottery code requirement: " + e.getMessage());

                return new Response<>(
                        null,
                        "Failed to check lottery code requirement: " + e.getMessage());
            }
        }));
    }

    public Response<Boolean> validateLotteryCode(String token, int companyId, int eventId, String code) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "validateLotteryCode called");

            try {
                String role = getValidatedRole(token);

                if (role == null) {
                    logger.log(Level.SEVERE, "Lottery code validation failed: invalid token");
                    return new Response<>(null, "Invalid token");
                }

                int userId = getUserIdFromToken(token);
                if (userId!=-1 && suspensionRepo.haveActiveSuspension(userId)) {
                    logger.severe("User does not have write access caused by suspension");
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }

                Event event = eventRepo.findById(eventId);

                if (event.getCompanyId() != companyId) {
                    logger.log(Level.SEVERE, "Lottery code validation failed: event does not belong to company");
                    return new Response<>(null, "The selected event does not belong to the company");
                }

                if (!event.isActive()) {
                    logger.log(Level.SEVERE, "Lottery code validation failed: event is not active");
                    return new Response<>(null, "The selected event is not active");
                }

                LocalDateTime now = LocalDateTime.now();

                if (event.getSaleStartDate().isAfter(now)) {
                    logger.log(Level.INFO, "Lottery code validation stopped: sale has not started yet");
                    return new Response<>(null, "The sale for this event has not started yet");
                }

                if (!event.hasLottery()) {
                    logger.log(Level.INFO, "Lottery code validation completed: lottery code is not required");
                    return new Response<>(true, "Lottery code is not required");
                }

                Lottery lottery = lotteryRepo.findById(eventId);

                LocalDateTime lotteryEndTime = event.getSaleStartDate().plusHours(lottery.getExpirationTime());

                if (!now.isBefore(lotteryEndTime)) {
                    logger.log(Level.INFO, "Lottery code validation completed: lottery exclusive period has ended");
                    return new Response<>(true, "Lottery period has ended. Everyone can purchase tickets");
                }

                if (code == null || code.isBlank()) {
                    logger.log(Level.INFO, "Lottery code validation failed: missing lottery code");
                    return new Response<>(false, "Please enter your lottery code");
                }

                if (!lottery.codeMatchesUser(userId, code)) {
                    logger.log(Level.INFO, "Lottery code validation failed: invalid lottery code");
                    return new Response<>(false, "Invalid lottery code");
                }

                logger.log(Level.INFO, "Lottery code validation completed successfully");
                return new Response<>(true, "Lottery code is valid");

            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Lottery code validation failed: event or lottery not found");
                return new Response<>(null, "Event or lottery not found");

            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                logger.log(Level.WARNING, "Lottery code validation failed due to optimistic locking");
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during validateLotteryCode: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Lottery code validation failed: " + e.getMessage());
                return new Response<>(null, "Failed to validate lottery code: " + e.getMessage());
            }
        }));
    }

    public Response<TicketSupplyResultDTO> issueTickets(TicketSupplyRequestDTO request) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "issueTickets called");

            if (request == null) {
                return new Response<>(null, "Invalid ticket supply request");
            }

            try {
                TicketSupplyResultDTO result = ticketSupply.issue(request);

                if (result == null || !result.isSuccess()) {
                    return new Response<>(result, "Ticket issuance failed");
                }

                return new Response<>(result, "Tickets issued successfully");

            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Ticket issuance failed: " + e.getMessage());
                return new Response<>(null, "Ticket issuance failed");
            }
        });
    }

    public Response<Integer> userSelectTickets(String identifier, Integer eventId, Map<String, List<SeatingTicketDTO>> seatingZones, Map<String, Integer> standingZones) {
        return RetryHelper.executeWithRetry(()-> transactionTemplate.execute(status ->{
        logger.log(Level.INFO, "userSelectTickets called");
        String role = getValidatedRole(identifier);
        if(role == null){
            logger.log(Level.SEVERE, "identifier is null");
            return new Response<>(null, "Invalid identifier supplied");
        }
        int userId =getUserIdFromToken(identifier);
        if (userId!=-1 && suspensionRepo.haveActiveSuspension(userId)) {
            logger.severe("User does not have write access caused by suspension");
            return new Response<>(null, "user does not have write access caused by suspension.");
        }
        try {
            int totalSeatingTickets = seatingZones.values().stream()
                    .mapToInt(List::size)
                    .sum();


            int totalStandingTickets = standingZones.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            int totalTickets = totalSeatingTickets + totalStandingTickets;
            Event e = this.eventRepo.findById(eventId);
            Response<UserDTO> userResponse = getUserDTOFromToken(identifier);
            e.quantityExceedsPolicy(userResponse.getValue(), totalTickets);
            int totalUserTickets = 0;
            if(userResponse.getValue()!=null){
                totalUserTickets = e.countUserTickets(userResponse.getValue());
            }
            Company c = companyRepo.findById(e.getCompanyId());
            c.quantityExceedsPolicy(userResponse.getValue(), totalTickets,totalUserTickets);
            ActiveOrder newActiveOrder;
            try {
                String userIdentifier = auth.getUserIdentifier(identifier).getValue();

                    ActiveOrderDTO activeOrderDTO = activeOrderRepo
                            .findActiveOrderByUserAndEvent(userIdentifier, eventId)
                            .map(ActiveOrderDTO::new)
                            .orElseThrow(() -> new NoSuchElementException(
                                    "Active order not found for user"));

                    newActiveOrder = activeOrderRepo.findById(activeOrderDTO.getId());
                } catch (NoSuchElementException ex) {
                    logger.log(Level.SEVERE, "Active order not found for user");
                    return new Response<>(null, "Active order not found for user");
                }
                List<Integer> tickets = e.bookTickets(seatingZones, standingZones);
                newActiveOrder.setTickets(tickets);
                newActiveOrder.proceedToCheckout();
                this.eventRepo.store(e);
                activeOrderRepo.store(newActiveOrder);

                preExpirationScheduler.scheduleOrReschedule(identifier,
                        newActiveOrder.getId(), newActiveOrder.getCheckoutWarningTime());

                logger.log(Level.INFO, "Tickets selected successfully");
                return new Response<>(newActiveOrder.getId(), "Tickets selected successfully");
            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                return new Response<>(null, "Event not found");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during userSelectTickets: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Failed to select tickets : " + e.getMessage());
                return new Response<>(null, "Failed to select tickets : " + e.getMessage());
            }

    }));}

    public Response<CheckoutPriceDTO> prepareCheckout(String token, int activeOrderId) {
        return prepareCheckoutPrice(token, activeOrderId, null);
    }
        private Response<UserDTO> getUserDTOFromToken(String token) {
            String email = auth.getUserEmail(token).getValue();
            if (email != null) {
                Member m = userRepo.findUserByEmail(email);
                if (m != null) return new Response<>(m.getUserDTO(), "User found");
            }
            return new Response<>(null, "User not found");
    }

    public Response<CheckoutPriceDTO> applyCheckoutCoupon(
            String token,
            int activeOrderId,
            String couponCode) {

        return prepareCheckoutPrice(token, activeOrderId, couponCode);
    }

    private Response<CheckoutPriceDTO> prepareCheckoutPrice(
            String token,
            int activeOrderId,
            String couponCode) {

        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "prepareCheckoutPrice called");

            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid token");
                    return new Response<>(null, "Invalid token");
                }

                String userIdentifier = auth.getUserIdentifier(token).getValue();

                if (role.equals("MEMBER")) {
                    if (userIdentifier == null) {
                        logger.log(Level.SEVERE, "not a valid user email");
                        return new Response<>(null, "not a valid user email");
                    }

                    if (suspensionRepo.haveActiveSuspension(getUserIdFromToken(token))) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }
                }

                ActiveOrder activeOrder = activeOrderRepo.findById(activeOrderId);

                if (!activeOrder.getUserIdentifier().equals(userIdentifier)) {
                    return new Response<>(null, "Active order does not belong to user");
                }

                Event event = eventRepo.findById(activeOrder.getEventId());

                if (!event.isActive()) {
                    return new Response<>(null, "Event is not active");
                }

                if (!activeOrder.hasTickets()) {
                    return new Response<>(null, "Active order has no selected tickets");
                }

                if (activeOrder.isExpired()) {
                    return new Response<>(null, "Active order expired");
                }

                Company company = companyRepo.findById(event.getCompanyId());

                double originalPrice = event.getEventMap()
                        .calculateTotalPriceBeforeDiscount(activeOrder.getTickets());

                double priceAfterEventDiscounts = event.calculateFinalTotalPrice(
                        activeOrder.getTickets(),
                        couponCode);

                double finalPrice = company.getDiscountPolicy().apply(
                        priceAfterEventDiscounts,
                        activeOrder.getTickets().size(),
                        couponCode);

                activeOrder.setApprovedCheckoutPrice(finalPrice);
                activeOrderRepo.store(activeOrder);

                CheckoutPriceDTO checkoutPrice = new CheckoutPriceDTO(
                        originalPrice,
                        finalPrice,
                        event.getDiscountPolicy().describe(),
                        company.getDiscountPolicy().describe());

                return new Response<>(checkoutPrice, "Checkout price prepared successfully");

            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                return new Response<>(null, "Event or active order not found");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during prepareCheckoutPrice: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Failed to prepare checkout price: " + e.getMessage());
                return new Response<>(null, "Failed to prepare checkout price");
            }
        }));
    }

    public Response<Integer> checkoutAndPayment(
            String token,
            int activeOrderId,
            PaymentDetailsDTO paymentDetails) {

        // PHASE 1 (T1): validate and lock the order as PAYMENT_IN_PROGRESS.
        Response<CheckoutContext> prep = RetryHelper.executeWithRetry(() ->
                transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "checkoutAndPayment called");
            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    return new Response<>(null, "Invalid token");
                }
                String userIdentifier = auth.getUserIdentifier(token).getValue();
                if (role.equals("MEMBER")) {
                    if (userIdentifier == null) {
                        return new Response<>(null, "not a valid user email");
                    }
                    if (suspensionRepo.haveActiveSuspension(getUserIdFromToken(token))) {
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }
                }
                ActiveOrder activeOrder = activeOrderRepo.findById(activeOrderId);
                if (!activeOrder.getUserIdentifier().equals(userIdentifier)) {
                    return new Response<>(null, "Active order does not belong to user");
                }
                Event event = eventRepo.findById(activeOrder.getEventId());
                if (!event.isActive()) {
                    return new Response<>(null, "Event is not active");
                }
                if (!activeOrder.hasTickets()) {
                    return new Response<>(null, "Active order has no selected tickets");
                }
                if (activeOrder.isExpired()) {
                    // release the held tickets and drop the expired order in the same tx
                    event.releaseTickets(activeOrder.getTickets());
                    eventRepo.store(event);
                    activeOrderRepo.delete(activeOrderId);
                    return new Response<>(null, "Active order expired");
                }
                if (!activeOrder.hasApprovedCheckoutPrice()) {
                    return new Response<>(null, "Checkout price was not approved");
                }
                double total = activeOrder.getApprovedCheckoutPrice();

                activeOrder.startPayment();
                activeOrderRepo.store(activeOrder);

                List<Integer> tickets = activeOrder.getTickets();
                Map<String, List<PurchasedTicketDTO>> ticketsByZone =
                        event.getPurchasedTicketDetails(tickets).stream()
                                .collect(Collectors.groupingBy(PurchasedTicketDTO::getZoneName));
                List<TicketSupplyRequestDTO> supplyRequests = new ArrayList<>();
                for (Map.Entry<String, List<PurchasedTicketDTO>> entry : ticketsByZone.entrySet()) {
                    boolean isSeating = entry.getValue().get(0).getTicketType().equalsIgnoreCase("SEATING");
                    supplyRequests.add(new TicketSupplyRequestDTO(
                            userIdentifier, String.valueOf(event.getId()),
                            entry.getKey(), isSeating, entry.getValue()));
                }

                CheckoutContext ctx = new CheckoutContext(
                        activeOrderId, userIdentifier, event.getId(), total, tickets, supplyRequests);
                return new Response<>(ctx, "Ready for payment");

            } catch (IllegalStateException e) {
                status.setRollbackOnly();
                return new Response<>(null, e.getMessage());
            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                return new Response<>(null, "Event or active order not found");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Failed to prepare checkout: " + e.getMessage());
                return new Response<>(null, "Failed to complete purchase");
            }
        }));

        if (prep == null || prep.getValue() == null) {
            return new Response<>(null, prep == null ? "Failed to complete purchase" : prep.getMessage());
        }
        CheckoutContext ctx = prep.getValue();

        // PHASE 2 (no transaction): external payment, then ticket issuance.
        String paymentConfirmationId = null;
        boolean paymentCallFailed = false;
        try {
            paymentConfirmationId = paymentSystem.pay(ctx.total(), paymentDetails);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Payment call failed: " + e.getMessage());
            paymentCallFailed = true;
        }

        if (paymentCallFailed) {
            // external error (not a clean decline): leave the order intact for retry
            resetOrderToCheckout(ctx.activeOrderId());
            return new Response<>(null, "Failed to complete purchase");
        }
        if (paymentConfirmationId == null || paymentConfirmationId.isBlank()) {
            resetOrderToCheckout(ctx.activeOrderId());
            return new Response<>(null, "Payment rejected");
        }

        boolean issuanceFailed = false;
        List<String> issuedCodes = new ArrayList<>();
        try {
            for (TicketSupplyRequestDTO request : ctx.supplyRequests()) {
                TicketSupplyResultDTO result = ticketSupply.issue(request);
                if (result == null || !result.isSuccess()) {
                    issuanceFailed = true;
                    break;
                }
                if (result.getIssuedCodes() != null) {
                    issuedCodes.addAll(result.getIssuedCodes());
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ticket issuance failed: " + e.getMessage());
            issuanceFailed = true;
        }

        boolean refundApproved = false;
        if (issuanceFailed) {
            logger.log(Level.WARNING, "Issuance failed. Cancelling " + issuedCodes.size() + " issued tickets.");
            for (String issuedCode : issuedCodes) {
                try {
                    ticketSupply.cancelTicket(issuedCode);
                } catch (Exception cancelEx) {
                    logger.warning("Failed to cancel ticket " + issuedCode + ": " + cancelEx.getMessage());
                }
            }
            try {
                refundApproved = paymentSystem.refund(paymentConfirmationId, ctx.total());
            } catch (Exception e) {
                logger.warning("Refund call failed: " + e.getMessage());
            }
        }

        final String confirmationId = paymentConfirmationId;
        final boolean finalIssuanceFailed = issuanceFailed;
        final boolean finalRefundApproved = refundApproved;

        // PHASE 3 (T3): persist the outcome on a freshly fetched event/order.
        Response<FinalizeResult> finalizeResponse = RetryHelper.executeWithRetry(() ->
                transactionTemplate.execute(status -> {
            try {
                Event event = eventRepo.findById(ctx.eventId());
                Order order = new Order(
                        ctx.activeOrderId(), ctx.userIdentifier(), event.getId(),
                        event.getName(), event.getDate().toString(), event.getLocation().name(),
                        event.getPurchasedTicketDetails(ctx.tickets()), ctx.tickets(),
                        ctx.total(), confirmationId);

                if (finalIssuanceFailed) {
                    if (finalRefundApproved) {
                        order.markRefunded();
                        NotifyPayload payload = new NotifyPayload("Refund processed for order " + order.getOrderId()
                                + " in event " + event.getId() + " because ticket issuance failed", event.getId(), null);
                        sendOrSaveNotification(ctx.userIdentifier(), new NotifyDTO(NotifyType.GENERAL_POPUP, payload));
                    } else {
                        order.markRefundRequired();
                        NotifyPayload payload = new NotifyPayload("Refund for order " + order.getOrderId() + " in event "
                                + event.getId() + " because ticket issuance failed has been failed, please contact support",
                                event.getId(), null);
                        sendOrSaveNotification(ctx.userIdentifier(), new NotifyDTO(NotifyType.GENERAL_POPUP, payload));
                    }
                    event.releaseTickets(ctx.tickets());
                    if (event.findOrderById(order.getOrderId()) == null) {
                        event.getOrders().add(order);
                    }
                    eventRepo.store(event);
                    activeOrderRepo.delete(ctx.activeOrderId());
                    return new Response<>(
                            new FinalizeResult(null, "Ticket issuance failed", true, false),
                            "Ticket issuance failed");
                }

                if (event.findOrderById(order.getOrderId()) == null) {
                    event.getOrders().add(order);
                }
                event.markTicketsAsSold(ctx.tickets());
                eventRepo.store(event);
                activeOrderRepo.delete(ctx.activeOrderId());
                try {
                    NotifyDTO confirmation = new NotifyDTO(NotifyType.GENERAL_POPUP, new NotifyPayload(
                            "Your order #" + order.getOrderId() + " for \"" + event.getName()
                                    + "\" was completed successfully.", event.getId(), event.getCompanyId()));
                    notifier.notifyTab(token, confirmation);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Purchase confirmation notification failed: " + e.getMessage());
                }
                return new Response<>(
                        new FinalizeResult(order.getOrderId(), "Purchase completed successfully", true, true),
                        "Purchase completed successfully");

            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                return new Response<>(
                        new FinalizeResult(null, "Event or active order not found", false, false),
                        "Event or active order not found");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Failed to finalize purchase: " + e.getMessage());
                return new Response<>(
                        new FinalizeResult(null, "Failed to complete purchase", false, false),
                        "Failed to complete purchase");
            }
        }));

        FinalizeResult outcome = finalizeResponse == null ? null : finalizeResponse.getValue();
        if (outcome == null) {
            return new Response<>(null, "Failed to complete purchase");
        }

        // Post-commit side effects: need the committed state, so kept out of the transaction.
        if (outcome.orderConsumed()) {
            preExpirationScheduler.cancel(ctx.activeOrderId());
            if (outcome.success()) {
                try {
                    notifySoldOutIfApplicable(ctx.eventId());
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Sold-out notification failed: " + e.getMessage());
                }
            }
            promoteNextInQueue(ctx.eventId());
        }

        return new Response<>(outcome.orderId(), outcome.message());
    }

    // Reverts a PAYMENT_IN_PROGRESS order back to checkout after a rejected payment.
    private void resetOrderToCheckout(int activeOrderId) {
        RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            try {
                ActiveOrder order = activeOrderRepo.findById(activeOrderId);
                order.returnToCheckout();
                activeOrderRepo.store(order);
                return new Response<>(true, "Order reset to checkout");
            } catch (NoSuchElementException e) {
                return new Response<>(true, "Order no longer exists");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                throw e;
            }
        }));
    }

    // Phase-1 snapshot carried across the external calls into phase 3.
    private record CheckoutContext(
            int activeOrderId,
            String userIdentifier,
            int eventId,
            double total,
            List<Integer> tickets,
            List<TicketSupplyRequestDTO> supplyRequests) {
    }

    // Phase-3 outcome used to drive post-commit side effects and the final response.
    private record FinalizeResult(
            Integer orderId,
            String message,
            boolean orderConsumed,
            boolean success) {
    }

    public void cleanupExpiredOrders() {
        logger.log(Level.INFO, "cleanupExpiredOrders running");
        List<ActiveOrder> expired = activeOrderRepo.findExpired(LocalDateTime.now());

        for (ActiveOrder expiredOrder : expired) {
            try {
                RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
                    try {
                        // re-fetch fresh on each retry, state may have changed
                        ActiveOrder current = activeOrderRepo.findById(expiredOrder.getId());
                        Event event = eventRepo.findById(current.getEventId());
                        event.releaseTickets(current.getTickets());
                        eventRepo.store(event); // optimistic lock check
                        activeOrderRepo.delete(current.getId());
                        preExpirationScheduler.cancel(current.getId());
                        promoteNextInQueue(event.getId());
                        return new Response<>(true, "expired");
                    } catch (NoSuchElementException e) {
                        // order already gone — user placed it before cleanup hit. Fine.
                        return new Response<>(true, "already removed");
                    } catch (OptimisticLockingFailureException e) {
                        status.setRollbackOnly();
                        throw e;
                    } catch (TransientDataAccessException e) {
                        status.setRollbackOnly();
                        throw e;
                    }
                }));
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to expire order " + expiredOrder.getId() + ": " + e.getMessage());
                // swallow — keep processing other orders
            }
        }
    }
    public Response<ActiveOrderDTO> memberProceedAnActiveOrder(String token) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "memberProceedActiveOrder called");
            String role = getValidatedRole(token); //if token is expired
            if (role == null) {
                return new Response<>(null, "Invalid token");
            }
            try {
                int userId = getUserIdFromToken(token);
                if(userId == -1){
                    logger.log(Level.SEVERE, "user not logged in");
                    return new Response<>(null, "user not logged in");
                }
                if (suspensionRepo.haveActiveSuspension(getUserIdFromToken(token))) {
                    logger.severe("User does not have write access caused by suspension");
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }
                String userEmail = auth.getUserEmail(token).getValue();
                ActiveOrderDTO order = activeOrderRepo.findOrderByUserId(userEmail);
                if (!order.getUserIdentifier().equals(auth.getUserEmail(token).getValue())) { // email for member
                    logger.log(Level.SEVERE, "Unauthorized access to active order");
                    return new Response<>(null, "Unauthorized access to active order");
                }
                ActiveOrder activeOrder = activeOrderRepo.findById(order.getId());
                if (activeOrder.isExpired(LocalDateTime.now())) {
                    logger.log(Level.SEVERE, "Active order has expired");
                    return new Response<>(null, "Active order has expired");
                }

                logger.log(Level.INFO, "Active order retrieved successfully");
                return new Response<>(order, "Active order retrieved successfully");
            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Active order not found: " + e.getMessage());
                return new Response<>(null, "Active order not found");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during memberProceedAnActiveOrder: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Failed to proceed active order: " + e.getMessage());
                return new Response<>(null, "Failed to proceed active order: " + e.getMessage());
            }
        }));
    }

    public Response<ActiveOrderDTO> returnToEditSelection(String token) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "returnToEditSelection called");
            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid token");
                    return new Response<>(null, "Invalid token");
                }
                int userId = getUserIdFromToken(token);
                if (userId != -1 && suspensionRepo.haveActiveSuspension(userId)) {
                    logger.severe("User does not have write access caused by suspension");
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }
                // Match the ownership key the order was stored under in enterEventPurchase:
                // member -> email, guest -> raw token.
                String userIdentifier = auth.getUserIdentifier(token).getValue();

                ActiveOrderDTO dto = activeOrderRepo.findOrderByUserId(userIdentifier);
                ActiveOrder order = activeOrderRepo.findById(dto.getId());

                if (order.isExpired(LocalDateTime.now())) {
                    logger.log(Level.SEVERE, "Active order has expired");
                    return new Response<>(null, "Active order has expired");
                }

                if (order.getStage() != STAGE.CHECKING_OUT) {
                    logger.log(Level.SEVERE, "Order is not at checkout; cannot enter edit mode");
                    return new Response<>(null, "Order is not at checkout; cannot enter edit mode");
                }

                order.returnToEditSelection();
                activeOrderRepo.store(order);
                // checkoutStartedAt is intentionally NOT touched — the continuous 10-minute
                // seat-hold timer and its pending pre-expiration warning carry over unchanged.

                logger.log(Level.INFO, "Order moved to EDITING successfully");
                return new Response<>(new ActiveOrderDTO(order), "Returned to edit selection");

            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Active order not found: " + e.getMessage());
                return new Response<>(null, "Active order not found");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during returnToEditSelection: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Failed to return to edit selection: " + e.getMessage());
                return new Response<>(null, "Failed to return to edit selection: " + e.getMessage());
            }
        }));
    }

    public Response<ActiveOrderDTO> editTicketSelection(
            String token,
            Map<String, List<SeatingTicketDTO>> seatingToRemove,
            Map<String, List<SeatingTicketDTO>> seatingToAdd,
            Map<String, Integer> standingDesired) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "editTicketSelection called");
            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid token");
                    return new Response<>(null, "Invalid token");
                }
                int userId = getUserIdFromToken(token);
                if (userId != -1 && suspensionRepo.haveActiveSuspension(userId)) {
                    logger.severe("User does not have write access caused by suspension");
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }
                // Match the ownership key the order was stored under in enterEventPurchase:
                // member -> email, guest -> raw token.
                String userIdentifier = auth.getUserIdentifier(token).getValue();

                // the case that a user tries to remove and add the same tickets
                if (seatingToRemove != null && seatingToAdd != null) {
                    Set<String> removeKeys = new HashSet<>();
                    for (Map.Entry<String, List<SeatingTicketDTO>> e : seatingToRemove.entrySet())
                        for (SeatingTicketDTO s : e.getValue())
                            removeKeys.add(e.getKey() + ":" + s.getRow() + "-" + s.getCol());
                    for (Map.Entry<String, List<SeatingTicketDTO>> e : seatingToAdd.entrySet())
                        for (SeatingTicketDTO s : e.getValue())
                            if (removeKeys.contains(e.getKey() + ":" + s.getRow() + "-" + s.getCol()))
                                return new Response<>(null, "Seat cannot be both added and removed: zone="
                                        + e.getKey() + " row=" + s.getRow() + " col=" + s.getCol());
                }
                ActiveOrderDTO dto = activeOrderRepo.findOrderByUserId(userIdentifier);
                ActiveOrder order = activeOrderRepo.findById(dto.getId());

                if (order.isExpired(LocalDateTime.now())) {
                    logger.log(Level.SEVERE, "Active order has expired");
                    return new Response<>(null, "Active order has expired");
                }

                if (order.getStage() != STAGE.EDITING) {
                    logger.log(Level.SEVERE, "Order is not in edit mode; call returnToEditSelection first");
                    return new Response<>(null, "Order is not in edit mode; call returnToEditSelection first");
                }

                Event event = eventRepo.findById(order.getEventId());
                List<Integer> currentTickets = order.getTickets();
                List<Integer> newTickets = new ArrayList<>(currentTickets);

                int projected = currentTickets.size();
                if (seatingToRemove != null)
                    for (List<SeatingTicketDTO> l : seatingToRemove.values())
                        projected -= l.size();
                if (seatingToAdd != null)
                    for (List<SeatingTicketDTO> l : seatingToAdd.values())
                        projected += l.size();
                if (standingDesired != null) {
                    for (Map.Entry<String, Integer> e : standingDesired.entrySet()) {
                        if (e.getValue() < 0)
                            return new Response<>(null, "Standing quantity cannot be negative");
                        int cur = event.countStandingInZone(e.getKey(), currentTickets);
                        projected += (e.getValue() - cur);
                    }
                }
                if (projected < 0) {
                    return new Response<>(null, "Edit would result in negative ticket count");
                }

                UserDTO userDTO = getUserDTOFromToken(token).getValue();
                event.quantityTotalExceedsPolicy(userDTO, projected); // throws if exceeded
                Company company = companyRepo.findById(event.getCompanyId());
                company.quantityExceedsPolicy(userDTO, 0, projected); // throws if exceeded

                if (seatingToRemove != null && !seatingToRemove.isEmpty()) {
                    List<Integer> ids = event.findSeatingTicketIds(seatingToRemove);
                    if (!new HashSet<>(newTickets).containsAll(ids)) {
                        return new Response<>(null, "Cannot remove tickets not in your order");
                    }
                    event.releaseTickets(ids);
                    newTickets.removeAll(ids);
                }

                if (standingDesired != null) {
                    for (Map.Entry<String, Integer> e : standingDesired.entrySet()) {
                        String zone = e.getKey();
                        int desired = e.getValue();
                        int current = event.countStandingInZone(zone, newTickets);
                        int delta = desired - current;
                        if (delta > 0) {
                            List<Integer> added = event.bookTickets(
                                    Collections.emptyMap(), Map.of(zone, delta));
                            newTickets.addAll(added);
                        } else if (delta < 0) {
                            List<Integer> ids = event.pickStandingFromZone(zone, newTickets, -delta);
                            event.releaseTickets(ids);
                            newTickets.removeAll(ids);
                        }
                    }
                }
                if (seatingToAdd != null && !seatingToAdd.isEmpty()) {
                    List<Integer> added = event.bookTickets(seatingToAdd, Collections.emptyMap());
                    newTickets.addAll(added);
                }
                order.setTickets(newTickets);
                order.confirmEdit();

                eventRepo.store(event);
                activeOrderRepo.store(order);

                // The 10-minute seat-hold deadline is fixed at userSelectTickets time and never
                // resets — the warning scheduled then remains valid, so no reschedule here.

                logger.log(Level.INFO, "Selection updated successfully");
                return new Response<>(new ActiveOrderDTO(order), "Selection updated successfully");

            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Order or event not found: " + e.getMessage());
                return new Response<>(null, "Order or event not found");
            } catch (IllegalArgumentException | IllegalStateException e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Invalid edit: " + e.getMessage());
                return new Response<>(null, "Invalid edit: " + e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during editTicketSelection: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Failed to edit selection: " + e.getMessage());
                return new Response<>(null, "Failed to edit selection: " + e.getMessage());
            }
        }));
    }

    public void shutdown() {
        cleanupScheduler.shutdown();
        preExpirationScheduler.shutdown();
    }

    public Response<Boolean> cancelEventQueueEntry(String token, int eventId) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            String role = getValidatedRole(token);
            if (role == null) {
                return new Response<>(false, "Invalid token");
            }

            try {
                Event event = eventRepo.findById(eventId);

                boolean removed = event.getEventQueue().remove(token);

                if (removed) {
                    eventRepo.store(event);
                }

                return new Response<>(
                        removed,
                        removed ? "Removed from event queue" : "User was not waiting in event queue");

            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                return new Response<>(false, "Event not found");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during cancelEventQueueEntry: " + e.getMessage());
                throw e;
            }
        }));
    }

    private void promoteNextInQueue(int eventId) {
        String nextToken = dequeueNextToken(eventId);
        boolean created;
        if (nextToken != null) {
            created = createActiveOrderForToken(nextToken, eventId);
            if (created) {
                try {
                    NotifyPayload payload = new NotifyPayload("Your turn for event " + eventId + " has arrived!",
                            eventId, null);
                    NotifyDTO notifyDTO = new NotifyDTO(NotifyType.QUEUE_EVENT_TURN_ARRIVED, payload);
                    notifier.notifyTab(nextToken, notifyDTO);
                    logger.info(
                            "Notified tab " + nextToken + " that their turn for event " + eventId + " has arrived.");
                } catch (Exception e) {
                    logger.log(Level.WARNING,
                            "Failed to notify tab " + nextToken + " about queue turn: " + e.getMessage());
                }
            }

        }
    }

    private String dequeueNextToken(int eventId) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            try {
                Event event = eventRepo.findById(eventId);
                if (event.getEventQueue().isEmpty()
                        || activeOrderRepo.countActiveOrdersForEvent(eventId) >= capacity) {
                    return new Response<>(null, "Queue empty or event at capacity");
                }
                String nextToken = event.getEventQueue().dequeue();
                eventRepo.store(event);
                return new Response<>(nextToken, "Token dequeued successfully");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                throw e;
            }
        })).getValue();
    }

    private boolean createActiveOrderForToken(String token, int eventId) {
        boolean skipped = RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            try {
                String role = getValidatedRole(token);
                String userIdentifier = auth.getUserIdentifier(token).getValue();
                activeOrderRepo.alreadyHasActiveOrder(userIdentifier, eventId);
                ActiveOrder nextOrder = new ActiveOrder(userIdentifier, eventId, new ArrayList<>());
                activeOrderRepo.store(nextOrder);
                return new Response<>(false, "created");

            } catch (IllegalStateException e) {
                return new Response<>(true, "duplicate");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                throw e;
            }
        })).getValue();

        if (skipped) {
            promoteNextInQueue(eventId);
            return false;
        }
        return true;
    }

    private void notifySoldOutIfApplicable(int eventId) {
        Event persisted = eventRepo.findById(eventId);
        if (persisted == null || !persisted.isSoldOut()) {
            return;
        }

        Company company = companyRepo.findById(persisted.getCompanyId());
        if (company == null) {
            return;
        }

        Set<Integer> recipientIds = new HashSet<>();
        recipientIds.addAll(company.getCompanyPermission().getOwnerIds());
        recipientIds.addAll(company.getCompanyPermission().getCompanyTree().keySet());

        NotifyDTO payload = new NotifyDTO(
                NotifyType.GENERAL_POPUP,
                new NotifyPayload("Event \"" + persisted.getName() + "\" is sold out.", eventId, null));

        for (Integer userId : recipientIds) {
            try {
                sendOrSaveNotification(userRepo.getUserEmail(userId), payload);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Sold-out notify failed for user " + userId + ": " + e.getMessage());
            }
        }
    }
    private int getUserIdFromToken(String token) {
        String email = auth.getUserEmail(token).getValue();
        if (email != null) {
            Member m = userRepo.findUserByEmail(email);
            if (m != null) return m.getUserId();
        }
        return -1; //for guest or invalid
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

    private String getValidatedRole(String token) {
        String role = auth.getRole(token).getValue();
        if (role == null) {
            notifyTokenExpired(token);
            return null;
        }
        return role;
    }

    public Response<Integer> getCompanyIdByActiveOrder(String token, int activeOrderId) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "getCompanyIdByActiveOrder called");

            try {
                String role = getValidatedRole(token);

                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid token");
                    return new Response<>(null, "Invalid token");
                }

                String userIdentifier = auth.getUserIdentifier(token).getValue();

                ActiveOrder order = activeOrderRepo.findById(activeOrderId);

                if (!order.getUserIdentifier().equals(userIdentifier)) {
                    logger.log(Level.SEVERE, "Unauthorized active order access");
                    return new Response<>(null, "Unauthorized active order access");
                }

                Event event = eventRepo.findById(order.getEventId());

                return new Response<>(
                        event.getCompanyId(),
                        "Company ID retrieved successfully"
                );

            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                return new Response<>(
                        null,
                        "Active order or event not found"
                );

            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during getCompanyIdByActiveOrder: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                return new Response<>(
                        null,
                        "Failed to retrieve company ID"
                );
            }
        }));
    }
    public Response<ActiveOrderSelectionDTO> getCurrentActiveOrderSelection(String token) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            logger.log(Level.INFO, "getCurrentActiveOrderSelection called");

            try {
                String role = getValidatedRole(token);

                if (role == null) {
                    return new Response<>(null, "Invalid token");
                }

                int userId = getUserIdFromToken(token);

                if (userId != -1 && suspensionRepo.haveActiveSuspension(userId)) {
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }

                String userIdentifier = auth.getUserIdentifier(token).getValue();

                ActiveOrderDTO activeOrderDTO =
                        activeOrderRepo.findOrderByUserId(userIdentifier);

                ActiveOrder activeOrder =
                        activeOrderRepo.findById(activeOrderDTO.getId());

                if (activeOrder.isExpired(LocalDateTime.now())) {
                    return new Response<>(null, "Active order has expired");
                }

                Event event =
                        eventRepo.findById(activeOrder.getEventId());

                return new Response<>(
                        event.getActiveOrderSelection(activeOrder.getTickets()),
                        "Current active order selection retrieved successfully"
                );

            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during getCurrentActiveOrderSelection: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                return new Response<>(
                        null,
                        "Failed to retrieve current active order selection: " + e.getMessage()
                );
            }
        }));
    }

    public Response<Long> getCheckoutRemainingSeconds(String token, int activeOrderId) {
        return RetryHelper.executeWithRetry(() -> transactionTemplate.execute(status -> {
            try {
                String role = getValidatedRole(token);

                if (role == null) {
                    return new Response<>(null, "Invalid token");
                }

                String userIdentifier = auth.getUserIdentifier(token).getValue();

                if (userIdentifier == null || userIdentifier.isBlank()) {
                    return new Response<>(null, "Invalid user identifier");
                }

                ActiveOrder activeOrder = activeOrderRepo.findById(activeOrderId);

                if (!activeOrder.getUserIdentifier().equals(userIdentifier)) {
                    return new Response<>(null, "Active order does not belong to user");
                }

                if (activeOrder.isExpired(LocalDateTime.now())) {
                    cleanupExpiredOrders();
                    return new Response<>(0L, "Active order expired");
                }

                long remainingSeconds =
                        activeOrder.getRemainingCheckoutSeconds(LocalDateTime.now());

                return new Response<>(
                        remainingSeconds,
                        "Checkout timer retrieved successfully"
                );

            } catch (NoSuchElementException e) {
                status.setRollbackOnly();
                return new Response<>(null, "Active order not found");
            } catch (OptimisticLockingFailureException e) {
                status.setRollbackOnly();
                throw e;
            } catch (TransientDataAccessException e) {
                status.setRollbackOnly();
                logger.warning("Transient DB error during getCheckoutRemainingSeconds: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                status.setRollbackOnly();
                return new Response<>(
                        null,
                        "Failed to get checkout timer: " + e.getMessage()
                );
            }
        }));
    }
}
