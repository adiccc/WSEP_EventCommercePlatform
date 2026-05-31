package UI.Views;

import UI.Presenters.AdminCompanyManagementPresenter;
import application.AdminService;
import application.Response;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "admin/company-management", layout = MainLayout.class)
@PageTitle("Admin Company Management")
@AnonymousAllowed
public class AdminCompanyManagementView extends VerticalLayout {

    private final AdminCompanyManagementPresenter presenter;
    private final IntegerField companyIdField = new IntegerField("Company ID");

    public AdminCompanyManagementView(AdminService adminService) {
        this.presenter = new AdminCompanyManagementPresenter(adminService);

        setPadding(true);
        setSpacing(true);

        buildPage();
    }

    private void buildPage() {
        H2 title = new H2("System Admin Company Management");

        Paragraph subtitle = new Paragraph(
                "Close production companies by company ID. Closing a company cancels its events and starts refund handling."
        );
        subtitle.getStyle().set("color", "#64748b");

        companyIdField.setMin(1);
        companyIdField.setStepButtonsVisible(true);
        companyIdField.setWidth("220px");

        Button closeCompanyButton = new Button("Close Company", event -> closeCompany());
        closeCompanyButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        add(title, subtitle, companyIdField, closeCompanyButton);
    }

    private void closeCompany() {
        Integer companyId = companyIdField.getValue();

        if (companyId == null || companyId < 1) {
            showError("Please enter a valid company ID.");
            return;
        }

        Response<Boolean> response = presenter.closeCompanyByAdmin(getToken(), companyId);

        if (Boolean.TRUE.equals(response.getValue())) {
            showSuccess(response.getMessage());
            companyIdField.clear();
        } else {
            showError(response.getMessage());
        }
    }

    private String getToken() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        return (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(
                message != null ? message : "Company closed successfully.",
                3000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(
                message != null ? message : "An error occurred.",
                4000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}