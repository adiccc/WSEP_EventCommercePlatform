package UI.Presenters;

import DTO.PurchaseHistoryDTO;
import application.EventCompanyManageService;
import application.Response;

import java.util.List;

public class PurchaseHistoryPresenter {

    private final EventCompanyManageService eventCompanyManageService;

    public PurchaseHistoryPresenter(EventCompanyManageService eventCompanyManageService) {
        this.eventCompanyManageService = eventCompanyManageService;
    }

    public Response<List<PurchaseHistoryDTO>> getPurchaseHistory(String token) {
        return eventCompanyManageService.getPurchaseHistoryByUser(token);
    }
}