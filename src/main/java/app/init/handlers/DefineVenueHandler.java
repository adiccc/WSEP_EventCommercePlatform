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
        StandingZoneDTO standing = new StandingZoneDTO(40, "General Standing", 80.0,  new ElementPositionDTO(70,  450));
        SeatingZoneDTO  vip      = new SeatingZoneDTO(3, 10, "VIP",     300.0, new ElementPositionDTO(270, 150));
        SeatingZoneDTO  regular  = new SeatingZoneDTO(8, 10, "Regular", 150.0, new ElementPositionDTO(270, 400));
        SeatingZoneDTO  balcony  = new SeatingZoneDTO(5,  3, "Balcony", 180.0, new ElementPositionDTO(700, 300));

        Response<Boolean> response = eventService.DefineVenueAndSeatingMap(
                token, eventId, stage, entries, List.of(standing), List.of(vip, regular, balcony));
        if (response.getValue() == null)
            throw new InitializationException("define-venue failed for event " + eventId + ": " + response.getMessage());
        return true;
    }
}
