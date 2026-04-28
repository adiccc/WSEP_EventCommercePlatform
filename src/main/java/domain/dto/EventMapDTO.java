package domain.dto;

import DTO.StandingZoneDTO;
import DTO.SeatingZoneDTO;

import DTO.ElementPositionDTO;
import domain.dataType.*;
import domain.event.EventMap;

import java.util.ArrayList;
import java.util.List;

public class EventMapDTO {

    private List<StandingZoneDTO> standingZones;
    private List<SeatingZoneDTO> seatingZones;
    private ElementPositionDTO stage;
    private List<ElementPositionDTO> entries;

    public EventMapDTO(EventMap eventMap) {
        this.standingZones = new ArrayList<>();
        this.seatingZones = new ArrayList<>();
        this.entries = new ArrayList<>();

        for (Zone zone : eventMap.getZones()) {
            if (zone instanceof StandingZone standingZone) {
                standingZones.add(new StandingZoneDTO(
                        standingZone.getCapacity(),
                        standingZone.getName(),
                        standingZone.getPrice(),
                        new ElementPositionDTO(
                                 standingZone.getElementPosition().getX(),
                                 standingZone.getElementPosition().getY()
                        )
                ));
            }

            if (zone instanceof SeatingZone seatingZone) {
                seatingZones.add(new SeatingZoneDTO(
                        seatingZone.getRows(),
                        seatingZone.getCols(),
                        seatingZone.getName(),
                        seatingZone.getPrice(),
                        new ElementPositionDTO(
                                  seatingZone.getElementPosition().getX(),
                                  seatingZone.getElementPosition().getY()
                        )
                ));
            }
        }

        for (ElementPosition entry : eventMap.getEntries()) {
            entries.add(new ElementPositionDTO( entry.getX(), entry.getY()));
        }

        this.stage = new ElementPositionDTO(
                 eventMap.getStage().getX(),
                 eventMap.getStage().getY()
        );
    }

    public List<StandingZoneDTO> getStandingZones() {
        return standingZones;
    }

    public List<SeatingZoneDTO> getSeatingZones() {
        return seatingZones;
    }

    public ElementPositionDTO getStage() {
        return stage;
    }

    public List<ElementPositionDTO> getEntries() {
        return entries;
    }
}
