package application;

import domain.dataType.EventSearchFilter;
import domain.dto.EventDTO;
import domain.dto.EventDetailsDTO;
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

                        // Search by price range (min and max price)
                        .filter(e -> {
                            if (filter.getMinPrice() == null && filter.getMaxPrice() == null)
                                return true;

                            return e.getMap().getZones().stream().anyMatch(z -> {
                                double price = z.getPrice();
                                if (filter.getMinPrice() != null && price < filter.getMinPrice())
                                    return false;
                                if (filter.getMaxPrice() != null && price > filter.getMaxPrice())
                                    return false;
                                return true;
                            });
                        }).toList();

                if (result.isEmpty()) {
                    logger.log(Level.INFO, "No matching events found");
                    return new Response<List<EventDTO>>(null, "No matching events found");
                }
                List<EventDTO> eventDTOs = result.stream().map(EventDTO::new).collect(Collectors.toList());
                logger.log(Level.INFO, "Events retrieved successfully");
                return new Response<>(eventDTOs, "Events retrieved successfully");

            } catch (Exception e) {
                logger.severe("Search failed: " + e.getMessage());
                return new Response<>(null, "Search failed");
            }
        });

    }

}