package application;

import DTO.TicketSupplyRequestDTO;
import DTO.TicketSupplyResultDTO;
import domain.activeOrder.IActiveOrderRepo;
import domain.company.ICompanyRepo;
import domain.dto.EventMapDTO;
import domain.event.*;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;
import Exception.OptimisticLockingFailureException;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;


import static domain.config.PurchaseConfig.MAX_ACTIVE_ORDERS_PER_EVENT;

public class ActiveOrderService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final IEventRepo eventRepo;
    private final IActiveOrderRepo activeOrderRepo;
    private final ICompanyRepo companyRepo;
    private final ILotteryRepo lotteryRepo;
    private final IAuth auth;
    private final IPaymentSystem paymentSystem;
    private final ITicketSupply ticketSupply;
    private final int capacity;

    public ActiveOrderService(
            IAuth auth,
            IActiveOrderRepo activeOrderRepo,
            IEventRepo eventRepo,
            ICompanyRepo companyRepo,
            ILotteryRepo lotteryRepo,
            IPaymentSystem paymentSystem,
            ITicketSupply ticketSupply,
            int capacity) {
        this.eventRepo = eventRepo;
        this.activeOrderRepo = activeOrderRepo;
        this.companyRepo = companyRepo;
        this.lotteryRepo = lotteryRepo;
        this.auth = auth;
        this.paymentSystem = paymentSystem;
        this.ticketSupply = ticketSupply;
        this.capacity = capacity;
       // this.capacity = MAX_ACTIVE_ORDERS_PER_EVENT;
    }

    public Response<EventMapDTO> enterEventPurchase(String token, int companyId, String eventId) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.log(Level.INFO, "enterEventPurchase called");

            // check valid token
            if (!auth.isLoggedIn(token).getValue()) {
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
//    public Response<Integer> checkoutAndPayment(
//            String token,
//            String eventId,
//            int activeOrderId,
//            PaymentDetailsDTO paymentDetails) {
//
//        return RetryHelper.executeWithRetry(() -> {
//            logger.log(Level.INFO, "checkoutAndPayment called");
//
//            int userId = auth.getUserId(token).getValue();
//            if (userId == -1) {
//                return new Response<>(null, "Invalid token");
//            }
//
//            try {
//                Event event = eventRepo.findById(eventId);
//
//                if (!event.isActive()) {
//                    return new Response<>(null, "Event is not active");
//                }
//
//                ActiveOrder activeOrder = activeOrderRepo.findById(activeOrderId);
//
//                if (activeOrder.getUserId() != userId) {
//                    return new Response<>(null, "Active order does not belong to user");
//                }
//
//                if (!activeOrder.getEventId().equals(eventId)) {
//                    return new Response<>(null, "Active order does not belong to event");
//                }
//
//                if (!activeOrder.hasTickets()) {
//                    return new Response<>(null, "Active order has no selected tickets");
//                }
//
//                // TODO: replace with real total calculation according to selected tickets,
//                // event zones, discounts, and company/event policies.
//                // TODO: implement Event.calculatePrice(...) using event/company discount policy.
//                // double total = event.calculatePrice(activeOrder.getTickets());
//
////                String paymentConfirmationId = paymentSystem.pay(total, paymentDetails);
//
////                if (paymentConfirmationId == null || paymentConfirmationId.isBlank()) {
////                    logger.log(Level.SEVERE, "Payment rejected");
////                    return new Response<>(null, "Payment rejected");
////                }
//
//                Order order = new Order(
//                        activeOrder.getId(),
//                        userId,
//                        eventId,
//                        activeOrder.getTickets(),
//                        total,
//                        paymentConfirmationId
//                );
//
////                event.getOrders().add(order);
//
//                // TODO: mark selected tickets as SOLD when ticket locking/supply logic is ready.
//                activeOrderRepo.delete(activeOrderId);
//
//                eventRepo.store(event);
//
//                // TODO: trigger Ticket Issuance UC using ticketSupply.issue(...)
//                // Do not make checkout response depend on ticket issuance yet.
//
//                // TODO: notify queue/active-order manager that a purchase slot (ao) was released.
//
//                logger.log(Level.INFO, "Purchase completed successfully");
//                return new Response<>(order.getOrderId(), "Purchase completed successfully");
//
//            } catch (NoSuchElementException e) {
//                logger.log(Level.SEVERE, "Event or active order not found: " + e.getMessage());
//                return new Response<>(null, "Event or active order not found");
//
//            } catch (Exception e) {
//                logger.log(Level.SEVERE, "Failed to complete purchase: " + e.getMessage());
//                return new Response<>(null, "Failed to complete purchase: " + e.getMessage());
//            }
//        });
//    }


    public Response<TicketSupplyResultDTO> issueTickets(TicketSupplyRequestDTO request) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "issueTickets called");

            if (request == null) {
                return new Response<>(null, "Invalid ticket supply request");
            }

            TicketSupplyResultDTO result = ticketSupply.issue(request);

            if (result == null || !result.isSuccess()) {

                return new Response<>(result, "Ticket issuance failed");
            }

            return new Response<>(result, "Tickets issued successfully");
        });
    }

    //TODO : this implementation is for test only, this function should be implemented currectly

    public Response<Boolean> placeOrder(String token, String eventId, int orderId) {
        Event event =eventRepo.findById(eventId);
        int userId=auth.getUserId(token).getValue();
        event.placeOrder(userId,orderId);
        eventRepo.store(event);
        return new Response<>(true, "Order placed successfully");
    }
}