package infrastructure;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.dataType.ElementPosition;
import domain.dataType.SeatingZone;
import domain.dataType.StandingZone;
import domain.dataType.Zone;
import domain.event.Event;
import domain.event.EventMap;
import domain.event.IEventRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class EventRepoImp implements IEventRepo {
    List<Event> events;

    @Override
    public Event getEvent(int eventId) {
        for(Event event : events) {
            if(event.getId()==eventId)
                return event;
        }
        throw new NoSuchElementException("Event with id "+eventId+" not found");
    }

    @Override
    public EventMap createMap(ElementPositionDTO stage, List<ElementPositionDTO> entries, List<StandingZoneDTO> standingZone, List<SeatingZoneDTO> seatingZone) {
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
        return new EventMap(new ElementPosition(stage.getX(), stage.getY()),allEntries,zones);
    }
}
