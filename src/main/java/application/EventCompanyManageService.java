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
import domain.event.IEventRepo;

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

    public EventCompanyManageService(ICompanyRepo companyRepo, IEventRepo eventRepo,TokenService tokenService) {
        this.companyRepo = companyRepo;
        this.eventRepo = eventRepo;
        this.logger = Logger.getLogger(EventCompanyManageService.class.getName());
        this.tokenService = tokenService;
    }


    public Response<Boolean> DefineVenueAndSeatingMap(String token, int userId, int eventId, ElementPositionDTO stage, List<ElementPositionDTO> entries, List<StandingZoneDTO> standingZone , List<SeatingZoneDTO> seatingZone) {
        logger.log(Level.INFO, "DefineVenueAndSeatingMap called");

        // check valid token
        if(!tokenService.validateToken(token)){
            return new Response<>(false, "Invalid token");
        }
        try {
            Event event = eventRepo.findById(eventId);
            int companyId=event.getCompanyId();
            int eventCreator=event.getCreatorId();
            Company c=this.companyRepo.findById(companyId);

            // check appropriate permission
            if(userId!=eventCreator || !c.checkPermission(userId,CreatEvent)){
                return new Response<>(false, "Permission required");
            }

            // create map
            if(stage==null || entries==null || (standingZone==null && seatingZone==null)){
                return new Response<>(false, "map element null");
            }

            List<Zone> zones = new ArrayList<>();
            for(StandingZoneDTO standingZoneDTO : standingZone){
                zones.add(new StandingZone(standingZoneDTO));
            }
            for(SeatingZoneDTO seatingZoneDTO : seatingZone){
                zones.add(new SeatingZone(seatingZoneDTO));
            }
            List<ElementPosition> allEntries=new ArrayList<>();
            for(ElementPositionDTO elementPositionDTO : entries){
                allEntries.add(new ElementPosition(elementPositionDTO.getX(), elementPositionDTO.getY()));
            }
            EventMap eventMap=new EventMap(new ElementPosition(stage.getX(), stage.getY()),allEntries,zones);
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
