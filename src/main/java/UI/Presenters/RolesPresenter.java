package UI.Presenters;

import application.CompanyService;
import application.Response;
import application.UserService;
import domain.dataType.PermissionType;
import domain.dto.RolesPermissionsTreeDTO;

import java.util.Set;

/**
 * Presenter for RolesView.
 * Provides role hierarchy (for owners/founders) and personal permissions (for managers).
 * All methods delegate directly to services — no business logic here.
 */
public class RolesPresenter {

    private final CompanyService companyService;
    private final UserService userService;

    public RolesPresenter(CompanyService companyService, UserService userService) {
        this.companyService = companyService;
        this.userService = userService;
    }

    /** Returns "FirstName LastName" for any userId. */
    public String getDisplayName(int userId) {
        return userService.getUserDisplayName(userId);
    }

    /** Returns the calling user's role in the company. */
    public String getUserRole(String token, int companyId) {
        var r = companyService.getUserRoleInCompany(token, companyId);
        return r.getValue() != null ? r.getValue() : "MEMBER";
    }

    /**
     * Returns the full roles tree — only succeeds for FOUNDER / OWNER.
     * The view should call this only after confirming the user's role.
     */
    public Response<RolesPermissionsTreeDTO> getRolesTree(String token, int companyId) {
        return companyService.viewRolesAndPermissionsTree(token, companyId);
    }

    /**
     * Returns the calling manager's own set of permissions.
     * Returns an empty set for non-managers.
     */
    public Response<Set<PermissionType>> getMyPermissions(String token, int companyId) {
        return companyService.getMyPermissions(token, companyId);
    }

    // ── Owner actions (delegate to service) ──────────────────────────────────

    /** Updates a manager's permission set. */
    public Response<Boolean> updateManagerPermissions(String token, int companyId,
                                                      int managerId, Set<PermissionType> newPermissions) {
        return companyService.updateManagerPermissions(token, companyId, managerId, newPermissions);
    }

    /** Requests appointing a user as owner (starts the pending flow). */
    public Response<Boolean> requestAppointOwner(String token, int companyId, int appointeeId) {
        return companyService.requestAppointOwner(token, companyId, appointeeId);
    }

    /** Requests appointing a user as manager with the given permissions. */
    public Response<Boolean> requestAppointManager(String token, int companyId,
                                                    int appointeeId, Set<PermissionType> permissions) {
        return companyService.requestAppointManager(token, companyId, appointeeId, permissions);
    }

    /** Removes a manager appointment (only the appointing owner can do this). */
    public Response<Boolean> removeManager(String token, int companyId, int managerId) {
        return companyService.removeManagerAppointment(token, companyId, managerId);
    }
}
