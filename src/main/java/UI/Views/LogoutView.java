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
                e -> getUI().ifPresent(ui -> ui.navigate(""))
        );

        add(title, logoutButton, cancelButton);
    }

    private void performLogout() {
        String token = (String) VaadinSession.getCurrent()
                .getAttribute("token");

        Response<Boolean> response = presenter.logout(token);

        if (Boolean.TRUE.equals(response.getValue())) {
            VaadinSession.getCurrent().setAttribute("token", null);

            Notification notification = Notification.show(
                    response.getMessage(),
                    3000,
                    Notification.Position.TOP_CENTER
            );
            notification.addThemeVariants(
                    NotificationVariant.LUMO_SUCCESS
            );

            getUI().ifPresent(ui -> ui.navigate("login"));
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