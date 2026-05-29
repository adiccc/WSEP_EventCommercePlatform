package UI.Views;

import DTO.NotifyType;
import DTO.QueueEntryResultDTO;
import application.ActiveOrderService;
import application.CompanyService;
import application.IAuth;
import application.Response;
import application.UserService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import DTO.NotifyDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.shared.Registration;
import infrastructure.Broadcaster;

@AnonymousAllowed
public class MainLayout extends AppLayout implements RouterLayout, BeforeEnterObserver {

    private final IAuth auth;
    private final UserService userService;
    private final CompanyService companyService;
    private Registration userBroadcasterRegistration;
    private Registration tabBroadcasterRegistration;
    private final ActiveOrderService activeOrderService;

    public MainLayout(IAuth auth, UserService userService, ActiveOrderService activeOrderService, CompanyService companyService) {
        this.auth = auth;
        this.userService = userService;
        this.activeOrderService = activeOrderService;
        this.companyService = companyService;

        registerToBroadcaster();
        createHeader();
        createDrawer();
        registerBrowserCloseHandler();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Boolean webQueueAdmitted =
                (Boolean) VaadinSession.getCurrent().getAttribute("webQueueAdmitted_" + getCurrentTabId());

        if (!Boolean.TRUE.equals(webQueueAdmitted)) {
            event.rerouteTo("");
            return;
        }

        cancelEventQueueIfNeeded(event);
    }

    private String getCurrentTabId() {
        UI ui = UI.getCurrent();

        if (ui == null) {
            return null;
        }

        return ui.getElement().getProperty("currentTabId");
    }

    private void cancelEventQueueIfNeeded(BeforeEnterEvent event) {
        String tabId = getCurrentTabId();

        if (tabId == null || tabId.isBlank()) {
            return;
        }

        String eventQueueToken = (String) VaadinSession.getCurrent()
                .getAttribute("eventQueueTabId_" + tabId);

        Integer eventId = (Integer) VaadinSession.getCurrent()
                .getAttribute("eventQueueEventId_" + tabId);

        if (eventQueueToken == null || eventQueueToken.isBlank() || eventId == null) {
            return;
        }

        String targetPath = event.getLocation().getPath();

        String purchasePath = "purchase/"
                + VaadinSession.getCurrent().getAttribute("eventQueueCompanyId_" + tabId)
                + "/"
                + eventId;

        String waitingPrefix = "waiting/"
                + VaadinSession.getCurrent().getAttribute("eventQueueCompanyId_" + tabId)
                + "/"
                + eventId;

        if (targetPath.equals(purchasePath) || targetPath.startsWith(waitingPrefix)) {
            return;
        }

        activeOrderService.cancelEventQueueEntry(eventQueueToken, eventId);

        VaadinSession.getCurrent().setAttribute("eventQueueTabId_" + tabId, null);
        VaadinSession.getCurrent().setAttribute("eventQueueCompanyId_" + tabId, null);
        VaadinSession.getCurrent().setAttribute("eventQueueEventId_" + tabId, null);
        VaadinSession.getCurrent().setAttribute("eventQueueAdmitted_" + tabId, null);

        UI.getCurrent().getPage().executeJs(
                """
                sessionStorage.removeItem("eventCommerceEventQueueToken");
                sessionStorage.removeItem("eventCommerceEventQueueEventId");
                """
        );
    }

    private void registerBrowserCloseHandler() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");

        if (tabId == null || tabId.isBlank()) {
            return;
        }

        String token = (String) VaadinSession.getCurrent()
                .getAttribute("token_" + tabId);

        if (token == null || token.isBlank()) {
            return;
        }

