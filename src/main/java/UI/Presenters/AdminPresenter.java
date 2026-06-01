package UI.Presenters;

import application.AdminService;
import application.Response;
import domain.dto.SuspensionDTO;

import java.util.List;

public class AdminPresenter {

    private final AdminService adminService;

    public AdminPresenter(AdminService adminService) {
        this.adminService = adminService;
    }

    public Response<Boolean> suspendUserPermanent(String token, int userId) {
        return adminService.SuspendUser(token, userId);
    }

    public Response<Boolean> suspendUserTemporary(String token, int userId, int days) {
        return adminService.SuspendUser(token, userId, days);
    }

    public Response<Boolean> unsuspendUser(String token, int userId) {
        return adminService.UnsuspendUser(token, userId);
    }

    public Response<Boolean> removeUser(String token, int userId) {
        return adminService.removeUser(token, userId);
    }

    public Response<List<SuspensionDTO>> getAllSuspensions(String token) {
        return adminService.getAllUsersSuspensions(token);
    }
}
