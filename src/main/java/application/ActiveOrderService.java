package application;

import domain.activeOrder.IActiveOrderRepo;
import domain.company.ICompanyRepo;
import domain.event.Event;
import domain.event.EventMap;
import domain.event.IEventRepo;
import domain.event.IOrderRepo;
import domain.lottery.ILotteryRepo;

import java.util.Map;
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
    private final int capacity = 100; // here? רף שהוגדר בזמן יצירת המערכת

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
        if (!auth.isLoggedIn(token)) {
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

            //add hash map to event and its active order
            while(activeOrderRepo.getAll().stream()
                    .filter(order -> order.getEventId().equals(eventId)).count() >= capacity) {
                //TODO: add to queue
            }
            if (e.hasLottery()){
                //TODO: enter lottery code
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