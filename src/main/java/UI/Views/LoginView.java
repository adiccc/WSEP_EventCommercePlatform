package UI.Views;

import UI.Presenters.LoginPresenter;
import application.Response;
import application.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Login screen — accessible without authentication.
 *
 * MVP pattern (lecture style):
 *   View     → builds UI, calls presenter methods, handles the returned Response
 *   Presenter → holds services only, returns data — never touches UI components
 */
@Route("login")                  // accessible at http://localhost:8080/login
@PageTitle("Login — EventCommerce")
@AnonymousAllowed               // no auth required to see this page
public class LoginView extends VerticalLayout {

    private final LoginPresenter presenter;

    // Spring injects UserService (declared as @Bean in ServiceConfig)
    public LoginView(UserService userService) {
        this.presenter = new LoginPresenter(userService);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H2 title = new H2("Sign in");

        EmailField emailField = new EmailField("Email");
        emailField.setWidth("20rem");
        emailField.setRequired(true);

        PasswordField passwordField = new PasswordField("Password");
        passwordField.setWidth("20rem");
        passwordField.setRequired(true);

        Button loginButton = new Button("Sign in", e -> {
            // View calls presenter → gets Response back → handles UI itself
            Response<String> response = presenter.login(emailField.getValue(), passwordField.getValue());
            if (response.getValue() != null) {
                VaadinSession.getCurrent()
                        .setAttribute("token", response.getValue());
                getUI().ifPresent(ui -> ui.navigate(""));
            } else {
                Notification notification = Notification.show(
                        response.getMessage(), 3000, Notification.Position.TOP_CENTER);
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        loginButton.setWidth("20rem");
        loginButton.getElement().setAttribute("theme", "primary");

        Button registerLink = new Button("No account? Register", e ->
                getUI().ifPresent(ui -> ui.navigate("register"))
        );
        registerLink.getElement().setAttribute("theme", "tertiary");

        add(title, emailField, passwordField, loginButton, registerLink);
    }
}
