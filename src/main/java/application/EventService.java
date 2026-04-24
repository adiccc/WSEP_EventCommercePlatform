package application;

import domain.event.Event;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class EventService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final TokenService tokenService;
    private final IEventRepo eventRepo;

    public EventService(TokenService tokenService, IEventRepo eventRepo) {
        this.tokenService = tokenService;
        this.eventRepo = eventRepo;
    }

    public Response<Event> ViewEventDetails(String token, int companyId, String eventId) {
        logger.log(Level.INFO, "ViewEventDetails called");

        // check valid token
        if (!tokenService.validateToken(token)) {
            return new Response<>(null, "Invalid token");
        }
        if (eventId == null || eventId.isEmpty()) {
            return new Response<>(null, "Invalid event ID");
        }
        try {
            Event e = this.eventRepo.findById(eventId);
            if (e.getCompanyId() != companyId) {
                return new Response<>(null, "The selected event does not belong to the company");
            }
            if (!e.isActive()) {
                return new Response<>(null, "The selected event is not active");
            }
            logger.log(Level.INFO, "Event details retrieved successfully");
            return new Response<>(e, "Event details retrieved successfully");
        } catch (NoSuchElementException e) {
            logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
            return new Response<>(null, "Event not found");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to return event details : " + e.getMessage());
            return new Response<>(null, "Failed to return event details : " + e.getMessage());
        }
    }

}