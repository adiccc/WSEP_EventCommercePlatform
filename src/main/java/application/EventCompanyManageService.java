package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.company.ICompanyRepo;
import domain.event.Event;
import domain.event.EventMap;
import domain.event.IEventRepo;

import java.util.logging.*;

import java.util.List;
import java.util.NoSuchElementException;

import static domain.dataType.PermissionType.CreatEvent;


public class EventCompanyManageService {
    private final ICompanyRepo companyRepo;
    private final IEventRepo eventRepo;
    private final Logger logger;

    public EventCompanyManageService(ICompanyRepo companyRepo, IEventRepo eventRepo) {
        this.companyRepo = companyRepo;
        this.eventRepo = eventRepo;
        this.logger = Logger.getLogger(EventCompanyManageService.class.getName());
    }


    public Response<Boolean> DefineVenueAndSeatingMap(String token, int userId, int eventId, ElementPositionDTO stage, List<ElementPositionDTO> entries, List<StandingZoneDTO> standingZone , List<SeatingZoneDTO> seatingZone) {
        logger.log(Level.INFO, "DefineVenueAndSeatingMap called");

        // check valid token
        if(!TokenService.validateToken(token)){
            return new Response<>(false, "Invalid token");
        }

        try {
            Event event = eventRepo.getEvent(eventId);
            int companyId=event.getCompanyId();
            int eventCreator=event.getCreatorId();

            // check appropriate permission
            if(userId!=eventCreator || !companyRepo.checkPremissions(companyId,userId,CreatEvent)){
                return new Response<>(false, "Permission required");
            }

            // create map
            if(stage==null || entries==null || (standingZone==null && seatingZone==null)){
                return new Response<>(false, "map element null");
            }

            List<StandingZoneDTO> standingZones = standingZone == null ? List.of() : standingZone;
            List<SeatingZoneDTO> seatingZones = seatingZone == null ? List.of() : seatingZone;

            EventMap eventMap=eventRepo.createMap(stage, entries, standingZones, seatingZones);
            event.setMap(eventMap);

            // success
            logger.log(Level.INFO, "map created and linked to event "+eventId);
            return new Response<>(true, "map saved successfully");

        } catch (NoSuchElementException e) {
            logger.log(Level.SEVERE,"event not found: "+e.getMessage());
            return new Response<>(false, "Event not found");
        }catch (Exception e){
            logger.log(Level.SEVERE,"failed creating a map : "+e.getMessage());
            return new Response<>(false, "failed to create map : "+e.getMessage());
        }

    }
}
