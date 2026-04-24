package application;

import domain.dataType.EventSearchFilter;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.dataType.Zone;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


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

    public Response<List<Event>> searchEvents(String token, EventSearchFilter filter) {
        logger.info("Search events called");

        // token validation
        if (!tokenService.validateToken(token)) {
            return new Response<>(null, "Invalid token");
        }

        if (filter == null) {
            return new Response<>(null, "Invalid search input");
        }

        try {
            List<Event> events = eventRepo.getAll();

            List<Event> result = events.stream()
                    .filter(Event::isActive) //Only active events
                    .filter(e -> e.getDate().isAfter(LocalDateTime.now())) //Only future events
                    //Search by name
                    .filter(e -> {
                        if (filter.getKeyword() == null || filter.getKeyword().isEmpty())
                            return true;
                        return e.getName().toLowerCase()
                                .contains(filter.getKeyword().toLowerCase());
                    })

                    // Search by date range
                    .filter(e -> {
                        if (filter.getStartDate() == null) return true;
                        return !e.getDate().isBefore(filter.startDate);
                    })
                    .filter(e -> {
                        if (filter.getEndDate() == null) return true;
                        return !e.getDate().isAfter(filter.getEndDate());
                    })

                    // Search by category (enum)
                    .filter(e -> {
                        if (filter.getCategory() == null) return true;
                        return e.getCategoryEvent() == filter.getCategory();
                    })

                    // Search by location (enum)
                    .filter(e -> {
                        if (filter.getLocation() == null) return true;
                        return e.getLocation() == filter.getLocation();
                    })

                    // Search by price range (min and max price) - we will use the minimum price of the event's map for filtering
                    .filter(e -> {
                        if (filter.getMinPrice() == null && filter.getMaxPrice() == null)
                            return true;
                        double minEventPrice = getMinPrice(e);
                        if (filter.getMinPrice() != null && minEventPrice < filter.getMinPrice())
                            return false;
                        if (filter.getMaxPrice() != null && minEventPrice > filter.getMaxPrice())
                            return false;
                        return true;
                    })
                    .toList();

            if (result.isEmpty()) {
                return new Response<>(null, "No matching events found");
            }
            return new Response<>(result, "Events retrieved successfully");

        } catch (Exception e) {
            logger.severe("Search failed: " + e.getMessage());
            return new Response<>(null, "Search failed");
        }

    }

    // Helper method to get the minimum price of an event's map for price filtering
    private double getMinPrice(Event event) {
        return event.getMap().getZones().stream()
                .mapToDouble(Zone::getPrice)
                .min()
                .orElse(Double.MAX_VALUE);
    }

}