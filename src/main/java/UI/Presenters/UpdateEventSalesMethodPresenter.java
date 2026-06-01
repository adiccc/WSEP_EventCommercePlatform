package UI.Presenters;

import application.CompanyService;
import application.LotteryService;
import application.Response;
import domain.dto.LotteryDTO;

public class UpdateEventSalesMethodPresenter {

    private final LotteryService lotteryService;
    private final CompanyService companyService;

    public UpdateEventSalesMethodPresenter(
            LotteryService lotteryService,
            CompanyService companyService
    ) {
        this.lotteryService = lotteryService;
        this.companyService = companyService;
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