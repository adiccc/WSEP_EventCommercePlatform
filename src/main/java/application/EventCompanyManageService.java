package application;

import DTO.*;
import domain.Suspension.ISuspensionRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dataType.*;
import domain.dto.*;
import domain.policy.DiscountPolicyType;
import domain.policy.PurchasePolicyType;
import domain.event.*;
import Exception.OptimisticLockingFailureException;
import domain.user.*;
import domain.user.IUserRepo;
import domain.user.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

import java.util.List;
import java.util.NoSuchElementException;

import static DTO.NotifyType.GENERAL_POPUP;
import static domain.dataType.PermissionType.*;

@Service
public class EventCompanyManageService {
    private final ICompanyRepo companyRepo;
    private final IEventRepo eventRepo;
    private final Logger logger;
    private final IAuth auth;
    private final IPaymentSystem paymentSystem;
    private final ISuspensionRepo suspensionRepo;
    private final INotifier notifier;
    private final IUserRepo userRepo;
    AtomicInteger ticketIdGenerator = new AtomicInteger(1);
    private final TransactionTemplate transactionTemplate;



    @Autowired
    public EventCompanyManageService(ICompanyRepo companyRepo, IEventRepo eventRepo, IAuth auth, IPaymentSystem paymentSystem, ISuspensionRepo suspensionRepo,INotifier notifier, IUserRepo userRepo,TransactionTemplate transactionTemplate) {
        this.companyRepo = companyRepo;
        this.eventRepo = eventRepo;
        this.auth = auth;
        this.logger = Logger.getLogger(EventCompanyManageService.class.getName());
        this.paymentSystem = paymentSystem;
        this.suspensionRepo = suspensionRepo;
        this.notifier = notifier;
        this.userRepo = userRepo;
        this.transactionTemplate = transactionTemplate;
    }

