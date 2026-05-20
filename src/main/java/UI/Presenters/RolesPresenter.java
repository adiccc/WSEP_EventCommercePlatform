package UI.Presenters;

import application.CompanyService;
import application.Response;
import domain.dataType.PermissionType;
import domain.dto.RolesPermissionsTreeDTO;

import java.util.Set;

/**
 * Presenter for RolesView.
 * Provides role hierarchy (for owners/founders) and personal permissions (for managers).
 */
public class RolesPresenter {

    private final CompanyService companyService;

    public RolesPresenter(CompanyService companyService) {
        this.companyService = companyService;
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
}
