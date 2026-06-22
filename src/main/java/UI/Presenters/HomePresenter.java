package UI.Presenters;

import application.CompanyService;
import DTO.CompanyDTO;

import java.util.List;

/**
 * Presenter for HomeView — lecture MVP pattern:
 *
 *   Presenter unwraps Response internally.
 *   View receives DTOs directly — never sees Response<T>.
 *   DTO serves as the Model (no separate model classes needed).
 */
public class HomePresenter {

    private final CompanyService companyService;
    private String lastError;

    public HomePresenter(CompanyService companyService) {
        this.companyService = companyService;
    }

    /**
     * Returns list of companies, or null on failure.
     * View checks for null and calls getLastError() if needed.
     */
    public List<CompanyDTO> getCompanies(String token) {
        var response = companyService.getAvailableCompanies(token);
        lastError = response.getMessage();
        return response.getValue();
    }

    /** Returns the error message from the last failed call. */
    public String getLastError() {
        return lastError;
    }
}
