package app.init.handlers;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.EventCompanyManageService;
import application.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DefineVenueHandler implements InitOperationHandler {

    @Autowired
    private EventCompanyManageService eventService;

    @Override
    public String operationType() { return "define-venue"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        String token = params.get("token");
        int eventId  = Integer.parseInt(params.get("eventId"));

        ElementPositionDTO stage = new ElementPositionDTO(400, 20);
        List<ElementPositionDTO> entries = List.of(
                new ElementPositionDTO(50, 100),
                new ElementPositionDTO(750, 100)
        );

        List<StandingZoneDTO> standingZones;
        List<SeatingZoneDTO>  seatingZones;

        // If standing/seating params are present → use them. Otherwise fall back to the default demo venue.
        if (params.containsKey("standingCapacity") || params.containsKey("seatingRows")) {
            standingZones = params.containsKey("standingCapacity")
                    ? List.of(new StandingZoneDTO(
                            Integer.parseInt(params.get("standingCapacity")),
                            params.getOrDefault("standingName", "General Standing"),
                            Double.parseDouble(params.get("standingPrice")),
                            new ElementPositionDTO(70, 450)))
                    : List.of();

            seatingZones = params.containsKey("seatingRows")
                    ? List.of(new SeatingZoneDTO(
                            Integer.parseInt(params.get("seatingRows")),
                            Integer.parseInt(params.get("seatingCols")),
                            params.getOrDefault("seatingName", "Seating"),
                            Double.parseDouble(params.get("seatingPrice")),
                            new ElementPositionDTO(270, 150)))
                    : List.of();
        } else {
            // Default demo venue (used by older init files / unspecified events)
            standingZones = List.of(
                    new StandingZoneDTO(40, "General Standing", 80.0, new ElementPositionDTO(70, 450)));
            seatingZones = List.of(
                    new SeatingZoneDTO(3, 10, "VIP",     300.0, new ElementPositionDTO(270, 150)),
                    new SeatingZoneDTO(8, 10, "Regular", 150.0, new ElementPositionDTO(270, 400)),
                    new SeatingZoneDTO(5,  3, "Balcony", 180.0, new ElementPositionDTO(700, 300)));
        }

        Response<Boolean> response = eventService.DefineVenueAndSeatingMap(
                token, eventId, stage, entries, standingZones, seatingZones);
        if (response.getValue() == null)
            throw new InitializationException("define-venue failed for event " + eventId + ": " + response.getMessage());
        return true;
    }
}
