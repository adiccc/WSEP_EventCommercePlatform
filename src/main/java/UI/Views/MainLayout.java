package UI.Views;

import DTO.NotifyType;
import DTO.QueueEntryResultDTO;
import application.IAuth;
import application.Response;
import application.UserService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
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
    private Registration userBroadcasterRegistration;

    public MainLayout(IAuth auth, UserService userService) {
        this.auth = auth;
        this.userService = userService;
        registerToBroadcaster();
        createHeader();
        createDrawer();
        registerBrowserCloseHandler();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");

        Boolean webQueueAdmitted =
                (Boolean) VaadinSession.getCurrent()
                        .getAttribute("webQueueAdmitted_" + tabId);
        if (!Boolean.TRUE.equals(webQueueAdmitted)) {
            event.rerouteTo("");
        }
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
    
                        if (!token) {
                            return;
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
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");

        String userIdentifier =
                (String) VaadinSession.getCurrent()
                        .getAttribute("notificationUserIdentifier_" + tabId);

        if (userIdentifier == null || userIdentifier.isBlank()) {
            return;
        }

        UI ui = UI.getCurrent();

        userBroadcasterRegistration = Broadcaster.registerUser(userIdentifier, notification -> {
            if (ui != null) {
                ui.access(() -> handleNotification(notification));
            }
        });

        addDetachListener(event -> unregisterFromBroadcaster());
    }

    private void unregisterFromBroadcaster() {
        if (userBroadcasterRegistration != null) {
            userBroadcasterRegistration.remove();
            userBroadcasterRegistration = null;
        }
    }

    private void handleNotification(NotifyDTO notification) {
        if (notification == null || notification.getType() == null) {
            showNotification(notification);
            return;
        }

        if (notification.getType() == NotifyType.GENERAL_POPUP) {
            showNotification(notification);
        }
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

            SideNavItem company = new SideNavItem("My Company", "manage");
            company.setPrefixComponent(VaadinIcon.OFFICE.create());

            SideNavItem logout = new SideNavItem("Logout", "logout");
            logout.setPrefixComponent(VaadinIcon.SIGN_IN.create());

            nav.addItem(orders, notifications, company, logout);

        } else if ("GUEST".equals(role)) {
            // Guest can still choose to log in
            addLoginItem(nav);

        } else {
            // Invalid or expired token
            VaadinSession.getCurrent().setAttribute("token_" + tabId, null);


            Notification notification = Notification.show(
                    "Session expired. Please sign in again.",
                    4000,
                    Notification.Position.TOP_CENTER
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);

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