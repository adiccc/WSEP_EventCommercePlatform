package UI.Presenters;

import application.AdminService;
import application.Response;

public class AdminCompanyManagementPresenter {

    private final AdminService adminService;

    public AdminCompanyManagementPresenter(AdminService adminService) {
        this.adminService = adminService;
    }

    public Response<Boolean> closeCompanyByAdmin(String token, int companyId) {
        return adminService.closeCompanyByAdmin(token, companyId);
    }
}