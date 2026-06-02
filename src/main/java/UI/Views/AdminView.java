package UI.Views;

import UI.Presenters.AdminPresenter;
import application.AdminService;
import application.IAuth;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.SuspensionDTO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "admin", layout = MainLayout.class)
@PageTitle("Admin Panel — EventCommerce")
@AnonymousAllowed
public class AdminView extends VerticalLayout implements BeforeEnterObserver {

    private final AdminPresenter presenter;
    private final IAuth auth;

    public AdminView(AdminService adminService, IAuth auth) {
        this.presenter = new AdminPresenter(adminService);
        this.auth = auth;
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        if (!Boolean.TRUE.equals(auth.isAdmin(token).getValue())) {
            event.rerouteTo("home");
            return;
        }

        buildPage(token);
    }

    private void buildPage(String token) {
        removeAll();

        H2 title = new H2("🔧 Admin Panel");
        title.getStyle().set("margin-bottom", "0");

        Tab suspendTab   = new Tab("Suspend User");
        Tab unsuspendTab = new Tab("Unsuspend User");
        Tab removeTab    = new Tab("Remove User");
        Tab listTab      = new Tab("Suspended Users");

        Tabs tabs =
                new Tabs(
                        suspendTab,
                        unsuspendTab,
                        removeTab,
                        listTab
                );

        tabs.setWidthFull();

        VerticalLayout suspendContent   = buildSuspendSection(token);
        VerticalLayout unsuspendContent = buildUnsuspendSection(token);
        VerticalLayout removeContent    = buildRemoveUserSection(token);
        VerticalLayout listContent      = buildSuspendedListSection(token);

        unsuspendContent.setVisible(false);
        removeContent.setVisible(false);
        listContent.setVisible(false);

        tabs.addSelectedChangeListener(e -> {
            suspendContent.setVisible(tabs.getSelectedTab() == suspendTab);
            unsuspendContent.setVisible(tabs.getSelectedTab() == unsuspendTab);
            removeContent.setVisible(tabs.getSelectedTab() == removeTab);
            listContent.setVisible(tabs.getSelectedTab() == listTab);
        });

        add(
                title,
                tabs,
                suspendContent,
                unsuspendContent,
                removeContent,
                listContent
        );
    }

    // ── Suspend User ──────────────────────────────────────────────────────────

    private VerticalLayout buildSuspendSection(String token) {
        VerticalLayout layout = sectionLayout();

        IntegerField userIdField = new IntegerField("User ID");
        userIdField.setMin(1);
        userIdField.setWidth("14rem");
        userIdField.setHelperText("Enter the numeric user ID to suspend");

        Checkbox temporaryCheck = new Checkbox("Temporary suspension");

        IntegerField daysField = new IntegerField("Duration (days)");
        daysField.setMin(1);
        daysField.setWidth("12rem");
        daysField.setVisible(false);
        daysField.setHelperText("Number of days");

        temporaryCheck.addValueChangeListener(e -> daysField.setVisible(e.getValue()));

        Button suspendBtn = new Button("🚫 Suspend User", e -> {
            Integer userId = userIdField.getValue();
            if (userId == null || userId < 1) {
                showError("Please enter a valid user ID.");
                return;
            }
            if (temporaryCheck.getValue()) {
                Integer days = daysField.getValue();
                if (days == null || days < 1) {
                    showError("Please enter a valid number of days.");
                    return;
                }
                var res = presenter.suspendUserTemporary(token, userId, days);
                handleResult(res, "User " + userId + " suspended for " + days + " day(s).");
            } else {
                var res = presenter.suspendUserPermanent(token, userId);
                handleResult(res, "User " + userId + " permanently suspended.");
            }
            userIdField.clear();
            daysField.clear();
            temporaryCheck.setValue(false);
        });
        suspendBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        layout.add(new Paragraph("Suspend a user by their numeric ID. Choose permanent or temporary."),
                userIdField, temporaryCheck, daysField, suspendBtn);
        return layout;
    }

    // ── Unsuspend User ────────────────────────────────────────────────────────

    private VerticalLayout buildUnsuspendSection(String token) {
        VerticalLayout layout = sectionLayout();

        IntegerField userIdField = new IntegerField("User ID");
        userIdField.setMin(1);
        userIdField.setWidth("14rem");
        userIdField.setHelperText("Enter the numeric user ID to unsuspend");

        Button unsuspendBtn = new Button("✅ Unsuspend User", e -> {
            Integer userId = userIdField.getValue();
            if (userId == null || userId < 1) {
                showError("Please enter a valid user ID.");
                return;
            }
            var res = presenter.unsuspendUser(token, userId);
            handleResult(res, "User " + userId + " has been unsuspended.");
            userIdField.clear();
        });
        unsuspendBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);

