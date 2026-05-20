package UI.Views;

import UI.Presenters.RolesPresenter;
import application.CompanyService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dataType.PermissionType;
import domain.dto.RolesPermissionsTreeDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Roles & Permissions page for a company.
 *
 * URL pattern: /company/42/roles
 *
 * - FOUNDER / OWNER → sees the full hierarchy tree (founder, owners, managers + permissions)
 * - MANAGER         → sees only their own granted permissions
 * - Others          → access denied message
 */
@Route(value = "company/:companyId(\\d+)/roles", layout = MainLayout.class)
@PageTitle("Roles & Permissions — EventCommerce")
@AnonymousAllowed
public class RolesView extends VerticalLayout implements BeforeEnterObserver {

    private final RolesPresenter presenter;
    private int companyId;

    public RolesView(CompanyService companyService) {
        this.presenter = new RolesPresenter(companyService);
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

        String token = (String) VaadinSession.getCurrent().getAttribute("token");

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
            add(new Paragraph("⛔ You don't have permission to view this page."));
        }
    }

    // ── Owner/Founder view: full hierarchy ────────────────────────────────────

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
        add(userChip("User #" + tree.getFounderId(), "var(--lumo-primary-color)"));

        // ── Owners ───────────────────────────────────────────────────────────
        add(sectionHeader("🏆 Owners"));
        HorizontalLayout ownersRow = new HorizontalLayout();
        ownersRow.setFlexWrap(HorizontalLayout.FlexWrap.WRAP);
        for (int ownerId : tree.getOwnerIds()) {
            if (ownerId != tree.getFounderId()) { // founder is already shown above
                ownersRow.add(userChip("User #" + ownerId, "var(--lumo-success-color)"));
            }
        }
        if (ownersRow.getComponentCount() == 0) {
            ownersRow.add(new Span("No additional owners"));
        }
        add(ownersRow);

        // ── Managers ─────────────────────────────────────────────────────────
        add(sectionHeader("🔑 Managers & Permissions"));

        Map<Integer, Set<PermissionType>> managers = tree.getManagersPermissions();
        if (managers.isEmpty()) {
            add(new Paragraph("No managers assigned to this company."));
        } else {
            // Build a flat list of ManagerRow records for the grid
            List<ManagerRow> rows = new ArrayList<>();
            for (Map.Entry<Integer, Set<PermissionType>> entry : managers.entrySet()) {
                rows.add(new ManagerRow(entry.getKey(), entry.getValue()));
            }

            Grid<ManagerRow> grid = new Grid<>();
            grid.addColumn(r -> "User #" + r.userId())
                    .setHeader("Manager").setFlexGrow(1);
            grid.addColumn(r -> formatPermissions(r.permissions()))
                    .setHeader("Permissions").setFlexGrow(3);
            grid.setItems(rows);
            grid.setWidthFull();
            grid.setHeight("300px");
            add(grid);
        }
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
            Span chip = permChip(p.name());
            list.add(chip);
        }
        add(list);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
        Span chip = new Span("✓ " + label);
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

    private record ManagerRow(int userId, Set<PermissionType> permissions) {}
}
