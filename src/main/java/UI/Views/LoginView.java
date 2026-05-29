package UI.Views;

import UI.Presenters.LoginPresenter;
import application.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import application.Response;
import com.vaadin.flow.component.UI;

@Route("login")
@PageTitle("Login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {
    private final LoginPresenter presenter;

    public LoginView(UserService userService) {
        this.presenter = new LoginPresenter(userService);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H2 title = new H2("Sign In");

        EmailField emailField = new EmailField("Email");
        emailField.setWidth("20rem");

        PasswordField passwordField = new PasswordField("Password");
        passwordField.setWidth("20rem");

        Button loginButton = new Button("Sign In", e -> {
            Response<String> response =
                    presenter.login(emailField.getValue(), passwordField.getValue());

            if (response.getValue() != null) {
                String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
                VaadinSession.getCurrent().setAttribute("token_" + tabId, response.getValue());
                VaadinSession.getCurrent().setAttribute("notificationUserIdentifier_" + tabId, emailField.getValue());
                VaadinSession.getCurrent().setAttribute("tokenExpiredHandled_" + tabId, null);
                showSuccess(response.getMessage());

                getUI().ifPresent(ui -> ui.navigate("home"));
            } else {
                showError(response.getMessage());
            }
        });

        loginButton.setWidth("20rem");

        Button registerButton = new Button(
                "No account? Register",
                e -> getUI().ifPresent(ui -> ui.navigate("register"))
        );

        Button guestButton = new Button("Continue as Guest", e -> {
            Response<String> response = presenter.continueAsGuest();

            if (response.getValue() != null) {
                String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
                VaadinSession.getCurrent().setAttribute("token_" + tabId, response.getValue());
                VaadinSession.getCurrent().setAttribute(
                        "notificationUserIdentifier_" + tabId,
                        presenter.getUserIdentifier(response.getValue()).getValue()
                );
                VaadinSession.getCurrent().setAttribute("tokenExpiredHandled_" + tabId, null);
                showSuccess(response.getMessage());
                getUI().ifPresent(ui -> ui.navigate("home"));
            } else {
                showError(response.getMessage());
            }
        });

        guestButton.setWidth("20rem");
        guestButton.getElement().setAttribute("theme", "contrast");

        add(
                title,
                emailField,
                passwordField,
                loginButton,
                guestButton,
                registerButton
        );
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");

        Boolean webQueueAdmitted =
                (Boolean) VaadinSession.getCurrent()
                        .getAttribute("webQueueAdmitted_" + tabId);
        if (!Boolean.TRUE.equals(webQueueAdmitted)) {
            Notification notification = Notification.show(
                    "Please wait for your turn before signing in.",
                    4000,
                    Notification.Position.TOP_CENTER
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);

            event.rerouteTo("");
        }
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