        layout.add(new Paragraph("Remove an active suspension from a user."),
                userIdField, unsuspendBtn);
        return layout;
    }

    // ── Remove user ──────────────────────────────────────────────────
    private VerticalLayout buildRemoveUserSection(String token) {

        VerticalLayout layout = sectionLayout();

        IntegerField userIdField =
                new IntegerField("User ID");

        userIdField.setMin(1);
        userIdField.setWidth("14rem");

        userIdField.setHelperText(
                "Enter the numeric user ID to remove"
        );

        Button removeButton =
                new Button(
                        "🗑 Remove User",
                        e -> openRemoveUserDialog(
                                token,
                                userIdField
                        )
                );

        removeButton.addThemeVariants(
                ButtonVariant.LUMO_ERROR,
                ButtonVariant.LUMO_PRIMARY
        );

        layout.add(
                new Paragraph(
                        "Remove a member from the platform. Founders cannot be removed."
                ),
                userIdField,
                removeButton
        );

        return layout;
    }

    private void openRemoveUserDialog(
            String token,
            IntegerField userIdField
    ) {

        Integer userId = userIdField.getValue();

        if (userId == null || userId < 1) {
            showError("Please enter a valid user ID.");
            return;
        }

        Dialog dialog = new Dialog();

        dialog.setHeaderTitle("Remove User");

        Span warning = new Span(
                "Are you sure you want to permanently remove user #"
                        + userId
                        + "?\nThis action deactivates the account and may reassign company/event ownership."
        );

        warning.getStyle()
                .set("white-space", "pre-line")
                .set("color", "#b91c1c")
                .set("font-weight", "600");

        Button cancelButton =
                new Button("Cancel", e -> dialog.close());

        Button confirmButton =
                new Button("Remove User", e -> {

                    dialog.close();

                    var response =
                            presenter.removeUser(
                                    token,
                                    userId
                            );

                    if (Boolean.TRUE.equals(response.getValue())) {

                        showSuccess(
                                "User "
                                        + userId
                                        + " removed successfully."
                        );

                        userIdField.clear();

                        return;
                    }

                    showError(response.getMessage());
                });

        confirmButton.addThemeVariants(
                ButtonVariant.LUMO_ERROR,
                ButtonVariant.LUMO_PRIMARY
        );

        HorizontalLayout actions =
                new HorizontalLayout(
                        cancelButton,
                        confirmButton
                );

        dialog.add(warning);

        dialog.getFooter().add(actions);

        dialog.open();
    }

    // ── Suspended Users List ──────────────────────────────────────────────────

    private VerticalLayout buildSuspendedListSection(String token) {
        VerticalLayout layout = sectionLayout();

        Grid<SuspensionDTO> grid = new Grid<>(SuspensionDTO.class, false);
        grid.addColumn(SuspensionDTO::getUserId)
                .setHeader("User ID").setSortable(true).setFlexGrow(0).setWidth("110px");
        grid.addColumn(s -> formatDateTime(s.getStartDate()))
                .setHeader("Suspended At").setSortable(true).setFlexGrow(1);
        grid.addColumn(s -> s.getEndDate() == null ? "Permanent" : formatDateTime(s.getEndDate()))
                .setHeader("Expires At").setSortable(true).setFlexGrow(1);
        grid.addComponentColumn(s -> {
            boolean active = s.getEndDate() == null || s.getEndDate().isAfter(LocalDateTime.now());
            Span badge = new Span(active ? "● Active" : "● Expired");
            badge.getStyle()
                    .set("font-size", "0.78rem")
                    .set("padding", "2px 8px")
                    .set("border-radius", "1rem")
                    .set("background", active
                            ? "var(--lumo-error-color-10pct)"
                            : "var(--lumo-contrast-5pct)")
                    .set("color", active
                            ? "var(--lumo-error-text-color)"
                            : "var(--lumo-secondary-text-color)");
            return badge;
        }).setHeader("Status").setFlexGrow(0).setWidth("110px");
        grid.setWidthFull();
        grid.setHeight("400px");

        Button refreshBtn = new Button("🔄 Refresh", e -> {
            var res = presenter.getAllSuspensions(token);
            if (res.getValue() != null) {
                grid.setItems(res.getValue());
            } else {
                showError(res.getMessage());
            }
        });
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        // Load immediately when section is shown
        var res = presenter.getAllSuspensions(token);
        if (res.getValue() != null) {
            grid.setItems(res.getValue());
        }

        HorizontalLayout header = new HorizontalLayout(
                new Paragraph("All suspension records (active and expired)."), refreshBtn);
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        layout.add(header, grid);
        return layout;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VerticalLayout sectionLayout() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("margin-top", "0.5rem");
        return layout;
    }

    private void handleResult(application.Response<Boolean> res, String successMessage) {
        if (Boolean.TRUE.equals(res.getValue())) {
            showSuccess(successMessage);
        } else {
            showError(res.getMessage());
        }
    }

    private String formatDateTime(LocalDateTime dt) {
        if (dt == null) return "—";
        return dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
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
