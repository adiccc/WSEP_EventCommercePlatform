package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dataType.*;
import domain.dto.CompanyDetailsDTO;
import domain.dto.EventDTO;
import domain.event.Event;
import domain.event.EventMap;
import domain.event.EventQueue;
import domain.event.IEventRepo;
import domain.dataType.ElementPosition;
import domain.dataType.SeatingZone;
import domain.dataType.StandingZone;
import domain.dataType.Zone;
import domain.event.*;
import domain.lottery.ILotteryRepo;
import domain.user.Member;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.logging.*;

import java.util.List;
import java.util.NoSuchElementException;

import static domain.dataType.PermissionType.CREATE_EVENT;
import static domain.dataType.PermissionType.VIEW_ORDERS_HISTORY;

public class EventCompanyManageService {
    private final ICompanyRepo companyRepo;
    private final IEventRepo eventRepo;
    private final Logger logger;
    private final IAuth auth;

    public EventCompanyManageService(ICompanyRepo companyRepo, IEventRepo eventRepo, IAuth auth) {
        this.companyRepo = companyRepo;
        this.eventRepo = eventRepo;
        this.auth = auth;
        this.logger = Logger.getLogger(EventCompanyManageService.class.getName());
    }

    public Response<Boolean> DefineVenueAndSeatingMap(String token, String eventId, ElementPositionDTO stage,
            List<ElementPositionDTO> entries, List<StandingZoneDTO> standingZone, List<SeatingZoneDTO> seatingZone) {
        logger.log(Level.INFO, "DefineVenueAndSeatingMap called");

        // check valid token
        int userId = auth.getUserId(token).getValue();
        if (userId == -1) {
            logger.severe("Invalid token");
            return new Response<>(false, "Invalid token");
        }
        try {
            Event event = eventRepo.findById(eventId);
            int companyId = event.getCompanyId();
            int eventCreator = event.getCreatorId();
            Company c = this.companyRepo.findById(companyId);

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
        } catch (Exception e) {
            logger.log(Level.SEVERE, "failed creating a map : " + e.getMessage());
            return new Response<>(false, "failed to create map : " + e.getMessage());
        }

    }

    public Response<String> createEvent(String token, int companyId, LocalDateTime date, String name,
            LocalDateTime saleStartDate, boolean hasLottery, GeographicalArea location, CategoryEvent category) {
        logger.log(Level.INFO, "createEvent called");

        // check valid token
        int creatorId = auth.getUserId(token).getValue();
        if (creatorId == -1) {
            logger.severe("Invalid token");
            return new Response<>(null, "Invalid token");
        }

        try {
            Company c = this.companyRepo.findById(companyId);
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
        } catch (Exception e) {
            logger.log(Level.SEVERE, "failed creating event : " + e.getMessage());
            return new Response<>(null, "failed to create event : " + e.getMessage());
        }
    }

    public Response<Boolean> UpdateEventDate(String token, String eventId, LocalDateTime date) {
        logger.log(Level.INFO, "UpdateEventDate called");

        // check valid token
        int userId = auth.getUserId(token).getValue();
        if (userId == -1) {
            logger.severe("Invalid token");
            return new Response<>(false, "Invalid token");
        }
        try {
            Event event = eventRepo.findById(eventId);
            if (event.getCreatorId() != userId) {
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
            return new Response<>(true, "Event updated successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "failed creating event : " + e.getMessage());
            return new Response<>(false, "failed to create event : " + e.getMessage());
        }

    }

    // adding new zones to an existing map of an event
    public Response<Boolean> AddZonesToEventMap(String token, String eventId, List<StandingZoneDTO> standingZone,
            List<SeatingZoneDTO> seatingZone) {
        logger.log(Level.INFO, "AddZonesToEventMap called");

        // check valid token
        int userId = auth.getUserId(token).getValue();
        if (userId == -1) {
            logger.severe("Invalid token");
            return new Response<>(false, "Invalid token");
        }

        try {
            Event event = eventRepo.findById(eventId);
            int eventCreator = event.getCreatorId();

            // check appropriate permission
            if (userId != eventCreator) {
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

        } catch (Exception e) {
            logger.log(Level.SEVERE, "failed adding zones to event map : " + e.getMessage());
            return new Response<>(false, "failed to add zones to event map : " + e.getMessage());
        }
    }

    public Response<List<Order>> getOrdersByCompany(String token, int companyId) {
        logger.log(Level.INFO, "getOrdersByCompany called");
        int userId = auth.getUserId(token).getValue();
        if (userId == -1) {
            logger.severe("Invalid token");
            return new Response<>(null, "Invalid token");
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
            logger.log(Level.INFO, "Orders found: " + orders.size());
            return new Response<>(orders, "orders found");

        } catch (NoSuchElementException e) {
            logger.log(Level.SEVERE, "company not found: " + e.getMessage());
            return new Response<>(null, "company not found");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "failed getOrdersByCompany : " + e.getMessage());
            return new Response<>(null, "failed getOrdersByCompany : " + e.getMessage());
        }
    }
    public Response<CompanyDetailsDTO> getCompanyDetails(String token, int companyId) {
        logger.log(Level.INFO, "retrieving company details for company: " + companyId);
        try {
            Company company=companyRepo.findById(companyId);
            if (company == null) {
                logger.log(Level.SEVERE, "company not found");
                return new Response<>(null, "company not found");
            }
            int userId = auth.getUserId(token).getValue();
            boolean isMember = userId != -1;
            boolean isUserPermitted = company.isActive() || (isMember && (company.getCompanyPermission().checkPermission(userId,PermissionType.VIEW_CLOSED_COMPANIES)));
            if (!isUserPermitted) {
                logger.log(Level.SEVERE, "User is not permitted to view closed companies");
                return new Response<>(null, "User is not permitted to view closed companies");
            }
            List<EventDTO> futureEvents= eventRepo.findByCompany(companyId).stream()
                    .filter(Event::isActive)
                    .filter(e->e.getDate().isAfter(LocalDateTime.now()))
                    .map(e-> new EventDTO(
                            e.getId(),
                            e.getName(),
                            e.getDate(),
                            e.getSaleStartDate(),
                            e.getCategoryEvent(),
                            e.getLocation(),
                            e.getCreatorId()
                    )).toList();
            CompanyDetailsDTO companyDetailsDTO=new CompanyDetailsDTO(
                    companyId,
                    company.getCompanyName(),
                    company.isActive(),
                    company.getContactInfo().getEmail(),
                    company.getContactInfo().getPhone(),
                    company.getPurchasePolicy().describe(),
                    company.getDiscountPolicy().describe(),
                    futureEvents);
            if(futureEvents.isEmpty()){
                logger.log(Level.INFO, "No future events found for company " + companyId);
                return new Response<>(companyDetailsDTO, "No future events found for company " + companyId);
            }
            logger.log(Level.INFO, "Company details found: " + companyDetailsDTO);
            return new Response<>(companyDetailsDTO, "Company details found");
        }
        catch(Exception e){
            logger.log(Level.SEVERE, "failed getCompanyDetails : " + e.getMessage());
            return new Response<>(null, "failed getCompanyDetails : " + e.getMessage());
        }
    }
}
