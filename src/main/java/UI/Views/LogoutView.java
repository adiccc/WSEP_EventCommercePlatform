package UI.Views;

import UI.Presenters.LogoutPresenter;
import application.UserService;
import application.Response;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.component.UI;

@Route(value = "logout", layout = MainLayout.class)
@PageTitle("Logout")
public class LogoutView extends VerticalLayout {

    private final LogoutPresenter presenter;

    public LogoutView(UserService userService) {
        this.presenter = new LogoutPresenter(userService);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H2 title = new H2("Are you sure you want to log out?");

        Button logoutButton = new Button("Log Out", e -> performLogout());
        logoutButton.getElement().setAttribute("theme", "primary error");

        Button cancelButton = new Button(
                "Cancel",
                e -> getUI().ifPresent(ui -> ui.getPage().getHistory().back())
        );

        add(title, logoutButton, cancelButton);
    }

    private void performLogout() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);
        Response<Boolean> response = presenter.logout(token);

        if (Boolean.TRUE.equals(response.getValue())) {
            VaadinSession.getCurrent().setAttribute("token_" + tabId, null);
            VaadinSession.getCurrent().setAttribute("notificationUserIdentifier_" + tabId, null);
            VaadinSession.getCurrent().setAttribute("webQueueToken_" + tabId, null);
            VaadinSession.getCurrent().setAttribute("webQueueAdmitted_" + tabId, null);
            VaadinSession.getCurrent().setAttribute("eventQueueTabId_" + tabId, null);
            VaadinSession.getCurrent().setAttribute("eventQueueCompanyId_" + tabId, null);
            VaadinSession.getCurrent().setAttribute("eventQueueEventId_" + tabId, null);
            VaadinSession.getCurrent().setAttribute("eventQueueAdmitted_" + tabId, null);

            Notification notification = Notification.show(
                    response.getMessage(),
                    3000,
                    Notification.Position.TOP_CENTER
            );
            notification.addThemeVariants(
                    NotificationVariant.LUMO_SUCCESS
            );

            getUI().ifPresent(ui -> ui.navigate(""));
        } else {
            Notification notification = Notification.show(
                    response.getMessage(),
                    4000,
                    Notification.Position.TOP_CENTER
            );
            notification.addThemeVariants(
                    NotificationVariant.LUMO_ERROR
            );
        }
    }
}