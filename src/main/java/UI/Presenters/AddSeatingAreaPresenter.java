package UI.Presenters;

import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import application.EventCompanyManageService;
import application.Response;

import java.util.Collections;
import java.util.List;

public class AddSeatingAreaPresenter {

    private final EventCompanyManageService eventCompanyManageService;

    public AddSeatingAreaPresenter(EventCompanyManageService eventCompanyManageService) {
        this.eventCompanyManageService = eventCompanyManageService;
    }

    public Response<Boolean> addZonesToEventMap(
            String token,
            int eventId,
            List<StandingZoneDTO> standingZones,
            List<SeatingZoneDTO> seatingZones
    ) {
        return eventCompanyManageService.AddZonesToEventMap(
                token,
                eventId,
                standingZones != null ? standingZones : Collections.emptyList(),
                seatingZones != null ? seatingZones : Collections.emptyList()
        );
    }
}