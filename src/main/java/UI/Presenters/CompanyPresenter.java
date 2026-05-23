package UI.Presenters;

import application.CompanyService;
import application.EventService;
import application.Response;
import domain.dataType.EventSearchFilter;
import domain.dto.CompanyDetailsDTO;
import domain.dto.EventDTO;

import java.util.List;

/**
 * Presenter for CompanyView.
 * Holds CompanyService + EventService — returns data, never touches UI.
 */
public class CompanyPresenter {

    private final CompanyService companyService;
    private final EventService eventService;

    public CompanyPresenter(CompanyService companyService, EventService eventService) {
        this.companyService = companyService;
        this.eventService = eventService;
    }

    /** Returns full company details including contact info and future events. */
    public Response<CompanyDetailsDTO> getCompany(String token, int companyId) {
        return companyService.getProductionCompany(token, companyId);
    }

    /**
     * Returns events for a specific company filtered by the given filter.
     * Any filter field left null means "no restriction on that field".
     */
    public Response<List<EventDTO>> searchEvents(String token, int companyId, EventSearchFilter filter) {
        return eventService.searchCompanyEvents(token, companyId, filter);
    }
}
