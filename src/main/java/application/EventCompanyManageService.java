package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dataType.ElementPosition;
import domain.dataType.SeatingZone;
import domain.dataType.StandingZone;
import domain.dataType.Zone;
import domain.event.Event;
import domain.event.EventMap;
import domain.event.EventQueue;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.user.Member;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.logging.*;

import java.util.List;
import java.util.NoSuchElementException;

import static domain.dataType.PermissionType.CreatEvent;


public class EventCompanyManageService {
    private final ICompanyRepo companyRepo;
    private final IEventRepo eventRepo;
    private final Logger logger;
    private final TokenService tokenService;
    private final IAuth auth;

    public EventCompanyManageService(ICompanyRepo companyRepo, IEventRepo eventRepo, TokenService tokenService, IAuth auth) {
        this.companyRepo = companyRepo;
        this.eventRepo = eventRepo;
        this.auth = auth;
        this.logger = Logger.getLogger(EventCompanyManageService.class.getName());
        this.tokenService = tokenService;
    }


    public Response<Boolean> DefineVenueAndSeatingMap(String token,String eventId, ElementPositionDTO stage, List<ElementPositionDTO> entries, List<StandingZoneDTO> standingZone, List<SeatingZoneDTO> seatingZone) {
        logger.log(Level.INFO, "DefineVenueAndSeatingMap called");

        // check valid token
        int userId = auth.getUserId(token);
        if(userId == -1) {
            logger.severe("Invalid token");
            return new Response<>(false, "Invalid token");
        }
        try {
            Event event = eventRepo.findById(eventId);
            int companyId = event.getCompanyId();
            int eventCreator = event.getCreatorId();
            Company c = this.companyRepo.findById(companyId);

            // check appropriate permission
            if (userId != eventCreator || !c.checkPermission(userId, CreatEvent)) {
                return new Response<>(false, "Permission required");
            }

            // create map
            if (stage == null || entries == null || (standingZone == null && seatingZone == null)) {
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
            event.setActive(true);
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

    public Response<Boolean> createEvent(String token, int companyId, LocalDateTime date, String name, LocalDateTime saleStartDate, boolean hasLottery) {
        logger.log(Level.INFO, "createEvent called");

        // check valid token
        int creatorId = auth.getUserId(token);
        if(creatorId == -1){
            logger.severe("Invalid token");
            return new Response<>(false, "Invalid token");
        }

        try {
            Company c = this.companyRepo.findById(companyId);
            if (!c.checkPermission(creatorId, CreatEvent)) {
                return new Response<>(false, "Permission required");
            }
            if (date.isBefore(LocalDateTime.now())) {
                return new Response<>(false, "Event date must be in the future");
            }
            if (saleStartDate.isAfter(date)) {
                return new Response<>(false, "Sale start date must be before event date");
            }
            Event event = new Event(companyId, creatorId, date, name, saleStartDate, hasLottery);
            eventRepo.store(event);
            logger.log(Level.INFO, "Event created successfully");
            return new Response<>(true, "Event created successfully");
        } catch (NoSuchElementException e) {
            logger.log(Level.SEVERE, "company not found: " + e.getMessage());
            return new Response<>(false, "Company not found");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "failed creating event : " + e.getMessage());
            return new Response<>(false, "failed to create event : " + e.getMessage());
        }
    }

    public Response<Boolean> UpdateEventDate(String token, String eventId, LocalDateTime date) {
        logger.log(Level.INFO, "UpdateEventDate called");

        // check valid token
        int userId = auth.getUserId(token);
        if(userId == -1){
            logger.severe("Invalid token");
            return new Response<>(false, "Invalid token");
        }
        try {
            Event event = eventRepo.findById(eventId);
            if(event.getCreatorId() != userId) {
                return new Response<>(false, "User id mismatch to the creator of the event");
            }
            if(date.isBefore(event.getDate())) {
                return new Response<>(false, "Event date can only be after the original date");
            }
            if(event.getDate().isBefore(LocalDateTime.now())) {
                return new Response<>(false, "Event date must be in the future");
            }

            event.setDate(date);
            eventRepo.store(event);
            logger.log(Level.INFO, "Event updated successfully");
            return new Response<>(true, "Event updated successfully");
        }catch (Exception e) {
            logger.log(Level.SEVERE, "failed creating event : " + e.getMessage());
            return new Response<>(false, "failed to create event : " + e.getMessage());
        }

    }
}
