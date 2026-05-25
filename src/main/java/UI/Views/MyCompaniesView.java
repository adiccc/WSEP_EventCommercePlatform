package UI.Views;

import UI.Presenters.MyCompaniesPresenter;
import application.CompanyService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.CompanyDTO;

import java.util.List;

/**
 * Shows only the companies where the logged-in user has a role
 * (Founder, Owner, or Manager).
 *
 * URL: /my-companies
 */
@Route(value = "my-companies", layout = MainLayout.class)
@PageTitle("My Companies — EventCommerce")
@AnonymousAllowed
public class MyCompaniesView extends VerticalLayout {

    private final MyCompaniesPresenter presenter;

    // Same palette as HomeView for visual consistency
    private static final String[] AVATAR_COLORS = {
            "#4A90D9", "#E67E22", "#27AE60", "#8E44AD", "#E74C3C", "#16A085"
    };

    public MyCompaniesView(CompanyService companyService) {
        this.presenter = new MyCompaniesPresenter(companyService);
        setPadding(true);
        setSpacing(true);
        buildPage();
    }

    private void buildPage() {
        removeAll();

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        H2 title = new H2("My Companies");
        title.getStyle().set("margin-bottom", "0");

        Paragraph subtitle = new Paragraph(
                "Companies where you are a Founder, Owner, or Manager.");
        subtitle.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "0");

        Button createBtn = new Button("+ Create Company", e -> openCreateCompanyDialog(token));
        createBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout header = new HorizontalLayout(title, createBtn);
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setWidthFull();

        add(header, subtitle);

        var response = presenter.getMyCompanies(token);
        List<CompanyDTO> companies = response.getValue();

        if (companies == null || companies.isEmpty()) {
            add(buildEmptyState());
            return;
        }

        FlexLayout cardsContainer = new FlexLayout();
        cardsContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        cardsContainer.getStyle().set("gap", "1.25rem").set("padding", "0.5rem 0");

        for (CompanyDTO company : companies) {
            String role = presenter.getUserRole(token, company.getCompanyId());
            cardsContainer.add(buildCard(company, role));
        }
        add(cardsContainer);
    }

    // ── Card builder ─────────────────────────────────────────────────────────

    private Div buildCard(CompanyDTO company, String role) {
        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "1.25rem")
                .set("width", "260px")
                .set("cursor", "pointer")
                .set("transition", "box-shadow 0.15s ease");

        card.getElement().addEventListener("mouseover", e ->
                card.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.10)"));
        card.getElement().addEventListener("mouseout", e ->
                card.getStyle().remove("box-shadow"));

        // ── Avatar + name ────────────────────────────────────────────────────
        String color = avatarColor(company.getCompanyName());
        String initial = company.getCompanyName().substring(0, 1).toUpperCase();

        Div avatar = new Div();
        avatar.setText(initial);
        avatar.getStyle()
                .set("width", "48px").set("height", "48px")
                .set("min-width", "48px")
                .set("border-radius", "50%")
                .set("background", color + "22")
                .set("color", color)
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("font-size", "1.3rem")
                .set("font-weight", "600")
                .set("border", "2px solid " + color + "55");

        Span name = new Span(company.getCompanyName());
        name.getStyle()
                .set("font-weight", "500")
                .set("font-size", "1rem")
                .set("line-height", "1.3");

        HorizontalLayout avatarRow = new HorizontalLayout(avatar, name);
        avatarRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        avatarRow.setSpacing(true);
        avatarRow.getStyle().set("margin-bottom", "0.6rem");

        // ── Role badge ───────────────────────────────────────────────────────
        Span roleBadge = new Span(role);
        roleBadge.getStyle()
                .set("font-size", "0.75rem")
                .set("padding", "3px 10px")
                .set("border-radius", "1rem")
                .set("font-weight", "500")
                .set("background", roleBadgeColor(role))
                .set("color", "white");

        // ── Active badge ─────────────────────────────────────────────────────
        Span activeBadge = new Span(company.isActive() ? "Active" : "Inactive");
        activeBadge.getStyle()
                .set("font-size", "0.75rem")
                .set("padding", "3px 10px")
                .set("border-radius", "1rem")
                .set("background", company.isActive()
                        ? "var(--lumo-success-color-10pct)"
                        : "var(--lumo-error-color-10pct)")
                .set("color", company.isActive()
                        ? "var(--lumo-success-text-color)"
                        : "var(--lumo-error-text-color)");

        HorizontalLayout badges = new HorizontalLayout(roleBadge, activeBadge);
        badges.setSpacing(true);

        card.add(avatarRow, badges);
        card.addClickListener(e ->
                getUI().ifPresent(ui -> ui.navigate("company/" + company.getCompanyId())));

        return card;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Div buildEmptyState() {
        Div empty = new Div();
        empty.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("padding", "2rem 0");
        empty.setText("You are not a Founder, Owner, or Manager of any company yet.");
        return empty;
    }

    private String avatarColor(String companyName) {
        int index = Character.toLowerCase(companyName.charAt(0)) % AVATAR_COLORS.length;
        return AVATAR_COLORS[Math.abs(index)];
    }

    private String roleBadgeColor(String role) {
        return switch (role) {
            case "FOUNDER" -> "#4A90D9";
            case "OWNER"   -> "#27AE60";
            case "MANAGER" -> "#8E44AD";
            default        -> "#888888";
        };
    }

    // ── Create Company Dialog ─────────────────────────────────────────────────

    private void openCreateCompanyDialog(String token) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create Company");
        dialog.setWidth("440px");

        IntegerField companyIdField = new IntegerField("Company ID");
        companyIdField.setWidthFull();
        companyIdField.setMin(1);

        TextField nameField = new TextField("Company Name");
        nameField.setWidthFull();

        EmailField emailField = new EmailField("Contact Email");
        emailField.setWidthFull();

        TextField phoneField = new TextField("Phone");
        phoneField.setWidthFull();

        TextField bankField = new TextField("Bank Account");
        bankField.setWidthFull();

        VerticalLayout content = new VerticalLayout(
                companyIdField, nameField, emailField, phoneField, bankField);
        content.setPadding(false);
        dialog.add(content);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button createBtn = new Button("Create", e -> {
            Integer id = companyIdField.getValue();
            String name = nameField.getValue();
            String email = emailField.getValue();
            String phone = phoneField.getValue();
            String bank = bankField.getValue();

            if (id == null || id < 1) {
                showError("Please enter a valid Company ID.");
                return;
            }
            if (name == null || name.isBlank()) {
                showError("Company name is required.");
                return;
            }
            if (email == null || email.isBlank()) {
                showError("Contact email is required.");
                return;
            }
            if (phone == null || phone.isBlank()) {
                showError("Phone is required.");
                return;
            }
            if (bank == null || bank.isBlank()) {
                showError("Bank account is required.");
                return;
            }

            var result = presenter.createCompany(token, id, name, email, phone, bank);
            dialog.close();
            if (result.getValue() != null) {
                showSuccess("Company \"" + name + "\" created successfully.");
                buildPage();
            } else {
                showError(result.getMessage());
            }
        });
        createBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelBtn, createBtn);
        dialog.open();
    }

    private void showSuccess(String message) {
        Notification n = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification n = Notification.show(message, 4000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
