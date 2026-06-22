package domain.dto;

import DTO.StandingZoneDTO;
import DTO.SeatingZoneDTO;
import java.util.HashMap;
import java.util.Map;
import DTO.ElementPositionDTO;
import domain.dataType.*;
import domain.event.EventMap;
import domain.event.SeatingZone;
import domain.event.StandingZone;
import domain.event.Zone;

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
                        standingZone.getId(),
                        standingZone.getCapacity(),
                        standingZone.getAvailable(),
                        standingZone.getName(),
                        standingZone.getPrice(),
                        new ElementPositionDTO(
                                standingZone.getElementPosition().getX(),
                                standingZone.getElementPosition().getY()
                        )
                ));
            }

            if (zone instanceof SeatingZone seatingZone) {
                Map<String, TicketStatus> ticketStatuses = new HashMap<>();

                for (var ticketEntry : seatingZone.getTicketMap().entrySet()) {
                    var ticket = ticketEntry.getValue();

                    ticketStatuses.put(
                            ticket.getRow() + "-" + ticket.getCol(),
                            ticket.getStatus()
                    );
                }

                seatingZones.add(new SeatingZoneDTO(
                        seatingZone.getId(),
                        seatingZone.getRows(),
                        seatingZone.getCols(),
                        seatingZone.getName(),
                        seatingZone.getPrice(),
                        new ElementPositionDTO(
                                seatingZone.getElementPosition().getX(),
                                seatingZone.getElementPosition().getY()
                        ),
                        ticketStatuses
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