        UI.getCurrent().getPage().executeJs(
                """
                window.__eventCommerceAuthToken = $0;
    
                if (!window.__eventCommerceCloseTabHandlerRegistered) {
                    window.__eventCommerceCloseTabHandlerRegistered = true;
    
                    window.addEventListener("beforeunload", function () {
                        const token = window.__eventCommerceAuthToken;
    
                        const eventQueueToken = sessionStorage.getItem("eventCommerceEventQueueToken");
                        const eventQueueEventId = sessionStorage.getItem("eventCommerceEventQueueEventId");
                
                        if (eventQueueToken && eventQueueEventId) {
                            navigator.sendBeacon(
                                "/api/session/cancel-event-queue",
                                new Blob(
                                    [eventQueueToken + ":" + eventQueueEventId],
                                    { type: "text/plain" }
                                )
                            );
                        }
                        navigator.sendBeacon(
                            "/api/session/close-tab",
                            new Blob([token], { type: "text/plain" })
                        );
                    });
                }
                """,
                token
        );
    }

    private void registerToBroadcaster() {
        UI ui = UI.getCurrent();

        if (ui == null) {
            return;
        }

        String tabId = ui.getElement().getProperty("currentTabId");

        if (tabId == null || tabId.isBlank()) {
            return;
        }

        String userIdentifier =
                (String) VaadinSession.getCurrent()
                        .getAttribute("notificationUserIdentifier_" + tabId);

        String token =
                (String) VaadinSession.getCurrent()
                        .getAttribute("token_" + tabId);

        if (userIdentifier != null && !userIdentifier.isBlank()) {
            userBroadcasterRegistration = Broadcaster.registerUser(userIdentifier, notification -> {
                ui.access(() -> handleNotification(notification));
            });
        }

        if (token != null && !token.isBlank()) {
            tabBroadcasterRegistration = Broadcaster.registerTab(token, notification -> {
                ui.access(() -> handleNotification(notification));
            });
        }

        addDetachListener(event -> unregisterFromBroadcaster());
    }

    private void unregisterFromBroadcaster() {
        if (userBroadcasterRegistration != null) {
            userBroadcasterRegistration.remove();
            userBroadcasterRegistration = null;
        }
        if (tabBroadcasterRegistration != null) {
            tabBroadcasterRegistration.remove();
            tabBroadcasterRegistration = null;
        }
    }

    private void handleNotification(NotifyDTO notification) {
        if (notification == null || notification.getType() == null) {
            showNotification(notification);
            return;
        }

        switch (notification.getType()) {
            case GENERAL_POPUP -> showNotification(notification);
            case TOKEN_EXPIRED -> handleTokenExpired();
            case KICKOUT_TAB_NAVIGATION -> handleKickout(notification);
            case ROLE_APPOINTMENT_REQUEST -> showAppointmentDialog(notification);
            default -> { }
        }
    }

    private void handleTokenExpired() {
        UI ui = UI.getCurrent();

        if (ui == null) {
            return;
        }

        String tabId = ui.getElement().getProperty("currentTabId");

        if (tabId != null && !tabId.isBlank()) {
            if (isTokenExpiredAlreadyHandled(tabId)) {
                return;
            }

            markTokenExpiredHandled(tabId);
        }

        Notification notification = Notification.show(
                "Your session has expired. Please start again.",
                4000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);

        if (tabId != null && !tabId.isBlank()) {
            VaadinSession session = VaadinSession.getCurrent();

            session.setAttribute("token_" + tabId, null);
            session.setAttribute("notificationUserIdentifier_" + tabId, null);

            session.setAttribute("eventQueueTabId_" + tabId, null);
            session.setAttribute("eventQueueCompanyId_" + tabId, null);
            session.setAttribute("eventQueueEventId_" + tabId, null);
            session.setAttribute("eventQueueAdmitted_" + tabId, null);
        }

        ui.getPage().executeJs(
                """
                sessionStorage.removeItem("eventCommerceEventQueueToken");
                sessionStorage.removeItem("eventCommerceEventQueueEventId");
                """
        );

        ui.navigate("");
    }

    private boolean isTokenExpiredAlreadyHandled(String tabId) {
        Boolean handled = (Boolean) VaadinSession.getCurrent()
                .getAttribute("tokenExpiredHandled_" + tabId);

        return Boolean.TRUE.equals(handled);
    }

    private void markTokenExpiredHandled(String tabId) {
        VaadinSession.getCurrent()
                .setAttribute("tokenExpiredHandled_" + tabId, true);
    }

    private void handleKickout(NotifyDTO notification) {
        String message = getMessageOrDefault(notification, "Your access to this company has been revoked.");
        Integer companyId = notification.getPayload() != null ? notification.getPayload().getCompanyId() : null;

        if (companyId != null) {
            UI ui = UI.getCurrent();
            if (ui != null) {
                String currentPath = ui.getInternals().getActiveViewLocation().getPath();
                if (currentPath.startsWith("company/" + companyId)) {
                    ui.navigate("my-companies");
                }
            }
        }
        showError(message);
    }

    private void showAppointmentDialog(NotifyDTO notification) {
        String message = getMessageOrDefault(notification, "You have a new role appointment request.");
        Integer companyId = notification.getPayload() != null ? notification.getPayload().getCompanyId() : null;

        if (companyId == null) {
            showNotification(notification);
            return;
        }

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Role Appointment Request");
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);

        Paragraph msg = new Paragraph(message);

        int resolvedCompanyId = companyId;

        String tabId2 = UI.getCurrent().getElement().getProperty("currentTabId");
        String userEmail = (String) VaadinSession.getCurrent().getAttribute("notificationUserIdentifier_" + tabId2);

        Button acceptBtn = new Button("Accept", e -> {
            var ownerRes = companyService.respondToOwnerAppointment(token, resolvedCompanyId, true);
            if (ownerRes.getValue() == null) {
                companyService.respondToManagerAppointment(token, resolvedCompanyId, true);
            }
            if (userEmail != null && !userEmail.isBlank()) {
                userService.cleanDelayedNotifications(userEmail);
            }
            dialog.close();
            showSuccess("You have accepted the appointment.");
        });
        acceptBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

        Button rejectBtn = new Button("Reject", e -> {
            var ownerRes = companyService.respondToOwnerAppointment(token, resolvedCompanyId, false);
            if (ownerRes.getValue() == null) {
                companyService.respondToManagerAppointment(token, resolvedCompanyId, false);
            }
            if (userEmail != null && !userEmail.isBlank()) {
                userService.cleanDelayedNotifications(userEmail);
            }
            dialog.close();
            showError("You have rejected the appointment.");
        });
        rejectBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        HorizontalLayout buttons = new HorizontalLayout(acceptBtn, rejectBtn);
        dialog.add(msg, buttons);
        dialog.open();
    }

    private void showNotification(NotifyDTO notification) {
        String message = "New notification";

        if (notification != null
                && notification.getPayload() != null
                && notification.getPayload().getMessage() != null
                && !notification.getPayload().getMessage().isBlank()) {
            message = notification.getPayload().getMessage();
        }

        Notification vaadinNotification = Notification.show(
                message,
                5000,
                Notification.Position.TOP_CENTER
        );

        vaadinNotification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
    }

    private void createHeader() {
        H1 logo = new H1("EventCommerce");
        logo.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0");

        DrawerToggle toggle = new DrawerToggle();

        HorizontalLayout header = new HorizontalLayout(toggle, logo);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.setPadding(true);

        addToNavbar(header);
    }

    private void createDrawer() {
        SideNav nav = new SideNav();

        // Always visible
        SideNavItem home = new SideNavItem("Home", "home");
        home.setPrefixComponent(VaadinIcon.HOME.create());

        SideNavItem search = new SideNavItem("Search Events", "search");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());

        nav.addItem(home, search);

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);
        // No token -> anonymous
        if (token == null || token.isBlank()) {
            addLoginItem(nav);
            addToDrawer(nav);
            return;
        }

        Response<String> roleResponse = auth.getRole(token);
        String role = roleResponse.getValue();

        if ("MEMBER".equals(role)) {
            // Registered member
            SideNavItem orders = new SideNavItem("My Orders", "my-orders");
            orders.setPrefixComponent(VaadinIcon.TICKET.create());

            SideNavItem notifications = new SideNavItem("Notifications", "notifications");
            notifications.setPrefixComponent(VaadinIcon.ENVELOPE.create());

            SideNavItem company = new SideNavItem("My Companies", "my-companies");
            company.setPrefixComponent(VaadinIcon.OFFICE.create());

            SideNavItem logout = new SideNavItem("Logout", "logout");
            logout.setPrefixComponent(VaadinIcon.SIGN_IN.create());

            nav.addItem(orders, notifications, company, logout);

        } else if ("GUEST".equals(role)) {
            // Guest can still choose to log in
            addLoginItem(nav);

        } else {
            VaadinSession.getCurrent().setAttribute("token_" + tabId, null);
            addLoginItem(nav);
        }

        addToDrawer(nav);
    }

    private void addLoginItem(SideNav nav) {
        SideNavItem login = new SideNavItem("Sign in", "login");
        login.setPrefixComponent(VaadinIcon.SIGN_IN.create());
        nav.addItem(login);
    }

    private String getMessageOrDefault(NotifyDTO notification, String defaultMessage) {
        if (notification == null
                || notification.getPayload() == null
                || notification.getPayload().getMessage() == null
                || notification.getPayload().getMessage().isBlank()) {
            return defaultMessage;
        }

        return notification.getPayload().getMessage();
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(
                message,
                3000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(
                message,
                4000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}