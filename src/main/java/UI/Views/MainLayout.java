package UI.Views;

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
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@AnonymousAllowed
public class MainLayout extends AppLayout implements RouterLayout {

    private final IAuth auth;
    private final UserService userService;

    public MainLayout(IAuth auth, UserService userService) {
        this.auth = auth;
        this.userService = userService;
        ensureToken();
        createHeader();
        createDrawer();
    }

    /**
     * If the session has no token yet, automatically obtain a guest token
     * so that all service calls work without requiring an explicit login.
     */
    private void ensureToken() {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null || token.isBlank()) {
            Response<String> r = userService.continueAsGuest();
            if (r.getValue() != null) {
                VaadinSession.getCurrent().setAttribute("token", r.getValue());
            }
        }
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
        SideNavItem home = new SideNavItem("Home", "");
        home.setPrefixComponent(VaadinIcon.HOME.create());

        SideNavItem search = new SideNavItem("Search Events", "search");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());

        nav.addItem(home, search);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");

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

            SideNavItem company = new SideNavItem("My Company", "manage");
            company.setPrefixComponent(VaadinIcon.OFFICE.create());

            SideNavItem logout = new SideNavItem("Logout", "logout");
            logout.setPrefixComponent(VaadinIcon.SIGN_IN.create());

            nav.addItem(orders, company, logout);

        } else if ("GUEST".equals(role)) {
            // Guest can still choose to log in
            addLoginItem(nav);

        } else {
            // Invalid or expired token
            VaadinSession.getCurrent().setAttribute("token", null);

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
        SideNavItem login = new SideNavItem("Login", "login");
        login.setPrefixComponent(VaadinIcon.SIGN_IN.create());
        nav.addItem(login);
    }
}