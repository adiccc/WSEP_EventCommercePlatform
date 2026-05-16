package UI.Views;

import UI.Presenters.RegisterPresenter;
import application.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.UserDTO;
import application.Response;

@Route("register")
@PageTitle("Register")
@AnonymousAllowed
public class RegisterView extends VerticalLayout {

    private final RegisterPresenter presenter;

    public RegisterView(UserService userService) {
        this.presenter = new RegisterPresenter(userService);

        setWidth("30rem");
        setAlignItems(Alignment.CENTER);

        H2 title = new H2("Create Account");

        TextField firstName = new TextField("First Name");
        TextField lastName = new TextField("Last Name");
        EmailField email = new EmailField("Email");
        PasswordField password = new PasswordField("Password");
        TextField phone = new TextField("Phone");
        TextField address = new TextField("Address");

        IntegerField day = new IntegerField("Day");
        IntegerField month = new IntegerField("Month");
        IntegerField year = new IntegerField("Year");

        Button registerButton = new Button("Register", e -> {
            if (email.isInvalid()) {
                showError("Please enter a valid email address");
                return;
            }

            UserDTO dto = new UserDTO(
                    email.getValue().trim(),
                    firstName.getValue().trim(),
                    lastName.getValue().trim(),
                    password.getValue(),
                    day.getValue(),
                    month.getValue(),
                    year.getValue(),
                    address.getValue().trim(),
                    phone.getValue().trim()
            );

            Response<Boolean> response = presenter.register(dto);

            if (Boolean.TRUE.equals(response.getValue())) {
                showSuccess(response.getMessage());
                getUI().ifPresent(ui -> ui.navigate("login"));
            } else {
                showError(response.getMessage());
            }
        });

        add(
                title,
                firstName,
                lastName,
                email,
                password,
                phone,
                address,
                day,
                month,
                year,
                registerButton
        );
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