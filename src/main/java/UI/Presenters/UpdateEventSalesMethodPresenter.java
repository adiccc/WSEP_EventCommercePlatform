package UI.Presenters;

import application.CompanyService;
import application.EventService;
import application.LotteryService;
import application.Response;
import domain.dto.EventDetailsDTO;
import domain.dto.LotteryDTO;

public class UpdateEventSalesMethodPresenter {

    private final LotteryService lotteryService;
    private final CompanyService companyService;
    private final EventService eventService;

    public UpdateEventSalesMethodPresenter(
            LotteryService lotteryService,
            CompanyService companyService,
            EventService eventService
    ) {
        this.lotteryService = lotteryService;
        this.companyService = companyService;
        this.eventService = eventService;
    }

    public Response<EventDetailsDTO> getEventDetails(
            String token,
            int companyId,
            int eventId
    ) {
        return eventService.ViewEventDetails(token, companyId, eventId);
    }

    public Response<Boolean> updateToRegularSale(String token, int eventId) {
        return lotteryService.updateLottery(token, eventId, null);
    }

    public Response<Boolean> updateToLotterySale(
            String token,
            int eventId,
            LotteryDTO lotteryDTO
    ) {
        return lotteryService.updateLottery(token, eventId, lotteryDTO);
    }

    public boolean canUpdateSalesMethod(String token, int companyId) {
        Response<String> roleResponse =
                companyService.getUserRoleInCompany(token, companyId);

        String role = roleResponse.getValue();

        return "FOUNDER".equals(role) || "OWNER".equals(role);
    }
}