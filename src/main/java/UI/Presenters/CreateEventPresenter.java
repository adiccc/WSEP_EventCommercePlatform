package UI.Presenters;

import application.EventCompanyManageService;
import application.Response;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;

import java.time.LocalDateTime;

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
}