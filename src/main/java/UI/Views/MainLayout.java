package UI.Views;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Shared shell for all authenticated pages.
 *
 * How to attach a view to this layout:
 *   @Route(value = "my-page", layout = MainLayout.class)
 *   public class MyView extends VerticalLayout { ... }
 *
 * Nav items use string paths for now. Once a view class exists you can
 * switch to the class-reference constructor: new SideNavItem("Label", MyView.class, icon)
 */
@AnonymousAllowed
public class MainLayout extends AppLayout implements RouterLayout {

    public MainLayout() {
        createHeader();
        createDrawer();
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

        // String paths used here so the file compiles even before other views exist.
        // Replace with class-reference constructors as you implement each view:
        //   new SideNavItem("Home", HomeView.class, VaadinIcon.HOME.create())

        SideNavItem home = new SideNavItem("Home", "");
        home.setPrefixComponent(VaadinIcon.HOME.create());

        SideNavItem search = new SideNavItem("Search Events", "search");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());

        SideNavItem orders = new SideNavItem("My Orders", "my-orders");
        orders.setPrefixComponent(VaadinIcon.TICKET.create());

        SideNavItem company = new SideNavItem("My Company", "manage");
        company.setPrefixComponent(VaadinIcon.OFFICE.create());

        SideNavItem login = new SideNavItem("Login", "login");
        login.setPrefixComponent(VaadinIcon.SIGN_IN.create());

        nav.addItem(home, search, orders, company, login);
        addToDrawer(nav);
    }
}
