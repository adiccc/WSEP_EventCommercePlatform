package UI.Presenters;

import DTO.AdminPurchaseHistoryDTO;
import application.AdminService;
import application.Response;

import java.util.List;

public class AdminPurchaseHistoryPresenter {

    private final AdminService adminService;

    public AdminPurchaseHistoryPresenter(AdminService adminService) {
        this.adminService = adminService;
    }

    public Response<List<AdminPurchaseHistoryDTO>> getGlobalOrders(
            String token,
            List<String> usersFilter,
            List<Integer> eventsFilter,
            List<Integer> companiesFilter
    ) {
        return adminService.getGlobalOrders(
                token,
                usersFilter,
                eventsFilter,
                companiesFilter
        );
    }
}