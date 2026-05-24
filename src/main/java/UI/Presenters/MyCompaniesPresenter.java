package UI.Presenters;

import application.CompanyService;
import application.Response;
import domain.dto.CompanyDTO;

import java.util.List;

/**
 * Presenter for MyCompaniesView.
 * Delegates to CompanyService — no business logic.
 */
public class MyCompaniesPresenter {

    private final CompanyService companyService;

    public MyCompaniesPresenter(CompanyService companyService) {
        this.companyService = companyService;
    }

    /** Returns companies where the user holds a role (FOUNDER / OWNER / MANAGER). */
    public Response<List<CompanyDTO>> getMyCompanies(String token) {
        return companyService.getMyCompanies(token);
    }

    /** Returns the user's role in a specific company. */
    public String getUserRole(String token, int companyId) {
        var r = companyService.getUserRoleInCompany(token, companyId);
        return r.getValue() != null ? r.getValue() : "MEMBER";
    }
}
