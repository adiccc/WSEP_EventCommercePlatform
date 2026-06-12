package application;

import DTO.NotifyDTO;
import DTO.NotifyPayload;
import DTO.NotifyType;
import domain.dataType.EventSearchFilter;
import domain.dto.EventDTO;
import domain.dto.EventDetailsDTO;
import domain.event.Event;
import domain.event.IEventRepo;

import java.time.LocalDateTime;
import java.util.List;
import Exception.OptimisticLockingFailureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class EventService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final IEventRepo eventRepo;
    private final IAuth auth;
    private final INotifier notifier;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public EventService(IAuth auth, IEventRepo eventRepo, INotifier notifier, TransactionTemplate transactionTemplate) {
        this.eventRepo = eventRepo;
        this.auth = auth;
        this.notifier = notifier;
        this.transactionTemplate = transactionTemplate;
    }

    public Response<EventDetailsDTO> ViewEventDetails(String token, int companyId, Integer eventId) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "ViewEventDetails called");

            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(null, "Invalid token");
            }

            if (eventId == null) {
                return new Response<>(null, "Invalid event ID");
            }

            return transactionTemplate.execute(status -> {
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
                    status.setRollbackOnly();
                    logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                    return new Response<>(null, "Event not found");

                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;

                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.log(Level.SEVERE, "Failed to return event details : " + e.getMessage());
                    return new Response<>(null, "Failed to return event details : " + e.getMessage());
                }
            });
        });
    }

    public Response<List<EventDTO>> searchEvents(String token, EventSearchFilter filter) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("Search events called");

            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(null, "Invalid token");
            }

            if (filter == null) {
                return new Response<>(null, "Invalid search input");
            }

            return transactionTemplate.execute(status -> {
                try {
                    List<Event> events = eventRepo.getAll();

                    List<EventDTO> result = events.stream()
                            .filter(e -> e.matches(filter))
                            .map(EventDTO::new)
                            .collect(Collectors.toList());

                    if (result.isEmpty()) {
                        logger.log(Level.INFO, "No matching events found");
                        return new Response<>(null, "No matching events found");
                    }
                    logger.log(Level.INFO, "Events retrieved successfully");
                    return new Response<>(result, "Events retrieved successfully");

                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;

                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Search failed: " + e.getMessage());
                    return new Response<>(null, "Search failed");
                }
            });
        });
    }

    public Response<List<EventDTO>> searchCompanyEvents(String token, int companyId, EventSearchFilter filter) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("Search company events called");

            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(null, "Invalid token");
            }

            if (filter == null) {
                return new Response<>(null, "Invalid search input");
            }

            return transactionTemplate.execute(status -> {
                try {
                    List<Event> events = eventRepo.findByCompany(companyId);

                    List<EventDTO> result = events.stream()
                            .filter(e -> e.matches(filter))
                            .map(EventDTO::new)
                            .collect(Collectors.toList());

                    if (result.isEmpty()) {
                        logger.log(Level.INFO, "No matching events found in the company");
                        return new Response<>(null, "No matching events found in the company");
                    }

                    logger.log(Level.INFO, "Events retrieved successfully");
                    return new Response<>(result, "Events retrieved successfully");

                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;

                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Search failed: " + e.getMessage());
                    return new Response<>(null, "Search failed");
                }
            });
        });
    }
    
    private void notifyTokenExpired(String token) {
        try {
            NotifyPayload payload = new NotifyPayload("Your session has expired");
            NotifyDTO expiredNotify = new NotifyDTO(NotifyType.TOKEN_EXPIRED, payload);
            notifier.notifyTab(token, expiredNotify);
        } catch (Exception e) { logger.warning("Notify failed"); }
    }

    private String getValidatedRole(String token) {
        String role = auth.getRole(token).getValue();
        if (role == null) {
            notifyTokenExpired(token);
            return null;
        }
        return role;
    }
}