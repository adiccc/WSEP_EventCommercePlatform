package UI.Presenters;

import application.CompanyService;
import application.EventCompanyManageService;
import application.EventService;
import application.IAuth;
import application.LotteryService;
import application.Response;
import domain.dataType.PermissionType;
import domain.dto.EventDetailsDTO;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventDetailsPresenter {

    private final EventService eventService;
    private final EventCompanyManageService eventCompanyManageService;
    private final CompanyService companyService;
    private final LotteryService lotteryService;
    private final IAuth auth;

    public EventDetailsPresenter(
            EventService eventService,
            EventCompanyManageService eventCompanyManageService,
            CompanyService companyService,
            LotteryService lotteryService,
            IAuth auth
    ) {
        this.eventService = eventService;
        this.eventCompanyManageService = eventCompanyManageService;
        this.companyService = companyService;
        this.lotteryService = lotteryService;
        this.auth = auth;
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

    public Response<Boolean> deleteEvent(
            String token,
            int eventId
    ) {
        return eventCompanyManageService.DeleteEvent(
                token,
                eventId
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

    public boolean canDeleteEvent(
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
                && permissions.contains(PermissionType.DELETE_EVENT);
    }

    public Response<Boolean> registerUserToLottery(
            String token,
            int eventId
    ) {
        return lotteryService.registerUserToLottery(
                token,
                eventId
        );
    }

    public Response<Boolean> canRegisterToLottery(
            String token,
            int eventId
    ) {
        return lotteryService.canRegisterToLottery(
                token,
                eventId
        );
    }

    public Response<String> getRole(String token) {
        return auth.getRole(token);
    }

    public int getRequiredMinAge(String token, int companyId, String eventPurchasePolicy) {
        int eventAge = extractMinAge(eventPurchasePolicy);

        int companyAge = -1;
        var companyResponse = companyService.getProductionCompany(token, companyId);
        if (companyResponse.getValue() != null) {
            companyAge = extractMinAge(companyResponse.getValue().getPurchasePolicy());
        }

        return Math.max(eventAge, companyAge);
    }

    private int extractMinAge(String policyText) {
        if (policyText == null) return -1;
        Matcher m = Pattern.compile("Minimum age: (\\d+)").matcher(policyText);
        int max = -1;
        while (m.find()) {
            int age = Integer.parseInt(m.group(1));
            if (age > max) max = age;
        }
        return max;
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