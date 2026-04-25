package application;

import domain.activeOrder.IActiveOrderRepo;
import domain.company.ICompanyRepo;
import domain.event.Event;
import domain.event.EventMap;
import domain.event.EventQueue;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ActiveOrderService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final IEventRepo eventRepo;
    private final IActiveOrderRepo activeOrderRepo;
    private final ICompanyRepo companyRepo;
    private final ILotteryRepo lotteryRepo;
    private final IAuth auth;
    private final int capacity = 100;

    public ActiveOrderService(IAuth auth, IActiveOrderRepo activeOrderRepo, IEventRepo eventRepo, ICompanyRepo companyRepo, ILotteryRepo lotteryRepo) {
        this.eventRepo = eventRepo;
        this.activeOrderRepo = activeOrderRepo;
        this.companyRepo = companyRepo;
        this.lotteryRepo = lotteryRepo;
        this.auth = auth;
    }

    public Response<EventMap> enterEventPurchase(String token, int companyId, String eventId) {
        logger.log(Level.INFO, "enterEventPurchase called");

        // check valid token
        if (!auth.isLoggedIn(token).getValue()) {
            return new Response<>(null, "Invalid token");
        }
        try {
            Event e = this.eventRepo.findById(eventId);

            if (e.getCompanyId() != companyId) {
                return new Response<>(null, "The selected event does not belong to the company");
            }
            if (!e.isActive()) {
                return new Response<>(null, "The selected event is not active");
            }
            if (e.getSaleStartDate().isAfter(java.time.LocalDateTime.now())) {
                return new Response<>(null, "The sale for this event has not started yet");
            }

            long activeOrdersCount = activeOrderRepo.getAll().stream()
                    .filter(order -> order.getEventId().equals(eventId))
                    .count();

            if (activeOrdersCount >= capacity) {
                EventQueue queue = e.getEventQueue();

                if (!queue.contains(token)) {
                    queue.enqueue(token);
                    return new Response<>(null, "Event is full, user added to waiting queue");
                }

                if (!queue.isFirst(token)) {
                    return new Response<>(null, "User is still waiting in queue");
                }
            }

            if (e.hasLottery()){
                Lottery l = lotteryRepo.findById(eventId);
                int code = auth.getUserId(token).getValue(); // the code of each user who registered to the lottery is his ID because there ara no notifications in the system
                LocalDateTime lotteryEndTime  = e.getSaleStartDate().plusHours(l.getExpirationTime());
                if (!l.getWinners().contains(code)) {
                    if (LocalDateTime.now().isBefore(lotteryEndTime))
                        return new Response<>(null, "User is not a lottery winner and lottery registration is still open");
                }
            }
            logger.log(Level.INFO, "Event map retrieved successfully");
            return new Response<>(e.getMap(), "Event map retrieved successfully");
        } catch (NoSuchElementException e) {
            logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
            return new Response<>(null, "Event not found");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enter event purchase : " + e.getMessage());
            return new Response<>(null, "Failed to enter event purchase  : " + e.getMessage());
        }
    }
}