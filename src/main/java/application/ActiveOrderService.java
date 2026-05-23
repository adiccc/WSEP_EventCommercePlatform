package application;

import DTO.EnterPurchaseDTO;
import DTO.NotifyDTO;
import DTO.NotifyPayload;
import DTO.NotifyType;
import DTO.PaymentDetailsDTO;
import DTO.TicketSupplyRequestDTO;
import DTO.TicketSupplyResultDTO;

import domain.activeOrder.ActiveOrder;
import domain.activeOrder.IActiveOrderRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dto.ActiveOrderDTO;
import domain.dto.EventMapDTO;
import domain.dto.SeatingTicketDTO;
import domain.dto.UserDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.event.Order;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;
import Exception.OptimisticLockingFailureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import java.util.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service

public class ActiveOrderService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());
    private final AtomicInteger idGenerator = new AtomicInteger(1);
    private final IEventRepo eventRepo;
    private final IActiveOrderRepo activeOrderRepo;
    private final ICompanyRepo companyRepo;
    private final ILotteryRepo lotteryRepo;
    private final IAuth auth;
    private final IAccessValidator accessValidator;
    private final IPaymentSystem paymentSystem;
    private final ITicketSupply ticketSupply;
    private final INotifier notifier;
    private int capacity;
    private final ScheduledExecutorService cleanupScheduler;

    @Autowired
    public ActiveOrderService(
            IAuth auth,
            IActiveOrderRepo activeOrderRepo,
            IEventRepo eventRepo,
            ICompanyRepo companyRepo,
            ILotteryRepo lotteryRepo,
            IPaymentSystem paymentSystem,
            ITicketSupply ticketSupply,
            IAccessValidator accessValidator,
            INotifier notifier,
            @Value("${active-order.capacity:20}") int capacity) {
        this.eventRepo = eventRepo;
        this.activeOrderRepo = activeOrderRepo;
        this.companyRepo = companyRepo;
        this.lotteryRepo = lotteryRepo;
        this.auth = auth;
        this.paymentSystem = paymentSystem;
        this.ticketSupply = ticketSupply;
        this.accessValidator = accessValidator;
        this.notifier = notifier;
        this.capacity = capacity;
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
                30, 30, TimeUnit.SECONDS
        );
    }

    public int getCapacity() {
        return this.capacity;
    }

    public Response<EnterPurchaseDTO> enterEventPurchase(String token, int companyId, int eventId) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "enterEventPurchase called");
            cleanupExpiredOrders();

            String role = auth.getRole(token).getValue();

            if (role == null) {
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(null, "Invalid token");
            }

            Integer userId = auth.getUserId(token).getValue();

            if (!accessValidator.hasWriteAccess(userId)) {
                logger.severe("User does not have write access");
                return new Response<>(null, "user does not have write access.");
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

                String userIdentifier = role.equals("MEMBER")
                        ? auth.getUserEmail(token).getValue()
                        : token;

                Optional<ActiveOrder> existingOrder =
                        activeOrderRepo.findActiveOrderByUserAndEvent(userIdentifier, eventId);

                if (existingOrder.isPresent()) {
                    logger.log(Level.INFO,
                            "Existing active order found. Redirecting user to checkout. Order ID: "
                                    + existingOrder.get().getId());
                    return new Response<>(
                            new EnterPurchaseDTO(
                                    new EventMapDTO(e.getMap()),
                                    new ActiveOrderDTO(existingOrder.get()),
                                    true,
                                    false, null
                            ),
                            "Existing active order found"
                    );
                }

                if (e.hasLottery()) {
                    Lottery l = lotteryRepo.findById(eventId);
                    int code = userId; // the code of each user who registered to the lottery is his ID because there ara no notifications in the system

                    LocalDateTime lotteryEndTime =
                            e.getSaleStartDate().plusHours(l.getExpirationTime());

                    if (!l.getWinners().contains(code)) {
                        if (LocalDateTime.now().isBefore(lotteryEndTime)) {
                            return new Response<>(
                                    null,
                                    "User is not a lottery winner and lottery registration is still open"
                            );
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
                                        position
                                ),
                                "User is still waiting in queue. Position: " + position
                        );
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
                                    position
                            ),
                            "Event is full, user added to waiting queue. Position: " + position
                    );
                }

                int orderId = idGenerator.getAndIncrement();

                ActiveOrder newActiveOrder =
                        new ActiveOrder(orderId, userIdentifier, eventId, new ArrayList<>());

                logger.log(Level.INFO,
                        "Creating active order with ID: " + newActiveOrder.getId()
                                + " for user identifier: " + newActiveOrder.getUserIdentifier()
                                + " and event ID: " + newActiveOrder.getEventId());

                eventRepo.store(e);
                activeOrderRepo.store(newActiveOrder);

                logger.log(Level.INFO, "Event map retrieved successfully");

                return new Response<>(
                        new EnterPurchaseDTO(
                                new EventMapDTO(e.getMap()),
                                new ActiveOrderDTO(newActiveOrder),
                                false, false, null
                        ),
                        "Event map retrieved successfully"
                );

            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                return new Response<>(null, "Event not found");
            } catch (IllegalStateException e) {
                logger.log(Level.SEVERE, e.getMessage());
                return new Response<>(null, e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to enter event purchase : " + e.getMessage());
                return new Response<>(
                        null,
                        "Failed to enter event purchase  : " + e.getMessage()
                );
            }
        });
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

    public Response<Integer>userSelectTickets(String identifier, Integer eventId, Map<String, List<SeatingTicketDTO>> seatingZones, Map<String, Integer> standingZones) {
        return RetryHelper.executeWithRetry(()->{
        logger.log(Level.INFO, "userSelectTickets called");
        String role = auth.getRole(identifier).getValue();
        if(role == null){
            logger.log(Level.SEVERE, "identifier is null");
            return new Response<>(null, "Invalid identifier supplied");
        }
        if(!accessValidator.hasWriteAccess(auth.getUserId(identifier).getValue())){
            logger.severe("User does not have write access");
            return new Response<>(null, "user does not have write access.");
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
            Response<UserDTO> userResponse = auth.getUserDTO(identifier);
            e.quantityExceedsPolicy(userResponse.getValue(), totalTickets);

            int totalUserTickets = e.countUserTickets(userResponse.getValue());
            Company c = companyRepo.findById(e.getCompanyId());
            c.quantityExceedsPolicy(userResponse.getValue(), totalTickets,totalUserTickets);
            ActiveOrder newActiveOrder;
            try {
                String userIdentifier = role.equals("MEMBER")
                        ? auth.getUserEmail(identifier).getValue()
                        : identifier;

                ActiveOrderDTO activeOrderDTO =
                        activeOrderRepo.findActiveOrderByUserAndEvent(userIdentifier, eventId)
                                .map(ActiveOrderDTO::new)
                                .orElseThrow(() -> new NoSuchElementException(
                                        "Active order not found for user"
                                ));

                newActiveOrder = activeOrderRepo.findById(activeOrderDTO.getId());
            }
            catch (NoSuchElementException ex) {
                logger.log(Level.SEVERE, "Active order not found for user");
                return new Response<>(null, "Active order not found for user");
            }
            List<Integer> tickets = e.bookTickets(seatingZones,standingZones);
            newActiveOrder.setTickets(tickets);
            newActiveOrder.proceedToCheckout();
            this.eventRepo.store(e);
            activeOrderRepo.store(newActiveOrder);

            logger.log(Level.INFO, "Tickets selected successfully");
            return new Response<>(newActiveOrder.getId(), "Tickets selected successfully");
        } catch (NoSuchElementException e) {
            logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
            return new Response<>(null, "Event not found");
        } catch (OptimisticLockingFailureException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to select tickets : " + e.getMessage());
            return new Response<>(null, "Failed to select tickets : " + e.getMessage());
        }

    });}

    public Response<Integer> checkoutAndPayment(
            String token,
            int activeOrderId,
            PaymentDetailsDTO paymentDetails) {

        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "checkoutAndPayment called");

            ActiveOrder activeOrder = null;
            Event event = null;
            Order order = null;

            boolean shouldDeleteActiveOrder = false;
            boolean shouldReleaseTickets = false;
            boolean paymentStageAcquired = false;

            try {
                String role = auth.getRole(token).getValue();
                if(role == null){
                    logger.log(Level.SEVERE, "Invalid token");
                    return new Response<>(null, "Invalid token");
                }
                String userIdentifier=token; //for Guest
                if(role.equals("MEMBER")) { //if it's a member should be email
                    userIdentifier=auth.getUserEmail(token).getValue();
                    if (userIdentifier == null) {
                        logger.log(Level.SEVERE, "not a valid user email");
                        return new Response<>(null, "not a valid user email");

                    }
                    if(!accessValidator.hasWriteAccess(auth.getUserId(token).getValue())){
                        logger.severe("User does not have write access");
                        return new Response<>(null, "user does not have write access.");
                    }
                }
                activeOrder = activeOrderRepo.findById(activeOrderId);
                if (!(activeOrder.getUserIdentifier().equals(userIdentifier))) { //userIdentifier
                    return new Response<>(null, "Active order does not belong to user");
                }

                event = eventRepo.findById(activeOrder.getEventId());

                if (!event.isActive()) {
                    return new Response<>(null, "Event is not active");
                }

                if (!activeOrder.hasTickets()) {
                    return new Response<>(null, "Active order has no selected tickets");
                }

                if (activeOrder.isExpired()) {
                    shouldDeleteActiveOrder = true;
                    shouldReleaseTickets = true;
                    return new Response<>(null, "Active order expired");
                }

                try {
                    activeOrder.startPayment();
                    activeOrderRepo.store(activeOrder);
                    paymentStageAcquired = true;
                } catch (IllegalStateException e) {
                    return new Response<>(null, e.getMessage());
                } catch (OptimisticLockingFailureException e) {
                    throw e;
                }

                Company company = companyRepo.findById(event.getCompanyId());

                double totalAfterEventDiscounts = event.calculateFinalTotalPrice(
                        activeOrder.getTickets(),
                        paymentDetails.getCouponCode()
                );

                double total = company.getDiscountPolicy().apply(
                        totalAfterEventDiscounts,
                        activeOrder.getTickets().size(),
                        paymentDetails.getCouponCode()
                );

                String paymentConfirmationId = paymentSystem.pay(total, paymentDetails);

                if (paymentConfirmationId == null || paymentConfirmationId.isBlank()) {
                    ActiveOrder freshOrder = activeOrderRepo.findById(activeOrderId);
                    freshOrder.returnToCheckout();
                    activeOrderRepo.store(freshOrder);
                    paymentStageAcquired = false;
                    return new Response<>(null, "Payment rejected");
                }

                order = new Order(
                        activeOrder.getId(),
                        userIdentifier,
                        event.getId(),
                        activeOrder.getTickets(),
                        total,
                        paymentConfirmationId
                );

                shouldDeleteActiveOrder = true;

                boolean issuanceFailed = false;

                try {
                    TicketSupplyResultDTO issueResult = ticketSupply.issue(
                            new TicketSupplyRequestDTO(activeOrder.getTickets())
                    );

                    if (issueResult == null || !issueResult.isSuccess()) {
                        issuanceFailed = true;
                    }

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Ticket issuance failed: " + e.getMessage());
                    issuanceFailed = true;
                }

                if (issuanceFailed) {
                    boolean refundApproved = paymentSystem.refund(paymentConfirmationId, total);

                    if (refundApproved) {
                        order.markRefunded();
                    } else {
                        order.markRefundRequired();
                    }

                    shouldReleaseTickets = true;
                    return new Response<>(null, "Ticket issuance failed");
                }

                return new Response<>(order.getOrderId(), "Purchase completed successfully");

            } catch (NoSuchElementException e) {
                return new Response<>(null, "Event or active order not found");

            } catch (OptimisticLockingFailureException e) {
                if (paymentStageAcquired && activeOrder != null && order == null) {
                    try {
                        ActiveOrder freshOrder = activeOrderRepo.findById(activeOrderId);
                        freshOrder.returnToCheckout();
                        activeOrderRepo.store(freshOrder);
                    } catch (Exception resetException) {
                        logger.log(Level.SEVERE, "Failed to reset active order stage: " + resetException.getMessage());
                    }
                }
                throw e;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to complete purchase: " + e.getMessage());

                if (paymentStageAcquired && activeOrder != null && order == null) {
                    try {
                        ActiveOrder freshOrder = activeOrderRepo.findById(activeOrderId);
                        freshOrder.returnToCheckout();
                        activeOrderRepo.store(freshOrder);
                    } catch (Exception resetException) {
                        logger.log(Level.SEVERE, "Failed to reset active order stage: " + resetException.getMessage());
                    }
                }

                return new Response<>(null, "Failed to complete purchase");

            } finally {
                if (event != null && activeOrder != null) {
                    if (shouldReleaseTickets || order != null) {
                        boolean stored = false;

                        while (!stored) {
                            try {
                                Event freshEvent = eventRepo.findById(event.getId());

                                if (shouldReleaseTickets) {
                                    freshEvent.releaseTickets(activeOrder.getTickets());
                                }

                                if (order != null && freshEvent.findOrderById(order.getOrderId()) == null) {
                                    freshEvent.getOrders().add(order);
                                }

                                if (order != null && !shouldReleaseTickets) {
                                    freshEvent.markTicketsAsSold(activeOrder.getTickets());
                                }

                                eventRepo.store(freshEvent);
                                stored = true;

                            } catch (OptimisticLockingFailureException e) {
                                logger.log(Level.INFO, "Retrying event update after optimistic locking failure");
                            }
                        }

                        if (order != null && !shouldReleaseTickets) {
                            try {
                                notifySoldOutIfApplicable(event.getId());
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Sold-out notification failed: " + e.getMessage());
                            }
                        }
                    }
                }
                if (shouldDeleteActiveOrder) {
                    activeOrderRepo.delete(activeOrderId);
                    promoteNextInQueue(event.getId());

                }
            }
        });
    }


    public void cleanupExpiredOrders() {
        logger.log(Level.INFO, "cleanupExpiredOrders running");
        List<ActiveOrder> expired = activeOrderRepo.findExpired(LocalDateTime.now());

        for (ActiveOrder expiredOrder : expired) {
            try {
                RetryHelper.executeWithRetry(() -> {
                    try {
                        // re-fetch fresh on each retry, state may have changed
                        ActiveOrder current = activeOrderRepo.findById(expiredOrder.getId());
                        Event event = eventRepo.findById(current.getEventId());
                        event.releaseTickets(current.getTickets());
                        eventRepo.store(event);                  // optimistic lock check
                        activeOrderRepo.delete(current.getId());
                        promoteNextInQueue(event.getId());
                        return new Response<>(true, "expired");
                    }  catch (NoSuchElementException e) {
                        // order already gone — user placed it before cleanup hit. Fine.
                        return new Response<>(true, "already removed");
                    }
                    catch (OptimisticLockingFailureException e) {
                        throw e;
                    }
                });
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to expire order " + expiredOrder.getId() + ": " + e.getMessage());
                // swallow — keep processing other orders
            }
        }
    }


    public Response<ActiveOrderDTO> memberProceedAnActiveOrder(String token) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "memberProceedActiveOrder called");
            try {
                int userId = auth.getUserId(token).getValue();
                if(userId == -1){
                    logger.log(Level.SEVERE, "user not logged in");
                    return new Response<>(null, "user not logged in");
                }
                if(!accessValidator.hasWriteAccess(userId)){
                    logger.severe("User does not have write access");
                    return new Response<>(null, "user does not have write access.");
                }
                String userEmail = auth.getUserEmail(token).getValue();
                ActiveOrderDTO order = activeOrderRepo.findOrderByUserId(userEmail);
                if (!order.getUserIdentifier().equals(auth.getUserEmail(token).getValue())) { //email for member
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
                logger.log(Level.SEVERE, "Active order not found: " + e.getMessage());
                return new Response<>(null, "Active order not found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to proceed active order: " + e.getMessage());
                return new Response<>(null, "Failed to proceed active order: " + e.getMessage());
            }
        });
    }


    public Response<ActiveOrderDTO> editTicketSelection(
            String token,
            Map<String, List<SeatingTicketDTO>> seatingToRemove,
            Map<String, List<SeatingTicketDTO>> seatingToAdd,
            Map<String, Integer> standingDesired) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "editTicketSelection called");
            try {
                String role = auth.getRole(token).getValue();
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid token");
                    return new Response<>(null, "Invalid token");
                }
                if(!accessValidator.hasWriteAccess(auth.getUserId(token).getValue())){
                    logger.severe("User does not have write access");
                    return new Response<>(null, "user does not have write access.");
                }
                String email = auth.getUserEmail(token).getValue();

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
                ActiveOrderDTO dto = activeOrderRepo.findOrderByUserId(email);
                ActiveOrder order = activeOrderRepo.findById(dto.getId());

                if (order.isExpired(LocalDateTime.now())) {
                    logger.log(Level.SEVERE, "Active order has expired");
                    return new Response<>(null, "Active order has expired");
                }

                Event event = eventRepo.findById(order.getEventId());
                List<Integer> currentTickets = order.getTickets();
                List<Integer> newTickets = new ArrayList<>(currentTickets);

                int projected = currentTickets.size();
                if (seatingToRemove != null)
                    for (List<SeatingTicketDTO> l : seatingToRemove.values()) projected -= l.size();
                if (seatingToAdd != null)
                    for (List<SeatingTicketDTO> l : seatingToAdd.values()) projected += l.size();
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

                UserDTO userDTO = auth.getUserDTO(token).getValue();
                event.quantityTotalExceedsPolicy(userDTO, projected); // throws if exceeded
                Company company = companyRepo.findById(event.getCompanyId());
                company.quantityExceedsPolicy(userDTO, 0,projected); // throws if exceeded

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
                order.returnToSelecting();

                eventRepo.store(event);
                activeOrderRepo.store(order);


                logger.log(Level.INFO, "Selection updated successfully");
                return new Response<>(new ActiveOrderDTO(order), "Selection updated successfully");

            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "Order or event not found: " + e.getMessage());
                return new Response<>(null, "Order or event not found");
            } catch (IllegalArgumentException | IllegalStateException e) {
                // thrown by quantityExceedsPolicy, bookTickets, etc.
                logger.log(Level.SEVERE, "Invalid edit: " + e.getMessage());
                return new Response<>(null, "Invalid edit: " + e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to edit selection: " + e.getMessage());
                return new Response<>(null, "Failed to edit selection: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        cleanupScheduler.shutdown();
    }

    public Response<Boolean> cancelEventQueueEntry(String token, int eventId) {
        return RetryHelper.executeWithRetry(() -> {
            if (token == null || token.isBlank()) {
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
                        removed ? "Removed from event queue" : "User was not waiting in event queue"
                );

            } catch (NoSuchElementException e) {
                return new Response<>(false, "Event not found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            }
        });
    }


    private void promoteNextInQueue(int eventId) {
        String nextToken = dequeueNextToken(eventId);
        if (nextToken != null) {
            createActiveOrderForToken(nextToken, eventId);
            NotifyPayload payload = new NotifyPayload("Your turn for event " + eventId + " has arrived!",eventId,null);
            NotifyDTO notifyDTO = new NotifyDTO(NotifyType.QUEUE_EVENT_TURN_ARRIVED,payload);
            notifier.notifyTab(nextToken, notifyDTO);
            logger.info("Notified tab " + nextToken + " that their turn for event " + eventId + " has arrived.");
        }
    }


    private String dequeueNextToken(int eventId) {
        return RetryHelper.executeWithRetry(() -> {
            try {
                Event event = eventRepo.findById(eventId);
                if (event.getEventQueue().isEmpty()
                        || activeOrderRepo.countActiveOrdersForEvent(eventId) >= capacity) {
                    return new Response<>(null, "Queue empty or event at capacity");
                }
                String nextToken = event.getEventQueue().dequeue();
                eventRepo.store(event);
                return new Response<>(nextToken, "Token dequeued successfully");
            }
            catch (OptimisticLockingFailureException e) {
                throw e;
            }
        }).getValue();
    }

    private void createActiveOrderForToken(String token, int eventId) {
        boolean skipped = RetryHelper.executeWithRetry(() -> {
            try {
                String role = auth.getRole(token).getValue();
                String userIdentifier = token;
                if (role.equals("MEMBER")) {
                    userIdentifier = auth.getUserEmail(token).getValue();
                }
                activeOrderRepo.alreadyHasActiveOrder(userIdentifier, eventId);
                int orderId = idGenerator.getAndIncrement();
                ActiveOrder nextOrder = new ActiveOrder(orderId, userIdentifier, eventId, new ArrayList<>());
                activeOrderRepo.store(nextOrder);
                return new Response<>(false, "created");

            } catch (IllegalStateException e) {
                return new Response<>(true, "duplicate");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            }
        }).getValue();

        if (skipped) {
            promoteNextInQueue(eventId);
        }
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
                new NotifyPayload("Event \"" + persisted.getName() + "\" is sold out.", eventId,null)
        );

        for (Integer userId : recipientIds) {
            try {
                notifier.notifyMemberById(userId, payload);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Sold-out notify failed for user " + userId + ": " + e.getMessage());
            }
        }
    }
}
