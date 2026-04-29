package application;

import domain.dataType.EventSearchFilter;
import domain.dto.EventDTO;
import domain.dto.EventDetailsDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import java.time.LocalDateTime;
import java.util.List;
import Exception.OptimisticLockingFailureException;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class EventService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final IEventRepo eventRepo;
    private final IAuth auth;

    public EventService(IAuth auth, IEventRepo eventRepo) {
        this.eventRepo = eventRepo;
        this.auth = auth;
    }

    public Response<EventDetailsDTO> ViewEventDetails(String token, int companyId, String eventId) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.log(Level.INFO, "ViewEventDetails called");

            // check valid token
            if (!auth.isLoggedIn(token).getValue()) {
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
                return new Response<>(new EventDetailsDTO(e), "Event details retrieved successfully");
            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                return new Response<>(null, "Event not found");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to return event details : " + e.getMessage());
                return new Response<>(null, "Failed to return event details : " + e.getMessage());
            }
        });
    }

    public Response<List<EventDTO>> searchEvents(String token, EventSearchFilter filter) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("Search events called");

            // token validation
            if (!auth.isLoggedIn(token).getValue()) {
                return new Response<>(null, "Invalid token");
            }

            if (filter == null) {
                return new Response<>(null, "Invalid search input");
            }

            try {
                List<Event> events = eventRepo.getAll();

                List<EventDTO> result = events.stream()
                        .filter(e -> e.matches(filter))
                        .map(EventDTO::new)
                        .collect(Collectors.toList());

                if (result.isEmpty()) {
                    logger.log(Level.INFO, "No matching events found");
                    return new Response(null, "No matching events found");
                }
                logger.log(Level.INFO, "Events retrieved successfully");
                return new Response<>(result, "Events retrieved successfully");

            } catch (Exception e) {
                logger.severe("Search failed: " + e.getMessage());
                return new Response<>(null, "Search failed");
            }
        });
    }

    public Response<List<EventDTO>> searchCompanyEvents(String token, int companyId, EventSearchFilter filter) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("Search company events called");

            // token validation
            if (!auth.isLoggedIn(token).getValue()) {
                return new Response<>(null, "Invalid token");
            }

            if (filter == null) {
                return new Response<>(null, "Invalid search input");
            }

            try {
                List<Event> events = eventRepo.findByCompany(companyId);

                List<EventDTO> result = events.stream()
                        .filter(e -> e.matches(filter))
                        .map(EventDTO::new)
                        .collect(Collectors.toList());

                if (result.isEmpty()) {
                    logger.log(Level.INFO, "No matching events found in the company");
                    return new Response<List<EventDTO>>(null, "No matching events found in the company");
                }
                logger.log(Level.INFO, "Events retrieved successfully");
                return new Response<>(result, "Events retrieved successfully");

            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Search failed: " + e.getMessage());
                return new Response<>(null, "Search failed");
            }
        });
    }
}