package UI.Views;

import UI.Presenters.RegisterPresenter;
import application.UserService;
import application.Response;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.UserDTO;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.server.VaadinSession;

import java.time.LocalDate;

@Route("register")
@PageTitle("Register")
@AnonymousAllowed
public class RegisterView extends VerticalLayout implements BeforeEnterObserver {

    private final RegisterPresenter presenter;

    public RegisterView(UserService userService) {
        this.presenter = new RegisterPresenter(userService);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setJustifyContentMode(JustifyContentMode.START);

        H2 title = new H2("Create Account");

        TextField firstNameField = new TextField("First Name");
        firstNameField.setWidth("20rem");

        TextField lastNameField = new TextField("Last Name");
        lastNameField.setWidth("20rem");

        EmailField emailField = new EmailField("Email");
        emailField.setWidth("20rem");

        PasswordField passwordField = new PasswordField("Password");
        passwordField.setWidth("20rem");

        TextField address = new TextField("Address");
        address.setWidth("20rem");

        TextField phoneNumber = new TextField("Phone Number");
        phoneNumber.setWidth("20rem");

        DatePicker birthDatePicker = new DatePicker("Date of Birth");
        birthDatePicker.setWidth("20rem");
        birthDatePicker.setMax(LocalDate.now());
        birthDatePicker.setMin(LocalDate.now().minusYears(120));

        Button registerButton = new Button("Register", e -> {

            LocalDate birthDate = birthDatePicker.getValue();

            UserDTO userDTO = new UserDTO(
                    emailField.getValue(),
                    firstNameField.getValue(),
                    lastNameField.getValue(),
                    passwordField.getValue(),
                    birthDate.getDayOfMonth(),
                    birthDate.getMonthValue(),
                    birthDate.getYear(),
                    address.getValue(),
                    phoneNumber.getValue()
            );

            Response<Boolean> response = presenter.register(userDTO);

            if (response.getValue() != null) {
                showSuccess(response.getMessage());
                getUI().ifPresent(ui -> ui.navigate("login"));
            } else {
                showError(response.getMessage());
            }
        });

        registerButton.setWidth("20rem");

        Button loginButton = new Button(
                "Already have an account? Login",
                e -> getUI().ifPresent(ui -> ui.navigate("login"))
        );
        loginButton.setWidth("20rem");
        VerticalLayout form = new VerticalLayout();
        form.setAlignItems(Alignment.CENTER);
        form.setWidth("100%");
        form.add(
                firstNameField,
                lastNameField,
                emailField,
                passwordField,
                birthDatePicker,
                address,
                phoneNumber,
                registerButton,
                loginButton
        );

        add(title, form);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Boolean webQueueAdmitted =
                (Boolean) VaadinSession.getCurrent().getAttribute("webQueueAdmitted");

        if (!Boolean.TRUE.equals(webQueueAdmitted)) {
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