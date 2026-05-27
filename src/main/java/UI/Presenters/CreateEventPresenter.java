package UI.Presenters;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import application.EventCompanyManageService;
import application.Response;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;

import java.time.LocalDateTime;
import java.util.List;

public class CreateEventPresenter {

    private final EventCompanyManageService eventCompanyManageService;

    public CreateEventPresenter(EventCompanyManageService eventCompanyManageService) {
        this.eventCompanyManageService = eventCompanyManageService;
    }

    public Response<Integer> createEvent(
            String token,
            int companyId,
            LocalDateTime eventDate,
            String eventName,
            LocalDateTime saleStartDate,
            boolean hasLottery,
            GeographicalArea location,
            CategoryEvent category
    ) {
        return eventCompanyManageService.createEvent(
                token,
                companyId,
                eventDate,
                eventName,
                saleStartDate,
                hasLottery,
                location,
                category
        );
    }

    public Response<Boolean> defineVenueAndSeatingMap(
            String token,
            Integer eventId,
            ElementPositionDTO stage,
            List<ElementPositionDTO> entries,
            List<StandingZoneDTO> standingZones,
            List<SeatingZoneDTO> seatingZones
    ) {
        return eventCompanyManageService.DefineVenueAndSeatingMap(
                token,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );
    }
}