package UI.Presenters;

import DTO.DiscountDTO;
import DTO.PurchaseRuleDTO;
import application.CompanyService;
import application.EventCompanyManageService;
import application.EventService;
import application.Response;
import domain.dataType.EventSearchFilter;
import domain.dataType.PermissionType;
import domain.policy.DiscountPolicyType;
import domain.policy.PurchasePolicyType;
import domain.dto.CompanyDetailsDTO;
import domain.dto.EventDTO;
import domain.dto.OrderDTO;
import domain.dto.SalesReportDTO;

import java.util.List;
import java.util.Set;

/**
 * Presenter for CompanyView.
 * Holds CompanyService + EventService — returns data, never touches UI.
 */
public class CompanyPresenter {

    private final CompanyService companyService;
    private final EventService eventService;
    private final EventCompanyManageService eventCompanyManageService;

    public CompanyPresenter(CompanyService companyService, EventService eventService, EventCompanyManageService eventCompanyManageService) {
        this.companyService = companyService;
        this.eventService = eventService;
        this.eventCompanyManageService = eventCompanyManageService;
    }

    /** Returns full company details including contact info and future events. */
    public Response<CompanyDetailsDTO> getCompany(String token, int companyId) {
        return companyService.getProductionCompany(token, companyId);
    }

    /**
     * Returns the current user's role in the company:
     * "FOUNDER", "OWNER", "MANAGER", or "MEMBER".
     */
    public String getUserRole(String token, int companyId) {
        Response<String> response = companyService.getUserRoleInCompany(token, companyId);
        return response.getValue() != null ? response.getValue() : "MEMBER";
    }

    /**
     * Returns events for a specific company filtered by the given filter.
     * Any filter field left null means "no restriction on that field".
     */
    public Response<List<EventDTO>> searchEvents(String token, int companyId, EventSearchFilter filter) {
        return eventService.searchCompanyEvents(token, companyId, filter);
    }

    /** Returns the manager's granted permissions in this company. */
    public Response<Set<PermissionType>> getMyPermissions(String token, int companyId) {
        return companyService.getMyPermissions(token, companyId);
    }

    /** Changes the company-level purchase policy type (AND / OR). */
    public Response<Void> changePurchasePolicyType(String token, int companyId, PurchasePolicyType policyType) {
        return companyService.changePurchasePolicyType(token, companyId, policyType);
    }

    /** Adds a purchase rule to the company policy. */
    public Response<Boolean> addRuleToCompany(String token, int companyId, PurchaseRuleDTO ruleDTO) {
        return companyService.addRuleToCompany(token, companyId, ruleDTO);
    }

    /** Removes a purchase rule from the company policy. */
    public Response<Boolean> removeRuleFromCompany(String token, int companyId, PurchaseRuleDTO ruleDTO) {
        return companyService.removeRuleFromCompany(token, companyId, ruleDTO);
    }

    public Response<Boolean> addDiscountToCompany(
            String token,
            int companyId,
            DiscountDTO discountDTO
    ) {
        return companyService.addDiscountToCompany(
                token,
                companyId,
                discountDTO
        );
    }

    public Response<Boolean> removeDiscountFromCompany(
            String token,
            int companyId,
            DiscountDTO discountDTO
    ) {
        return companyService.removeDiscountFromCompany(
                token,
                companyId,
                discountDTO
        );
    }

    public Response<Void> changeDiscountPolicyType(
            String token,
            int companyId,
            DiscountPolicyType policyType
    ) {
        return companyService.changeDiscountPolicyType(
                token,
                companyId,
                policyType
        );
    }

    /** Returns all orders for the company's events. Requires VIEW_ORDERS_HISTORY permission. */
    public Response<List<OrderDTO>> getOrdersByCompany(String token, int companyId) {
        return eventCompanyManageService.getOrdersByCompany(token, companyId);
    }

    /** Generates a sales report for the company. Requires GENERATE_SALES_REPORTS permission. */
    public Response<SalesReportDTO> generateSalesReport(String token, int companyId) {
        return eventCompanyManageService.generateSalesReports(companyId, token);
    }
}
