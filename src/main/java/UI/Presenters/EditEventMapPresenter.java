package UI.Presenters;

import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import application.EventCompanyManageService;
import application.Response;
import domain.dto.EventMapDTO;

import java.util.Collections;
import java.util.List;

public class EditEventMapPresenter {

    private final EventCompanyManageService eventCompanyManageService;

    public EditEventMapPresenter(EventCompanyManageService eventCompanyManageService) {
        this.eventCompanyManageService = eventCompanyManageService;
    }

    public Response<EventMapDTO> getExistingEventMap(String token, int eventId) {
        return eventCompanyManageService.getEventMapForManagement(token, eventId);
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

    public Response<Boolean> removeZoneFromEventMap(
            String token,
            int eventId,
            int zoneId
    ) {
        return eventCompanyManageService.removeZonesToEventMap(
                token,
                eventId,
                zoneId
        );
    }
}