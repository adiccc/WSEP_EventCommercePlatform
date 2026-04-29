package application;

import domain.activeOrder.ActiveOrder;
import domain.activeOrder.IActiveOrderRepo;
import domain.company.ICompanyRepo;
import domain.dto.EventMapDTO;
import domain.event.Event;
import domain.event.EventMap;
import domain.event.EventQueue;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;
import Exception.OptimisticLockingFailureException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
    private final int capacity = 100;
    private int orderExpireMinutes = 10;

    private final int capacity;

    public ActiveOrderService(IAuth auth, IActiveOrderRepo activeOrderRepo, IEventRepo eventRepo, ICompanyRepo companyRepo, ILotteryRepo lotteryRepo, int capacity) {
        this.eventRepo = eventRepo;
        this.activeOrderRepo = activeOrderRepo;
        this.companyRepo = companyRepo;
        this.lotteryRepo = lotteryRepo;
        this.auth = auth;
        this.capacity = capacity;
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


    public Response<Integer>guestSelectTickets(String identifier, String eventId, Map<String, List<String>> seatingZones,Map<String, Integer> standingZones) {
        return RetryHelper.executeWithRetry(()->{
        logger.log(Level.INFO, "guestSelectTickets called");

        try {
            Event e = this.eventRepo.findById(eventId);
            int orderId = idGenerator.getAndIncrement();
            List<Integer> tickets = e.bookTickets(false,seatingZones,standingZones); // check here quantity and policy
            this.eventRepo.store(e);
            ActiveOrder newActiveOrder = new ActiveOrder(orderId, auth.getUserId(identifier).getValue(), eventId, tickets,orderExpireMinutes);
            // todo : schedule deletion and ticket status -> available after expiration time
            activeOrderRepo.store(newActiveOrder);
            logger.log(Level.INFO, "Tickets selected successfully");
            return new Response<>(newActiveOrder.getId(), "Tickets selected successfully");
        } catch (NoSuchElementException e) {
            logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
            return new Response<>(null, "Event not found");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to select tickets : " + e.getMessage());
            return new Response<>(null, "Failed to select tickets : " + e.getMessage());
        }

    });}

}