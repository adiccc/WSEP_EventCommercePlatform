package application;

import DTO.TicketSupplyRequestDTO;
import DTO.TicketSupplyResultDTO;

import domain.activeOrder.ActiveOrder;
import domain.activeOrder.IActiveOrderRepo;
import domain.company.ICompanyRepo;
import domain.dto.ActiveOrderDTO;
import domain.dto.EventMapDTO;
import domain.dto.SeatingTicketDTO;
import domain.dto.UserDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;
import Exception.OptimisticLockingFailureException;
import java.time.LocalDateTime;

import java.util.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;



public class ActiveOrderService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());
    private final AtomicInteger idGenerator = new AtomicInteger(1);
    private final IEventRepo eventRepo;
    private final IActiveOrderRepo activeOrderRepo;
    private final ICompanyRepo companyRepo;
    private final ILotteryRepo lotteryRepo;
    private final IAuth auth;
    private final IPaymentSystem paymentSystem;
    private final ITicketSupply ticketSupply;
    private int capacity = 100;
    private final int orderExpireMinutes;
    private final ScheduledExecutorService cleanupScheduler;


    public ActiveOrderService(
            IAuth auth,
            IActiveOrderRepo activeOrderRepo,
            IEventRepo eventRepo,
            ICompanyRepo companyRepo,
            ILotteryRepo lotteryRepo,
            IPaymentSystem paymentSystem,
            ITicketSupply ticketSupply,
            int capacity,
            int orderExpireMinutes) {
        this.eventRepo = eventRepo;
        this.activeOrderRepo = activeOrderRepo;
        this.companyRepo = companyRepo;
        this.lotteryRepo = lotteryRepo;
        this.auth = auth;
        this.paymentSystem = paymentSystem;
        this.ticketSupply = ticketSupply;
        this.capacity = capacity;
        this.orderExpireMinutes = orderExpireMinutes;
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

    public Response<EventMapDTO> enterEventPurchase(String token, int companyId, int eventId) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.log(Level.INFO, "enterEventPurchase called");
            cleanupExpiredOrders();


            // check valid token
            String role = auth.getRole(token).getValue();
            if(role == null){
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(null, "Invalid token");
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
                if (e.getSaleStartDate().isAfter(java.time.LocalDateTime.now())) {
                    logger.log(Level.SEVERE, "The sale for this event has not started yet");
                    return new Response<>(null, "The sale for this event has not started yet");
                }

                if (e.hasLottery()) {
                    Lottery l = lotteryRepo.findById(eventId);
                    int code = auth.getUserId(token).getValue(); // the code of each user who registered to the lottery is his ID because there ara no notifications in the system
                    LocalDateTime lotteryEndTime = e.getSaleStartDate().plusHours(l.getExpirationTime());
                    if (!l.getWinners().contains(code)) {
                        if (LocalDateTime.now().isBefore(lotteryEndTime))
                            return new Response<>(null, "User is not a lottery winner and lottery registration is still open");
                    }
                }
                boolean acquired = e.tryAcquirePurchaseSlot(capacity);
                if (!acquired) {
                    if (e.getEventQueue().contains(token)) {
                        int position = e.getEventQueue().position(token);
                        return new Response<>(null,
                                "User is still waiting in queue. Position: " + position);
                    }
                    e.getEventQueue().enqueue(token);
                    int position = e.getEventQueue().position(token);
                    eventRepo.store(e); // persist the updated event with the new queue state
                    return new Response<>(null,
                            "Event is full, user added to waiting queue. Position: " + position);
                }
                eventRepo.store(e); // persist the updated event with the new queue state

                logger.log(Level.INFO, "Event map retrieved successfully");
                return new Response<>(new EventMapDTO(e.getMap()), "Event map retrieved successfully");
            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                return new Response<>(null, "Event not found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to enter event purchase : " + e.getMessage());
                return new Response<>(null, "Failed to enter event purchase  : " + e.getMessage());
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
        if(identifier == null){
            logger.log(Level.SEVERE, "identifier is null");
            return new Response<>(null, "Invalid identifier supplied");
        }
        String role = auth.getRole(identifier).getValue();
        try {
            this.activeOrderRepo.alreadyHasActiveOrder(auth.getUserId(identifier).getValue(), eventId);
            int totalSeatingTickets = seatingZones.values().stream()
                    .mapToInt(List::size)
                    .sum();

            int totalStandingTickets = standingZones.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            int totalTickets = totalSeatingTickets + totalStandingTickets;
            Event e = this.eventRepo.findById(eventId);
            //todo: handle user/ guest
            Response<UserDTO> userResponse = auth.getUserDTO(identifier);
            e.quantityExceedsPolicy(userResponse.getValue(), totalTickets);
            int orderId = idGenerator.getAndIncrement();
            List<Integer> tickets = e.bookTickets(seatingZones,standingZones); // check here quantity and policy
            this.eventRepo.store(e);
            ActiveOrder newActiveOrder = new ActiveOrder(orderId, auth.getUserId(identifier).getValue(), eventId, tickets,orderExpireMinutes);
            activeOrderRepo.store(newActiveOrder);
            logger.log(Level.INFO, "Tickets selected successfully");
            newActiveOrder.proceedToCheckout();
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
                        return new Response<>(true, "expired");
                    } catch (OptimisticLockingFailureException e) {
                        throw e;
                     }
                    catch (NoSuchElementException e) {
                        // order already gone — user placed it before cleanup hit. Fine.
                        return new Response<>(true, "already removed");
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
                ActiveOrderDTO order = activeOrderRepo.findOrderByUserId(userId);
                if (order.getUserId() != auth.getUserId(token).getValue()) {
                    logger.log(Level.SEVERE, "Unauthorized access to active order");
                    return new Response<>(null, "Unauthorized access to active order");
                }
                if (order.getExpireTime().isBefore(LocalDateTime.now())) {
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
                int userId = auth.getUserId(token).getValue();
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
                ActiveOrderDTO dto = activeOrderRepo.findOrderByUserId(userId);
                ActiveOrder order = activeOrderRepo.findById(dto.getId());

                if (order.getExpireTime().isBefore(LocalDateTime.now())) {
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
                event.quantityExceedsPolicy(userDTO, projected); // throws if exceeded

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

    //TODO : this implementation is for test only, this function should be implemented currectly

    public Response<Boolean> placeOrder(String token, Integer eventId, int orderId) {
        Event event =eventRepo.findById(eventId);
        int userId=auth.getUserId(token).getValue();
        event.placeOrder(userId,orderId);
        eventRepo.store(event);
        return new Response<>(true, "Order placed successfully");
    }
}
