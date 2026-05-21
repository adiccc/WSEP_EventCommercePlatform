package UI.Views;

import UI.Presenters.RolesPresenter;
import application.CompanyService;
import application.Response;
import application.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dataType.PermissionType;
import domain.dto.RolesPermissionsTreeDTO;

import java.util.*;

/**
 * Roles & Permissions page for a company.
 *
 * URL pattern: /company/42/roles
 *
 * - FOUNDER / OWNER → sees the full hierarchy tree (founder, owners, managers + permissions)
 *                      and can edit permissions, remove managers, appoint new owners/managers
 * - MANAGER         → sees only their own granted permissions
 * - Others          → access denied message
 */
@Route(value = "company/:companyId(\\d+)/roles", layout = MainLayout.class)
@PageTitle("Roles & Permissions — EventCommerce")
@AnonymousAllowed
public class RolesView extends VerticalLayout implements BeforeEnterObserver {

    private final RolesPresenter presenter;
    private int companyId;

    public RolesView(CompanyService companyService, UserService userService) {
        this.presenter = new RolesPresenter(companyService, userService);
        setPadding(true);
        setSpacing(true);
    }

    // ── Route parameter ───────────────────────────────────────────────────────

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = Integer.parseInt(
                event.getRouteParameters().get("companyId").orElse("0"));
        buildPage();
    }

    // ── Page layout ───────────────────────────────────────────────────────────

    private void buildPage() {
        removeAll();

        String token = getToken();

        // Back button
        Button back = new Button("← Back to Company",
                e -> getUI().ifPresent(ui -> ui.navigate("company/" + companyId)));
        back.getElement().setAttribute("theme", "tertiary");
        add(back);

        String role = presenter.getUserRole(token, companyId);

        if ("FOUNDER".equals(role) || "OWNER".equals(role)) {
            buildOwnerView(token);
        } else if ("MANAGER".equals(role)) {
            buildManagerView(token);
        } else {
            add(new Paragraph("You don't have permission to view this page."));
        }
    }

    // ── Owner/Founder view: full hierarchy + management actions ───────────────

    private void buildOwnerView(String token) {
        var response = presenter.getRolesTree(token, companyId);
        if (response.getValue() == null) {
            add(new Paragraph("Could not load roles: " + response.getMessage()));
            return;
        }

        RolesPermissionsTreeDTO tree = response.getValue();

        add(new H2("Roles & Permissions"));

        // ── Founder ──────────────────────────────────────────────────────────
        add(sectionHeader("👑 Founder"));
        add(userChip(presenter.getDisplayName(tree.getFounderId()), "var(--lumo-primary-color)"));

        // ── Owners ───────────────────────────────────────────────────────────
        add(sectionHeader("Owners"));
        HorizontalLayout ownersRow = new HorizontalLayout();
        ownersRow.getStyle().set("flex-wrap", "wrap");
        for (int ownerId : tree.getOwnerIds()) {
            if (ownerId != tree.getFounderId()) {
                ownersRow.add(userChip(presenter.getDisplayName(ownerId), "var(--lumo-success-color)"));
            }
        }
        if (ownersRow.getComponentCount() == 0) {
            ownersRow.add(new Span("No additional owners"));
        }
        add(ownersRow);

        // ── Managers grid with Edit / Remove actions ─────────────────────────
        add(sectionHeader("🔑 Managers & Permissions"));

        Map<Integer, Set<PermissionType>> managers = tree.getManagersPermissions();
        if (managers.isEmpty()) {
            add(new Paragraph("No managers assigned to this company."));
        } else {
            List<ManagerRow> rows = new ArrayList<>();
            for (Map.Entry<Integer, Set<PermissionType>> entry : managers.entrySet()) {
                rows.add(new ManagerRow(entry.getKey(),
                        presenter.getDisplayName(entry.getKey()), entry.getValue()));
            }

            Grid<ManagerRow> grid = new Grid<>();
            grid.addColumn(ManagerRow::displayName)
                    .setHeader("Manager").setFlexGrow(1);
            grid.addColumn(r -> formatPermissions(r.permissions()))
                    .setHeader("Permissions").setFlexGrow(3);
            grid.addComponentColumn(r -> buildManagerActions(r))
                    .setHeader("Actions").setFlexGrow(1);
            grid.setItems(rows);
            grid.setWidthFull();
            grid.setHeight("300px");
            add(grid);
        }

        // ── Appointment buttons ──────────────────────────────────────────────
        add(buildAppointmentBar());
    }

    // ── Action buttons per manager row ────────────────────────────────────────

    private HorizontalLayout buildManagerActions(ManagerRow row) {
        Button editBtn = new Button("Edit", e -> openEditPermissionsDialog(row));
        editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);

        Button removeBtn = new Button("Remove", e -> openRemoveConfirmDialog(row));
        removeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

        HorizontalLayout actions = new HorizontalLayout(editBtn, removeBtn);
        actions.setSpacing(true);
        actions.setPadding(false);
        return actions;
    }

    // ── Edit permissions dialog ───────────────────────────────────────────────

    private void openEditPermissionsDialog(ManagerRow row) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Permissions — " + row.displayName());
        dialog.setWidth("420px");

        CheckboxGroup<PermissionType> checkboxes = new CheckboxGroup<>("Permissions");
        checkboxes.setItems(PermissionType.values());
        checkboxes.setItemLabelGenerator(PermissionType::name);
        checkboxes.setValue(new HashSet<>(row.permissions()));

        VerticalLayout content = new VerticalLayout(checkboxes);
        content.setPadding(false);
        dialog.add(content);

        Button saveBtn = new Button("Save", e -> {
            Set<PermissionType> selected = checkboxes.getValue();
            if (selected == null || selected.isEmpty()) {
                showError("At least one permission must be selected.");
                return;
            }
            Response<Boolean> result = presenter.updateManagerPermissions(
                    getToken(), companyId, row.userId(), selected);
            dialog.close();
            if (result.getValue() != null && result.getValue()) {
                showSuccess("Permissions updated for " + row.displayName());
                buildPage();
            } else {
                showError(result.getMessage());
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    // ── Remove manager confirm dialog ─────────────────────────────────────────

    private void openRemoveConfirmDialog(ManagerRow row) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Remove Manager");
        confirm.setText("Are you sure you want to remove " + row.displayName()
                + " as a manager? Their sub-managers will be reassigned.");
        confirm.setCancelable(true);
        confirm.setConfirmText("Remove");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> {
            Response<Boolean> result = presenter.removeManager(getToken(), companyId, row.userId());
            if (result.getValue() != null && result.getValue()) {
                showSuccess(row.displayName() + " removed.");
                buildPage();
            } else {
                showError(result.getMessage());
            }
        });
        confirm.open();
    }

    // ── Appointment action bar ────────────────────────────────────────────────

    private HorizontalLayout buildAppointmentBar() {
        Button appointOwnerBtn = new Button("Appoint Owner",
                e -> openAppointOwnerDialog());
        appointOwnerBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button appointManagerBtn = new Button("Appoint Manager",
                e -> openAppointManagerDialog());
        appointManagerBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout bar = new HorizontalLayout(appointOwnerBtn, appointManagerBtn);
        bar.getStyle().set("padding-top", "1rem");
        return bar;
    }

    // ── Appoint owner dialog ──────────────────────────────────────────────────

    private void openAppointOwnerDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Appoint Owner");
        dialog.setWidth("360px");

        IntegerField userIdField = new IntegerField("User ID");
        userIdField.setMin(1);
        userIdField.setStepButtonsVisible(true);
        userIdField.setWidthFull();

        Span namePreview = new Span();
        namePreview.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "0.9rem");
        userIdField.addValueChangeListener(e -> {
            Integer id = e.getValue();
            namePreview.setText(id != null && id >= 1
                    ? presenter.getDisplayName(id) : "");
        });

        dialog.add(new VerticalLayout(userIdField, namePreview));

        Button sendBtn = new Button("Send Appointment", e -> {
            Integer appointeeId = userIdField.getValue();
            if (appointeeId == null || appointeeId < 1) {
                showError("Please enter a valid User ID.");
                return;
            }
            String name = presenter.getDisplayName(appointeeId);
            Response<Boolean> result = presenter.requestAppointOwner(
                    getToken(), companyId, appointeeId);
            dialog.close();
            if (result.getValue() != null && result.getValue()) {
                showSuccess("Owner appointment request sent to " + name);
            } else {
                showError(result.getMessage());
            }
        });
        sendBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelBtn, sendBtn);
        dialog.open();
    }

    // ── Appoint manager dialog ────────────────────────────────────────────────

    private void openAppointManagerDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Appoint Manager");
        dialog.setWidth("420px");

        IntegerField userIdField = new IntegerField("User ID");
        userIdField.setMin(1);
        userIdField.setStepButtonsVisible(true);
        userIdField.setWidthFull();

        Span namePreview = new Span();
        namePreview.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "0.9rem");
        userIdField.addValueChangeListener(e -> {
            Integer id = e.getValue();
            namePreview.setText(id != null && id >= 1
                    ? presenter.getDisplayName(id) : "");
        });

        CheckboxGroup<PermissionType> checkboxes = new CheckboxGroup<>("Permissions");
        checkboxes.setItems(PermissionType.values());
        checkboxes.setItemLabelGenerator(PermissionType::name);

        VerticalLayout content = new VerticalLayout(userIdField, namePreview, checkboxes);
        content.setPadding(false);
        dialog.add(content);

        Button sendBtn = new Button("Send Appointment", e -> {
            Integer appointeeId = userIdField.getValue();
            Set<PermissionType> selected = checkboxes.getValue();
            if (appointeeId == null || appointeeId < 1) {
                showError("Please enter a valid User ID.");
                return;
            }
            if (selected == null || selected.isEmpty()) {
                showError("At least one permission must be selected.");
                return;
            }
            String name = presenter.getDisplayName(appointeeId);
            Response<Boolean> result = presenter.requestAppointManager(
                    getToken(), companyId, appointeeId, selected);
            dialog.close();
            if (result.getValue() != null && result.getValue()) {
                showSuccess("Manager appointment request sent to " + name);
            } else {
                showError(result.getMessage());
            }
        });
        sendBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelBtn, sendBtn);
        dialog.open();
    }

    // ── Manager view: own permissions only ────────────────────────────────────

    private void buildManagerView(String token) {
        add(new H2("My Permissions"));

        var response = presenter.getMyPermissions(token, companyId);
        if (response.getValue() == null) {
            add(new Paragraph("Could not load permissions: " + response.getMessage()));
            return;
        }

        Set<PermissionType> perms = response.getValue();
        if (perms.isEmpty()) {
            add(new Paragraph("No specific permissions have been granted to you."));
            return;
        }

        VerticalLayout list = new VerticalLayout();
        list.setPadding(false);
        list.setSpacing(false);
        for (PermissionType p : perms) {
            list.add(permChip(p.name()));
        }
        add(list);
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    private void showSuccess(String message) {
        Notification n = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification n = Notification.show(message != null ? message : "An error occurred.",
                4000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private H3 sectionHeader(String title) {
        H3 h = new H3(title);
        h.getStyle().set("margin-top", "1rem").set("margin-bottom", "0.25rem");
        return h;
    }

    private Span userChip(String label, String color) {
        Span chip = new Span(label);
        chip.getStyle()
                .set("background", color)
                .set("color", "white")
                .set("border-radius", "1rem")
                .set("padding", "0.2rem 0.75rem")
                .set("font-size", "0.85rem")
                .set("margin", "0.2rem");
        return chip;
    }

    private Span permChip(String label) {
        Span chip = new Span(label);
        chip.getStyle()
                .set("display", "block")
                .set("padding", "0.35rem 0")
                .set("font-size", "0.9rem")
                .set("color", "var(--lumo-body-text-color)");
        return chip;
    }

    private String formatPermissions(Set<PermissionType> permissions) {
        if (permissions == null || permissions.isEmpty()) return "(none)";
        return String.join(", ", permissions.stream()
                .map(PermissionType::name)
                .sorted()
                .toList());
    }

    // ── Inner record for the managers grid ────────────────────────────────────

    private record ManagerRow(int userId, String displayName, Set<PermissionType> permissions) {}
}