    public Response<Boolean> DefineVenueAndSeatingMap(String token, Integer eventId, ElementPositionDTO stage,
            List<ElementPositionDTO> entries, List<StandingZoneDTO> standingZone, List<SeatingZoneDTO> seatingZone) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.log(Level.INFO, "DefineVenueAndSeatingMap called");
            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid or expired token");
                return new Response<>(false, "Invalid or expired token");
            }
            // check valid token
            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("Invalid token");
                return new Response<>(false, "Only members can define venue");
            }
            if (suspensionRepo.haveActiveSuspension(userId)) {
                logger.severe("User does not have write access caused by suspension");
                return new Response<>(null, "user does not have write access caused by suspension.");
            }
            try {
                Event event = eventRepo.findById(eventId);
                int companyId = event.getCompanyId();
                int eventCreator = event.getCreatorId();
                Company c = this.companyRepo.findById(companyId);

                if(!c.isActive()){
                    logger.severe("Company is closed, cannot define venue and seating map for events of this company");
                    return new Response<>(false, "Company is closed, cannot define venue and seating map for events of this company");
                }

                // check appropriate permission
                if (userId != eventCreator || !c.getCompanyPermission().checkPermission(userId, CREATE_EVENT)) {
                    logger.severe("User does not have permission to define venue and seating map for this event");
                    return new Response<>(false, "Permission required");
                }

                // create map
                if (stage == null || entries == null || (standingZone == null && seatingZone == null)) {
                    logger.severe("Map element is null");
                    return new Response<>(false, "map element null");
                }
                List<Zone> zones = new ArrayList<>();
                for (StandingZoneDTO standingZoneDTO : standingZone) {
                    zones.add(new StandingZone(standingZoneDTO));
                }
                for (SeatingZoneDTO seatingZoneDTO : seatingZone) {
                    zones.add(new SeatingZone(seatingZoneDTO));
                }
                List<ElementPosition> allEntries = new ArrayList<>();
                for (ElementPositionDTO elementPositionDTO : entries) {
                    allEntries.add(new ElementPosition(elementPositionDTO.getX(), elementPositionDTO.getY()));
                }
                EventMap eventMap = new EventMap(new ElementPosition(stage.getX(), stage.getY()), allEntries, zones);
                event.setMap(eventMap);
                if (!event.hasLottery()) {
                    event.setActive(true);
                }
                eventRepo.store(event);
                // success
                logger.log(Level.INFO, "map created and linked to event " + eventId);
                return new Response<>(true, "map saved successfully");

            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "event not found: " + e.getMessage());
                return new Response<>(false, "Event not found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "failed creating a map : " + e.getMessage());
                return new Response<>(false, "failed to create map : " + e.getMessage());
            }
        });

    }

    public Response<Integer> createEvent(String token, int companyId, LocalDateTime date, String name,
            LocalDateTime saleStartDate, boolean hasLottery, GeographicalArea location, CategoryEvent category) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.log(Level.INFO, "createEvent called");
            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid or expired token");
                return new Response<>(null, "Invalid or expired token");
            }
            // check valid token
            int creatorId = getUserIdFromToken(token);
            if (creatorId == -1) {
                logger.severe("Only members can create events");
                return new Response<>(null, "Only members can create events");
            }
            if (suspensionRepo.haveActiveSuspension(creatorId)) {
                logger.severe("User does not have write access caused by suspension");
                return new Response<>(null, "user does not have write access caused by suspension.");
            }

            try {
                Company c = this.companyRepo.findById(companyId);

                if(!c.isActive()){
                    logger.severe("Company is closed, cannot define venue and seating map for events of this company");
                    return new Response<>(null, "Company is closed, cannot define venue and seating map for events of this company");
                }

                if (!c.getCompanyPermission().checkPermission(creatorId, CREATE_EVENT)) {
                    logger.severe("User does not have permission to create event for this company");
                    return new Response<>(null, "Permission required");
                }
                if (date.isBefore(LocalDateTime.now())) {
                    logger.severe("Event date must be in the future");
                    return new Response<>(null, "Event date must be in the future");
                }
                if (saleStartDate.isAfter(date)) {
                    logger.severe("Sale start date must be before event date");
                    return new Response<>(null, "Sale start date must be before event date");
                }

                Event event = new Event(
                        companyId,
                        creatorId,
                        date,
                        name,
                        saleStartDate,
                        hasLottery,
                        location,
                        category);
                eventRepo.store(event);
                logger.log(Level.INFO, "Event created successfully");
                return new Response<>(event.getId(), "Event created successfully");
            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "company not found: " + e.getMessage());
                return new Response<>(null, "Company not found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "failed creating event : " + e.getMessage());
                return new Response<>(null, "failed to create event : " + e.getMessage());
            }
        });
    }
    public Response<Boolean> UpdateEventDate(String token, Integer eventId, LocalDateTime date) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.log(Level.INFO, "UpdateEventDate called");
            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(false, "Invalid token");
            }
            // check valid token
            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("Only members can update event's dates");
                return new Response<>(false, "Only members can update event's dates");
            }
            if (suspensionRepo.haveActiveSuspension(userId)) {
                logger.severe("User does not have write access caused by suspension");
                return new Response<>(null, "user does not have write access caused by suspension.");
            }
            try {
                Event event = eventRepo.findById(eventId);
                Company company = companyRepo.findById(event.getCompanyId());
                if (!company.checkPermission(userId, CREATE_EVENT)) {
                    logger.severe("User does not have permission to update event date");
                    return new Response<>(false, "User id mismatch to the creator of the event");
                }
                if (date.isBefore(event.getDate())) {
                    logger.severe("Event date can only be after the original date");
                    return new Response<>(false, "Event date can only be after the original date");
                }
                if (event.getDate().isBefore(LocalDateTime.now())) {
                    logger.severe("Event date must be in the future");
                    return new Response<>(false, "Event date must be in the future");
                }

                event.setDate(date);
                eventRepo.store(event);
                logger.log(Level.INFO, "Event updated successfully");
                    List<String> purchasers = eventRepo.getAllEventPurchasers(eventId);
                    NotifyPayload payload = new NotifyPayload("The Date of event " + event.getName() + " has been updated to " + event.getDate().toString(), eventId, null);
                    NotifyDTO notifyDTO = new NotifyDTO(GENERAL_POPUP, payload);
                    for (String purchaser : purchasers) {
                        sendOrSaveNotification(purchaser, notifyDTO);
                    }
                    return new Response<>(true, "Event updated successfully");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "failed creating event : " + e.getMessage());
                return new Response<>(false, "failed to create event : " + e.getMessage());
            }
        });

    }

    // adding new zones to an existing map of an event
    public Response<Boolean> AddZonesToEventMap(String token, Integer eventId, List<StandingZoneDTO> standingZone,
            List<SeatingZoneDTO> seatingZone) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.log(Level.INFO, "AddZonesToEventMap called");
            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(false, "Invalid token");
            }
            // check valid token
            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("Only members can add zones to events map");
                return new Response<>(false, "Only members can add zones to events map");
            }
            if (suspensionRepo.haveActiveSuspension(userId)) {
                logger.severe("User does not have write access caused by suspension");
                return new Response<>(null, "user does not have write access caused by suspension.");
            }

            try {
                Event event = eventRepo.findById(eventId);
                int eventCreator = event.getCreatorId();

                // check appropriate permission
                Company company = companyRepo.findById(event.getCompanyId());
                if (!company.checkPermission(userId, CREATE_EVENT)) {
                    logger.severe("User does not have permission to add zones to event map");
                    return new Response<>(false, "Permission required");
                }

                EventMap map = event.getMap();
                if (map == null) {
                    logger.severe("Event map not defined yet");
                    return new Response<>(false, "Event map not defined yet");
                }

                if (!event.isActive()) {
                    logger.severe("Event is not active yet, cannot add zones");
                    return new Response<>(false, "Event is not active yet, cannot add zones");
                }

                boolean hasStanding = standingZone != null && !standingZone.isEmpty();
                boolean hasSeating = seatingZone != null && !seatingZone.isEmpty();

                if (!hasStanding && !hasSeating) {
                    logger.severe("No zones provided to add");
                    return new Response<>(false, "No zones provided to add");
                }

                List<Zone> zones = map.getZones();
                if (hasStanding) {
                    for (StandingZoneDTO standingZoneDTO : standingZone) {
                        zones.add(new StandingZone(standingZoneDTO));
                    }
                }

                if (hasSeating) {
                    for (SeatingZoneDTO seatingZoneDTO : seatingZone) {
                        zones.add(new SeatingZone(seatingZoneDTO));
                    }
                }

                eventRepo.store(event);
                logger.log(Level.INFO, "Zones added to event map successfully");
                return new Response<>(true, "Zones added to event map successfully");

            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "event not found: " + e.getMessage());
                return new Response<>(false, "Event not found");

            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "failed adding zones to event map : " + e.getMessage());
                return new Response<>(false, "failed to add zones to event map : " + e.getMessage());
            }
        });
    }

    public Response<EventMapDTO> getEventMapForManagement(String token, Integer eventId) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "getEventMapForManagement called");

            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(null, "Invalid token");
            }

            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("Only members can view event map for management");
                return new Response<>(null, "Only members can view event map for management");
            }

            if (suspensionRepo.haveActiveSuspension(userId)) {
                logger.severe("User does not have write access caused by suspension");
                return new Response<>(null, "user does not have write access caused by suspension.");
            }

            try {
                Event event = eventRepo.findById(eventId);
                Company company = companyRepo.findById(event.getCompanyId());

                if (!company.checkPermission(userId, CREATE_EVENT)) {
                    logger.severe("User does not have permission to view event map for management");
                    return new Response<>(null, "Permission required");
                }

                EventMap map = event.getMap();

                if (map == null) {
                    logger.severe("Event map not defined yet");
                    return new Response<>(null, "Event map not defined yet");
                }

                return new Response<>(new EventMapDTO(map), "Event map retrieved successfully");

            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "event not found: " + e.getMessage());
                return new Response<>(null, "Event not found");

            } catch (OptimisticLockingFailureException e) {
                throw e;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "failed retrieving event map : " + e.getMessage());
                return new Response<>(null, "failed to retrieve event map : " + e.getMessage());
            }
        });
    }

    public Response<Boolean> DeleteEvent(String token, Integer eventId) {
        return RetryHelper.executeWithRetry(()->{
            logger.log(Level.INFO, "DeleteEvent called");
            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(false, "Invalid token");
            }
            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("Only members can delete events");
                return new Response<>(false, "Only members can delete events");
            }
            if (suspensionRepo.haveActiveSuspension(userId)) {
                logger.severe("User does not have write access caused by suspension");
                return new Response<>(null, "user does not have write access caused by suspension.");
            }
            try{
                Event event = eventRepo.findById(eventId);
                if (!event.isActive()){
                    logger.severe("Event is not active yet, cannot be deleted");
                    return new Response<>(false, "Event is not active yet, cannot be deleted");
                }
                if(event.getDate().isBefore(LocalDateTime.now())) {
                    logger.severe("Event deletion can be on future events only");
                    return new Response<>(false, "Event deletion can be on future events only");
                }
                int companyId= event.getCompanyId();
                Company company = companyRepo.findById(companyId);
                if(!company.checkPermission(userId,DELETE_EVENT)){
                    logger.severe("User does not have permission to delete event");
                    return new Response<>(false, "User does not have permission to delete event");
                }
                event.setActive(false);
                // not in version 1 - send notification to all users who bought tickets to the event
                List<Order> orders = event.getOrders();

                for(Order order : orders){
                    order.markRefundRequired();
                }
                eventRepo.store(event);
                event=eventRepo.findById(eventId);
                orders = event.getOrders();
                for(Order order : orders){
                    try {
                        String purchaserIdentifier = order.getUserIdentifier();
                        NotifyPayload payload = new NotifyPayload("Event " + eventId + "cancelled", eventId, null);
                        NotifyDTO notifyDTO = new NotifyDTO(GENERAL_POPUP,payload);
                        sendOrSaveNotification(purchaserIdentifier,notifyDTO);
                        processRefund(token, event.getId(), order.getOrderId());
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to process automatic refund for order " +
                                order.getOrderId() + " in event " + event.getId() + ": " + e.getMessage());
                    }
                }
                logger.log(Level.INFO, "Orders deleted successfully");
                return new Response<>(true, "Orders deleted successfully");
            }catch (OptimisticLockingFailureException e) {
                throw e;
            } catch(Exception e){
                logger.log(Level.SEVERE, "failed delete event : " + e.getMessage());
                return new Response<>(false, "failed to detele event : " + e.getMessage());
            }
        });
    }

    public Response<List<OrderDTO>> getOrdersByCompany(String token, int companyId) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.log(Level.INFO, "getOrdersByCompany called");
            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(null, "Invalid token");
            }
            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("Only members can get orders by company");
                return new Response<>(null, "Only members can get orders by company");
            }
            try {
                // validate that the company exist
                Company company = companyRepo.findById(companyId);

                // validate relevant permissions
                if (!company.getCompanyPermission().checkPermission(userId, VIEW_ORDERS_HISTORY)) {
                    logger.log(Level.SEVERE, "Permission required");
                    return new Response<>(null, "Permission required");
                }

                List<Order> orders = new ArrayList<>();
                List<Event> events = eventRepo.findByCompany(companyId);
                for (Event e : events) {
                    orders.addAll(e.getOrders());
                }

                // in case there is no orders for the company
                if (orders.size() == 0) {
                    logger.log(Level.SEVERE, "No orders found for company " + companyId);
                    return new Response<>(null, "No orders found for company " + companyId);
                }

                List<OrderDTO> orderDTOs = orders.stream().map(OrderDTO::new).toList();
                logger.log(Level.INFO, "Orders found: " + orders.size());
                return new Response<>(orderDTOs, "Orders found");

            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "company not found: " + e.getMessage());
                return new Response<>(null, "company not found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "failed getOrdersByCompany : " + e.getMessage());
                return new Response<>(null, "failed getOrdersByCompany : " + e.getMessage());
            }
        });
    }

    public Response<CompanyDetailsDTO> getCompanyDetails(String token, int companyId) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.log(Level.INFO, "retrieving company details for company: " + companyId);
            try {
                Company company = companyRepo.findById(companyId);
                if (company == null) {
                    logger.log(Level.SEVERE, "company not found");
                    return new Response<>(null, "company not found");
                }
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid token");
                    return new Response<>(null, "Invalid token");
                }
                int userId = getUserIdFromToken(token);
                boolean isMember = "MEMBER".equals(role);
                boolean isUserPermitted = company.isActive() || (isMember && company.getCompanyPermission().checkPermission(userId, PermissionType.VIEW_CLOSED_COMPANIES));
                if (!isUserPermitted) {
                    logger.log(Level.SEVERE, "User is not permitted to view closed companies");
                    return new Response<>(null, "User is not permitted to view closed companies");
                }
                List<EventDTO> futureEvents = eventRepo.findByCompany(companyId).stream()
                        .filter(Event::isActive)
                        .filter(e -> e.getDate().isAfter(LocalDateTime.now()))
                        .map(e -> new EventDTO(
                                e.getId(),
                                e.getName(),
                                e.getDate(),
                                e.getSaleStartDate(),
                                e.getCategoryEvent(),
                                e.getLocation(),
                                e.getCreatorId(),
                                e.getCompanyId()
                        )).toList();
                CompanyDetailsDTO companyDetailsDTO = new CompanyDetailsDTO(
                        companyId,
                        company.getCompanyName(),
                        company.isActive(),
                        company.getContactInfo().getEmail(),
                        company.getContactInfo().getPhone(),
                        company.getPurchasePolicy().describe(),
                        company.getDiscountPolicy().describe(),
                        futureEvents);
                if (futureEvents.isEmpty()) {
                    logger.log(Level.INFO, "No future events found for company " + companyId);
                    return new Response<>(companyDetailsDTO, "No future events found for company " + companyId);
                }
                logger.log(Level.INFO, "Company details found: " + companyDetailsDTO);
                return new Response<>(companyDetailsDTO, "Company details found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "failed getCompanyDetails : " + e.getMessage());
                return new Response<>(null, "failed getCompanyDetails : " + e.getMessage());
            }
        });
    }

    public Response<SalesReportDTO> generateSalesReports(int companyId, String token){
        return RetryHelper.executeWithRetry(() -> {
        logger.log(Level.INFO, "generateSalesReports called");
        try {
            Company company = companyRepo.findById(companyId);
            if (company == null) {
                logger.log(Level.SEVERE, "company not found");
                return new Response<>(null, "company not found");
            }
            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid or expired token");
                return new Response<>(null, "Invalid or expired token");
            }
            int userId = getUserIdFromToken(token);
            boolean isMember = userId != -1;
            boolean isUserPermitted = isMember && (company.getCompanyPermission().checkPermission(userId, PermissionType.GENERATE_SALES_REPORTS)); 
            //The requerment is just for the Owner 
            if (!isUserPermitted) {
                logger.log(Level.SEVERE, "User is not permitted to generate sales report");
                return new Response<>(null, "User is not permitted generate sales report");
            }
            Set<Integer> allsSubTree = company.getCompanyPermission().getSubTreeAppointees(userId);
            double totalRevenue = 0;
            int totalTicketsSold = 0;
            List<EventSalesRecordDTO> events = new ArrayList<>();
            List<Event> allEvents = eventRepo.findByCompany(companyId);
            for (Event e : allEvents) {
                if(allsSubTree.contains(e.getCreatorId())){
                    double eventRevnue = 0;
                    int eventTicketsSold = 0;
                    for(Order o : e.getOrders()){
                        eventRevnue += o.getTotalSum();
                        eventTicketsSold += o.getNumOfTickets();

                        totalRevenue += o.getTotalSum();
                        totalTicketsSold += o.getNumOfTickets();
                    }
                    if(eventTicketsSold>0) {
                        EventSalesRecordDTO eventSalesRecordDTO = new EventSalesRecordDTO(e.getId(), e.getName(), e.getCreatorId(), eventTicketsSold, eventRevnue);
                        events.add(eventSalesRecordDTO);
                    }
                }
            }
            if(events.isEmpty()){
                logger.log(Level.WARNING, "No sales data found for company " + companyId);
                return new Response<>(new SalesReportDTO(companyId,totalRevenue,totalTicketsSold,new ArrayList<>()), "No future events found for company " + companyId);
            }
            SalesReportDTO result = new SalesReportDTO(companyId,totalRevenue,totalTicketsSold,events);
            logger.log(Level.INFO, "Sales Report generated successfully");
            return new Response<>(result, "Sales Report generated successfully");
        }
        catch (OptimisticLockingFailureException e) {
            throw e;
        } catch(Exception e){
            logger.log(Level.SEVERE, "failed generate sales report : " + e.getMessage());
            return new Response<>(null, "failed generate sales report : " + e.getMessage());
        }
        });
    }
    public Response<Boolean> processRefund(String token, Integer eventId, int orderId) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "processRefund called");
            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid token");
                return new Response<>(false, "Invalid token");
            }
            int userId = getUserIdFromToken(token);
            if (userId == -1) {
                logger.severe("Invalid token");
                return new Response<>(false, "Invalid token");
            }

            try {
                Event event = eventRepo.findById(eventId);
                Order order = event.findOrderById(orderId);

                if (order == null) {
                    logger.log(Level.SEVERE, "Order not found for refund");
                    return new Response<>(false, "No matching order found for refund");
                }

                if (!order.canBeRefunded()) {
                    logger.log(Level.SEVERE, "Order cannot be refunded");
                    return new Response<>(false, "Order cannot be refunded");
                }

                boolean refundApproved = paymentSystem.refund(
                        order.getPaymentConfirmationId(),
                        order.getTotalSum()
                );

                if (refundApproved) {
                    order.markRefunded();
                    eventRepo.store(event);
                    logger.log(Level.INFO, "Refund completed successfully");

                    String userIdentifier = order.getUserIdentifier();
                    NotifyPayload payload = new NotifyPayload("Refund process for " + order.getOrderId() + "in event " + eventId + "because of event closed", eventId,null);
                    sendOrSaveNotification(userIdentifier, new NotifyDTO(GENERAL_POPUP,payload));
                    return new Response<>(true, "Refund completed successfully");
                }

                order.markRefundRequired();
                eventRepo.store(event);
                String userIdentifier = order.getUserIdentifier();
                NotifyPayload payload = new NotifyPayload("Refund process failed for " + order.getOrderId() + "in event " + eventId + "because of event closed", eventId,null);
                sendOrSaveNotification(userIdentifier, new NotifyDTO(GENERAL_POPUP,payload));
                logger.log(Level.SEVERE, "Refund rejected by external payment service");
                return new Response<>(false, "Refund rejected by external payment service");

            } catch (NoSuchElementException e) {
                logger.log(Level.SEVERE, "Event not found: " + e.getMessage());
                return new Response<>(false, "Event not found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to process refund: " + e.getMessage());
                return new Response<>(false, "Failed to process refund: " + e.getMessage());
            }
        });
    }


    public Response<Boolean> addRuleToEvent(String token, int eventId, PurchaseRuleDTO ruleDTO) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("addRuleToEvent called for eventId: " + eventId);
            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                int userId = getUserIdFromToken(token);
                if (userId == -1)
                    return Response.error("Only members can add rule to event");
                if (suspensionRepo.haveActiveSuspension(userId)) {
                    logger.severe("User does not have write access caused by suspension");
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }
                if (ruleDTO == null)
                    return Response.error("Invalid rule data");

                Event event = eventRepo.findById(eventId);
                Company company = companyRepo.findById(event.getCompanyId());
                if (!company.isActive())
                    return Response.error("Company is not active");
                if (!company.checkPermission(userId, PermissionType.MANAGE_POLICIES))
                    return Response.error("User does not have permission to manage event policies");

                event.addRule(ruleDTO);
                eventRepo.store(event);

                logger.info("addRuleToEvent succeeded for eventId: " + eventId);
                return Response.ok(true);

            } catch (SecurityException e) {
                logger.warning("addRuleToEvent unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warning("addRuleToEvent invalid data: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalStateException e) {
                logger.warning("addRuleToEvent invalid state: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in addRuleToEvent: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> removeRuleFromEvent(String token, int eventId, PurchaseRuleDTO ruleDTO) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("removeRuleFromEvent called for eventId: " + eventId);
            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                int userId = getUserIdFromToken(token);
                if (userId == -1)
                    return Response.error("Only members can remove rule from event");
                if (suspensionRepo.haveActiveSuspension(userId)) {
                    logger.severe("User does not have write access caused by suspension");
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }
                if (ruleDTO == null)
                    return Response.error("Invalid rule data");

                Event event = eventRepo.findById(eventId);
                Company company = companyRepo.findById(event.getCompanyId());
                if (!company.isActive())
                    return Response.error("Company is not active");
                if (!company.checkPermission(userId, PermissionType.MANAGE_POLICIES))
                    return Response.error("User does not have permission to manage event policies");

                event.removeRule(ruleDTO);
                eventRepo.store(event);

                logger.info("removeRuleFromEvent succeeded for eventId: " + eventId);
                return Response.ok(true);

            } catch (SecurityException e) {
                logger.warning("removeRuleFromEvent unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warning("removeRuleFromEvent invalid data: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalStateException e) {
                logger.warning("removeRuleFromEvent invalid state: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in removeRuleFromEvent: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> addDiscountToEvent(String token, int eventId, DiscountDTO discountDTO) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("addDiscountToEvent called for eventId: " + eventId);
            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                int userId = getUserIdFromToken(token);
                if (userId == -1)
                    return Response.error("Only members can add discount to event");
                if (suspensionRepo.haveActiveSuspension(userId)) {
                    logger.severe("User does not have write access caused by suspension");
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }
                if (discountDTO == null)
                    return Response.error("Invalid discount data");

                Event event = eventRepo.findById(eventId);
                Company company = companyRepo.findById(event.getCompanyId());
                if (!company.isActive())
                    return Response.error("Company is not active");
                if (!company.checkPermission(userId, PermissionType.MANAGE_POLICIES))
                    return Response.error("User does not have permission to manage event policies");

                event.addDiscount(discountDTO);
                eventRepo.store(event);

                logger.info("addDiscountToEvent succeeded for eventId: " + eventId);
                return Response.ok(true);

            } catch (SecurityException e) {
                logger.warning("addDiscountToEvent unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warning("addDiscountToEvent invalid data: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalStateException e) {
                logger.warning("addDiscountToEvent invalid state: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in addDiscountToEvent: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> removeDiscountFromEvent(String token, int eventId, DiscountDTO discountDTO) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("removeDiscountFromEvent called for eventId: " + eventId);
            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                int userId = getUserIdFromToken(token);
                if (userId == -1)
                    return Response.error("Only members can remove discounts from event");
                if (suspensionRepo.haveActiveSuspension(userId)) {
                    logger.severe("User does not have write access caused by suspension");
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }
                if (discountDTO == null)
                    return Response.error("Invalid discount data");

                Event event = eventRepo.findById(eventId);
                Company company = companyRepo.findById(event.getCompanyId());
                if (!company.isActive())
                    return Response.error("Company is not active");
                if (!company.checkPermission(userId, PermissionType.MANAGE_POLICIES))
                    return Response.error("User does not have permission to manage event policies");

                event.removeDiscount(discountDTO);
                eventRepo.store(event);

                logger.info("removeDiscountFromEvent succeeded for eventId: " + eventId);
                return Response.ok(true);

            } catch (SecurityException e) {
                logger.warning("removeDiscountFromEvent unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warning("removeDiscountFromEvent invalid data: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalStateException e) {
                logger.warning("removeDiscountFromEvent invalid state: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in removeDiscountFromEvent: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Void> changeEventPurchasePolicyType(String token, int eventId, PurchasePolicyType policyType) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("changeEventPurchasePolicyType called for eventId: " + eventId);
            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                int userId = getUserIdFromToken(token);
                if (userId == -1)
                    return Response.error("Only members can change purpose policy types");
                if (suspensionRepo.haveActiveSuspension(userId)) {
                    logger.severe("User does not have write access caused by suspension");
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }
                Event event = eventRepo.findById(eventId);
                Company company = companyRepo.findById(event.getCompanyId());
                if (!company.isActive())
                    return Response.error("Company is not active");
                if (!company.checkPermission(userId, PermissionType.MANAGE_POLICIES))
                    return Response.error("User does not have permission to manage event policies");

                event.changePurchasePolicyType(policyType);
                eventRepo.store(event);

                logger.info("changeEventPurchasePolicyType succeeded for eventId: " + eventId);
                return Response.ok(null);

            } catch (SecurityException e) {
                logger.warning("changeEventPurchasePolicyType unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in changeEventPurchasePolicyType: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Void> changeEventDiscountPolicyType(String token, int eventId, DiscountPolicyType policyType) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("changeEventDiscountPolicyType called for eventId: " + eventId);
            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.log(Level.SEVERE, "Invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                int userId = getUserIdFromToken(token);
                if (userId == -1)
                    return Response.error("Only members can change discount policies");
                if (suspensionRepo.haveActiveSuspension(userId)) {
                    logger.severe("User does not have write access caused by suspension");
                    return new Response<>(null, "user does not have write access caused by suspension.");
                }
                Event event = eventRepo.findById(eventId);
                Company company = companyRepo.findById(event.getCompanyId());
                if (!company.isActive())
                    return Response.error("Company is not active");
                if (!company.checkPermission(userId, PermissionType.MANAGE_POLICIES))
                    return Response.error("User does not have permission to manage event policies");

                event.changeDiscountPolicyType(policyType);
                eventRepo.store(event);

                logger.info("changeEventDiscountPolicyType succeeded for eventId: " + eventId);
                return Response.ok(null);

            } catch (SecurityException e) {
                logger.warning("changeEventDiscountPolicyType unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in changeEventDiscountPolicyType: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<List<PurchaseHistoryDTO>> getPurchaseHistoryByUser(String token) {
        return RetryHelper.executeWithRetry(() -> {
            logger.log(Level.INFO, "getPurchaseHistoryByUser called");
            String role = getValidatedRole(token);
            if (role == null) {
                logger.log(Level.SEVERE, "Invalid or expired token");
                return new Response<>(null, "Invalid or expired token");
            }
            String userEmail = auth.getUserEmail(token).getValue();
            if (userEmail == null) {
                logger.severe("User is not logged in");
                return new Response<>(null, "User is not logged in");
            }
            try {
                List<PurchaseHistoryDTO> purchaseHistory = new ArrayList<>();

                List<Event> events = eventRepo.getAll();

                for (Event event : events) {
                    List<Order> orders = event.getOrders();

                    for (Order order : orders) {
                        if (order.getUserIdentifier().equals(userEmail)) {
                            purchaseHistory.add(order.toPurchaseHistoryDTO());
                        }
                    }
                }

                if (purchaseHistory.isEmpty()) {
                    logger.log(Level.INFO, "No purchase history found for user " + userEmail);
                    return new Response<>(purchaseHistory, "No purchase history found for user");
                }

                logger.log(Level.INFO, "Purchase history found: " + purchaseHistory.size());
                return new Response<>(purchaseHistory, "Purchase history found");

            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to get purchase history: " + e.getMessage());
                return new Response<>(null, "Failed to get purchase history: " + e.getMessage());
            }
        });
    }
    private int getUserIdFromToken(String token) {
        String email = auth.getUserEmail(token).getValue();
        if (email == null) {
            return -1;
        }
        Member m = userRepo.findUserByEmail(email);
        if (m != null) return m.getUserId();
        return -1; //for guest or invalid
    }

    private void notifyTokenExpired(String token) {
        try{
            NotifyPayload payload = new NotifyPayload("Your session has expired");
            NotifyDTO expiredNotify = new NotifyDTO(NotifyType.TOKEN_EXPIRED, payload);
            notifier.notifyTab(token, expiredNotify);
            logger.info("Sent TOKEN_EXPIRED notification to tab: " + token);
        } catch (Exception e) {
            logger.warning("Failed to send TOKEN_EXPIRED notification: " + e.getMessage());
        }
    }

    private String getValidatedRole(String token) {
        Response<String> roleRes = auth.getRole(token);
        if (roleRes.getValue() == null) {
            notifyTokenExpired(token);
            return null;
        }
        return roleRes.getValue();
    }
     // Helper method to send a real-time notification or save it as delayed if the user is offline.
    private Response<Void> sendOrSaveNotification(String userIdentifier, NotifyDTO notifyDTO) {
        boolean isDelivered = notifier.notifyUser(userIdentifier, notifyDTO);

        if (isDelivered) {
            return new Response<>(null, "Notification sent successfully");
        }

        logger.info("User is offline. Saving delayed notification for: " + userIdentifier);
        return saveDelayedNotificationWithRetry(userIdentifier, notifyDTO);
    }
    private Response<Void> saveDelayedNotificationWithRetry(String userIdentifier, NotifyDTO notifyDTO) {
        return RetryHelper.executeWithRetry(() ->
                transactionTemplate.execute(status -> {
                    try {
                        Member member = userRepo.findUserByEmail(userIdentifier);

                        if (member == null) {
                            logger.warning("User not found for identifier: " + userIdentifier);
                            return new Response<>(null, "User not found");
                        }
                            UserNotification userNotification = new UserNotification(notifyDTO.getType(),notifyDTO.getPayload());
                            member.addDelayedNotification(userNotification);
                            userRepo.store(member);

                            logger.info("Delayed notification saved successfully for: " + member.getIdentifier());
                            return new Response<>(null, "Notification saved as delayed");

                    } catch (OptimisticLockingFailureException e) {
                        status.setRollbackOnly();
                        throw e;

                    } catch (Exception e) {
                        status.setRollbackOnly();
                        logger.warning("Failed to send or save notification for "
                                + userIdentifier + ": " + e.getMessage());

                        return new Response<>(null, "Failed to send or save notification");
                    }
                })
        );
    }
}
