package UI.Presenters;

import application.CompanyService;
import application.EventCompanyManageService;
import application.EventService;
import application.Response;
import domain.dataType.PermissionType;
import domain.dto.EventDetailsDTO;

import java.time.LocalDateTime;
import java.util.Set;

public class EventDetailsPresenter {

    private final EventService eventService;
    private final EventCompanyManageService eventCompanyManageService;
    private final CompanyService companyService;

    public EventDetailsPresenter(
            EventService eventService,
            EventCompanyManageService eventCompanyManageService,
            CompanyService companyService
    ) {
        this.eventService = eventService;
        this.eventCompanyManageService = eventCompanyManageService;
        this.companyService = companyService;
    }

    public Response<EventDetailsDTO> getDetails(
            String token,
            int companyId,
            int eventId
    ) {
        return eventService.ViewEventDetails(
                token,
                companyId,
                eventId
        );
    }

    public Response<Boolean> updateEventDate(
            String token,
            int eventId,
            LocalDateTime newDate
    ) {
        return eventCompanyManageService.UpdateEventDate(
                token,
                eventId,
                newDate
        );
    }

    public boolean canUpdateEventDate(
            String token,
            int companyId
    ) {
        String role = getUserRole(token, companyId);

        if ("FOUNDER".equals(role) || "OWNER".equals(role)) {
            return true;
        }

        if (!"MANAGER".equals(role)) {
            return false;
        }

        Response<Set<PermissionType>> permissionsResponse =
                companyService.getMyPermissions(token, companyId);

        Set<PermissionType> permissions = permissionsResponse.getValue();

        return permissions != null
                && permissions.contains(PermissionType.CREATE_EVENT);
    }

    private String getUserRole(
            String token,
            int companyId
    ) {
        Response<String> response =
                companyService.getUserRoleInCompany(token, companyId);

        return response.getValue() != null
                ? response.getValue()
                : "MEMBER";
    }
}