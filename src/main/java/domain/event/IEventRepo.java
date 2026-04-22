package domain.event;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;

import java.util.List;

public interface IEventRepo {
    Event getEvent(int eventId);
    EventMap createMap(ElementPositionDTO stage, List<ElementPositionDTO> entries, List<StandingZoneDTO> standingZone, List<SeatingZoneDTO> seatingZone);

}