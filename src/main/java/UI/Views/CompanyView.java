package UI.Views;

import DTO.PurchaseRuleDTO;
import UI.Presenters.CompanyPresenter;
import application.CompanyService;
import application.EventCompanyManageService;
import application.EventService;
import application.IAuth;
import domain.dto.OrderDTO;
import domain.dto.SalesReportDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dataType.CategoryEvent;
import domain.dataType.EventSearchFilter;
import domain.dataType.GeographicalArea;
import domain.dataType.PermissionType;
import domain.policy.PurchasePolicyType;
import com.vaadin.flow.component.UI;
import domain.dto.CompanyDetailsDTO;
import domain.dto.EventDTO;

import java.util.List;
import java.util.Set;

/**
 * Company page — shows company details and its upcoming events.
 *
 * URL pattern: /company/42  (42 = companyId)
 *
 * Event list supports full EventSearchFilter:
 *   keyword, area (GeographicalArea), category (CategoryEvent),
 *   min price, max price, start date, end date.
 */
@Route(value = "company/:companyId(\\d+)", layout = MainLayout.class)
@PageTitle("Company — EventCommerce")
@AnonymousAllowed
public class CompanyView extends VerticalLayout implements BeforeEnterObserver {

    private final CompanyPresenter presenter;
    private final IAuth auth;

    // Filter fields — kept as instance variables so buildFilter() can read them
    private final TextField keywordField   = new TextField("Keyword");
    private final ComboBox<GeographicalArea> areaBox = new ComboBox<>("Area");
    private final ComboBox<CategoryEvent>    categoryBox = new ComboBox<>("Category");
    private final NumberField minPriceField = new NumberField("Min price (₪)");
    private final NumberField maxPriceField = new NumberField("Max price (₪)");
    private final DateTimePicker startDateField = new DateTimePicker("Start Date");
    private final DateTimePicker endDateField = new DateTimePicker("End Date");

    // Event grid
    private final Grid<EventDTO> eventGrid = new Grid<>(EventDTO.class, false);

    // Loaded once from the URL parameter
    private int companyId;

    public CompanyView(CompanyService companyService, EventService eventService, EventCompanyManageService eventCompanyManageService, IAuth auth) {
        this.presenter = new CompanyPresenter(companyService, eventService, eventCompanyManageService);
        this.auth = auth;
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

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        // Back button — guests/anonymous go to Search, registered members go to My Companies
        String globalRole = (token != null && !token.isBlank()) ? auth.getRole(token).getValue() : null;
        Button back;
        if ("MEMBER".equals(globalRole)) {
            back = new Button("← My Companies", e -> getUI().ifPresent(ui -> ui.navigate("my-companies")));
        } else {
            back = new Button("← Search", e -> getUI().ifPresent(ui -> ui.navigate("search")));
        }
        back.getElement().setAttribute("theme", "tertiary");

        // Load and display company header.
        // getProductionCompany requires a logged-in member (userId != -1),
        // so guests fall back to the name cached in the session by HomeView.
        var companyResponse = presenter.getCompany(token, companyId);
        if (companyResponse.getValue() != null) {
            add(back, buildCompanyHeader(companyResponse.getValue()));
        } else {
            String cachedName = (String) VaadinSession.getCurrent()
                    .getAttribute("company_name_" + companyId);
            String displayName = cachedName != null ? cachedName : "Company #" + companyId;
            add(back, buildGuestCompanyHeader(displayName));
        }

        // Show role-based management actions
        String role = presenter.getUserRole(token, companyId);
        if ("FOUNDER".equals(role) || "OWNER".equals(role)) {
            add(buildOwnerActions());
        } else if ("MANAGER".equals(role)) {
            add(buildManagerActions());
        }

        // Filter section
        add(buildFilterSection(token));

        // Events grid
        buildEventGrid();
        add(eventGrid);

        // Load all events with empty filter on first render
        loadEvents(token, new EventSearchFilter());
    }

    // ── Company header ────────────────────────────────────────────────────────

    /** Minimal header shown when the user is a guest (no access to full company details). */
    private VerticalLayout buildGuestCompanyHeader(String companyName) {
        VerticalLayout header = new VerticalLayout();
        header.setPadding(false);
        header.setSpacing(false);
        header.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "1.25rem")
                .set("margin-bottom", "0.5rem");

        H2 name = new H2(companyName);
        name.getStyle().set("margin", "0");
        header.add(name);
        return header;
    }

    private VerticalLayout buildCompanyHeader(CompanyDetailsDTO company) {
        VerticalLayout header = new VerticalLayout();
        header.setPadding(false);
        header.setSpacing(false);
        header.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "1.25rem")
                .set("margin-bottom", "0.5rem");

        H2 name = new H2(company.getCompanyName());
        name.getStyle().set("margin", "0 0 0.5rem 0");

        HorizontalLayout meta = new HorizontalLayout();
        meta.setSpacing(true);
        meta.add(metaChip("✉ " + company.getEmail()));
        meta.add(metaChip("📞 " + company.getPhone()));

        header.add(name, meta);

        String policy = company.getPurchasePolicy();
        if (policy != null && !policy.isBlank() && !"No purchase restrictions".equals(policy)) {
            Span policyChip = metaChip("📋 Purchase Policy: " + policy);
            policyChip.getStyle().set("margin-top", "0.4rem");
            header.add(policyChip);
        }

        return header;
    }

    private Span metaChip(String text) {
        Span chip = new Span(text);
        chip.getStyle()
                .set("font-size", "0.85rem")
                .set("color", "var(--lumo-secondary-text-color)");
        return chip;
    }

    // ── Role-based action bars ────────────────────────────────────────────────

    private HorizontalLayout buildOwnerActions() {
        String token = getToken();

        Button rolesBtn = new Button("👥 View Roles & Permissions",
                e -> getUI().ifPresent(ui -> ui.navigate("company/" + companyId + "/roles")));

        Button policyBtn = new Button("📋 Manage Purchase Policy",
                e -> openPurchasePolicyDialog(token));

        Button ordersBtn = new Button("📦 View Order History",
                e -> openOrderHistoryDialog(token));

        Button salesBtn = new Button("📊 Sales Report",
                e -> openSalesReportDialog(token));

        Button createEventBtn = new Button("🎫 Create Event",
                e -> getUI().ifPresent(ui ->
                        ui.navigate("company/" + companyId + "/create-event")));

        List.of(createEventBtn, rolesBtn, policyBtn, ordersBtn, salesBtn)
                .forEach(this::styleActionButton);

        HorizontalLayout bar = new HorizontalLayout(createEventBtn, rolesBtn, policyBtn, ordersBtn, salesBtn);
        bar.getStyle()
                .set("padding", "0.5rem 0")
                .set("margin-bottom", "0.25rem");
        return bar;
    }

    private HorizontalLayout buildManagerActions() {
        String token = getToken();

        Button rolesBtn = new Button("🔑 My Permissions",
                e -> getUI().ifPresent(ui -> ui.navigate("company/" + companyId + "/roles")));
        styleActionButton(rolesBtn);

        HorizontalLayout bar = new HorizontalLayout(rolesBtn);
        bar.getStyle()
                .set("padding", "0.5rem 0")
                .set("margin-bottom", "0.25rem");

        var permResponse = presenter.getMyPermissions(token, companyId);
        Set<PermissionType> perms = permResponse.getValue();

        if (perms != null && perms.contains(PermissionType.MANAGE_POLICIES)) {
            Button policyBtn = new Button("📋 Manage Purchase Policy",
                    e -> openPurchasePolicyDialog(token));
            styleActionButton(policyBtn);
            bar.add(policyBtn);
        }

        if (perms != null && perms.contains(PermissionType.VIEW_ORDERS_HISTORY)) {
            Button ordersBtn = new Button("📦 View Order History",
                    e -> openOrderHistoryDialog(token));
            styleActionButton(ordersBtn);
            bar.add(ordersBtn);
        }

        if (perms != null && perms.contains(PermissionType.GENERATE_SALES_REPORTS)) {
            Button salesBtn = new Button("📊 Sales Report",
                    e -> openSalesReportDialog(token));
            styleActionButton(salesBtn);
            bar.add(salesBtn);
        }

        if (perms != null && perms.contains(PermissionType.CREATE_EVENT)) {
            Button createEventBtn = new Button("🎫 Create Event",
                    e -> getUI().ifPresent(ui ->
                            ui.navigate("company/" + companyId + "/create-event")));
            styleActionButton(createEventBtn);
            bar.add(createEventBtn);
        }

        return bar;
    }


    // ── Filter section ────────────────────────────────────────────────────────

    private VerticalLayout buildFilterSection(String token) {
        // Configure fields
        keywordField.setPlaceholder("Search event name...");
        keywordField.setClearButtonVisible(true);
        keywordField.setWidth("14rem");

        areaBox.setItems(GeographicalArea.values());
        areaBox.setClearButtonVisible(true);
        areaBox.setWidth("12rem");

        categoryBox.setItems(CategoryEvent.values());
        categoryBox.setClearButtonVisible(true);
        categoryBox.setWidth("12rem");

        minPriceField.setMin(0);
        minPriceField.setWidth("10rem");

        maxPriceField.setMin(0);
        maxPriceField.setWidth("10rem");
        startDateField.setWidth("14rem");
        endDateField.setWidth("14rem");

        Button applyBtn = new Button("Search", e -> {
            String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
            String t = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);            loadEvents(t, buildFilter());
        });
        applyBtn.getElement().setAttribute("theme", "primary");

        Button clearBtn = new Button("Clear", e -> {
            keywordField.clear();
            areaBox.clear();
            categoryBox.clear();
            minPriceField.clear();
            maxPriceField.clear();
            String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
            String t = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);            loadEvents(t, new EventSearchFilter());
        });

        HorizontalLayout row1 = new HorizontalLayout(keywordField, areaBox, categoryBox);

        HorizontalLayout row2 = new HorizontalLayout(startDateField, endDateField);

        HorizontalLayout row3 = new HorizontalLayout(minPriceField, maxPriceField, applyBtn, clearBtn);        row1.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

        row3.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

        VerticalLayout filterBox = new VerticalLayout(
                new H3("Filter Events"), row1, row2, row3);
        filterBox.setPadding(true);
        filterBox.setSpacing(false);
        filterBox.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");

        return filterBox;
    }

    /** Reads all filter fields and builds an EventSearchFilter. */
    private EventSearchFilter buildFilter() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword(keywordField.getValue().isBlank() ? null : keywordField.getValue());
        filter.setLocation(areaBox.getValue());
        filter.setCategory(categoryBox.getValue());
        filter.setMinPrice(minPriceField.getValue());
        filter.setMaxPrice(maxPriceField.getValue());
        filter.setStartDate(startDateField.getValue());
        filter.setEndDate(endDateField.getValue());
        return filter;
    }

    // ── Events grid ───────────────────────────────────────────────────────────

    private void buildEventGrid() {
        eventGrid.addColumn(EventDTO::getName)
                .setHeader("Event Name").setFlexGrow(2).setSortable(true);
        eventGrid.addColumn(EventDTO::getCategoryEvent)
                .setHeader("Category").setFlexGrow(1).setSortable(true);
        eventGrid.addColumn(EventDTO::getLocation)
                .setHeader("Area").setFlexGrow(1).setSortable(true);
        eventGrid.addColumn(EventDTO::getEventDate)
                .setHeader("Date").setFlexGrow(1).setSortable(true);

        eventGrid.setWidthFull();
        eventGrid.setHeight("400px");

        // Click row → navigate to event details page
        eventGrid.addItemClickListener(e ->
                getUI().ifPresent(ui ->
                        ui.navigate("event/" + companyId + "/" + e.getItem().getEventID())));
    }

    private void loadEvents(String token, EventSearchFilter filter) {
        var response = presenter.searchEvents(token, companyId, filter);
        if (response.getValue() != null) {
            eventGrid.setItems(response.getValue());
        } else {
            eventGrid.setItems(List.of());
        }
    }

    // ── Purchase Policy Dialog ────────────────────────────────────────────────

    private void openPurchasePolicyDialog(String token) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Manage Purchase Policy");
        dialog.setWidth("480px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        // ── Current policy display ────────────────────────────────────────────
        H4 currentHeader = new H4("Current Policy");
        currentHeader.getStyle().set("margin-bottom", "0.25rem");

        Paragraph policyDescription = new Paragraph();
        policyDescription.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "0.6rem 1rem")
                .set("font-family", "monospace")
                .set("white-space", "pre-wrap")
                .set("width", "100%");

        Runnable refreshPolicy = () -> {
            var r = presenter.getCompany(token, companyId);
            if (r.getValue() != null && r.getValue().getPurchasePolicy() != null) {
                policyDescription.setText(r.getValue().getPurchasePolicy());
            } else {
                policyDescription.setText("No rules defined.");
            }
        };
        refreshPolicy.run();

        content.add(currentHeader, policyDescription);

        // ── Policy type selector ──────────────────────────────────────────────
        H4 typeHeader = new H4("Policy Type");
        typeHeader.getStyle().set("margin-bottom", "0.25rem");

        ComboBox<PurchasePolicyType> typeBox = new ComboBox<>("Policy Type");
        typeBox.setItems(PurchasePolicyType.values());
        typeBox.setWidthFull();

        Button changeTypeBtn = new Button("Change Policy Type", e -> {
            PurchasePolicyType selected = typeBox.getValue();
            if (selected == null) {
                showError("Please select a policy type.");
                return;
            }
            var result = presenter.changePurchasePolicyType(token, companyId, selected);
            if (result.getMessage() != null && result.getValue() == null
                    && result.getMessage().toLowerCase().contains("error")) {
                showError(result.getMessage());
            } else {
                showSuccess("Policy type changed to " + selected);
                refreshPolicy.run();
            }
        });
        changeTypeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        content.add(typeHeader, typeBox, changeTypeBtn);

        // ── Add rule ──────────────────────────────────────────────────────────
        Hr divider = new Hr();
        H4 addHeader = new H4("Add Rule");
        addHeader.getStyle().set("margin-bottom", "0.25rem");

        ComboBox<PurchaseRuleDTO.Type> ruleTypeBox = new ComboBox<>("Rule Type");
        ruleTypeBox.setItems(
                PurchaseRuleDTO.Type.MIN_AGE,
                PurchaseRuleDTO.Type.MAX_TICKETS,
                PurchaseRuleDTO.Type.MIN_TICKETS);
        ruleTypeBox.setWidthFull();

        IntegerField ruleValueField = new IntegerField("Value");
        ruleValueField.setMin(1);
        ruleValueField.setWidthFull();
        ruleValueField.setHelperText("e.g. min age = 18, max tickets = 4");

        Button addRuleBtn = new Button("Add Rule", e -> {
            PurchaseRuleDTO.Type ruleType = ruleTypeBox.getValue();
            Integer val = ruleValueField.getValue();
            if (ruleType == null) {
                showError("Please select a rule type.");
                return;
            }
            if (val == null || val < 1) {
                showError("Please enter a valid value (minimum 1).");
                return;
            }
            PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(ruleType, val);
            var result = presenter.addRuleToCompany(token, companyId, ruleDTO);
            if (result.getValue() != null && result.getValue()) {
                showSuccess("Rule added successfully.");
                ruleTypeBox.clear();
                ruleValueField.clear();
                refreshPolicy.run();
            } else {
                showError(result.getMessage());
            }
        });
        addRuleBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        content.add(divider, addHeader, ruleTypeBox, ruleValueField, addRuleBtn);

        // ── Remove rule ───────────────────────────────────────────────────────
        Hr divider2 = new Hr();
        H4 removeHeader = new H4("Remove Rule");
        removeHeader.getStyle().set("margin-bottom", "0.25rem");

        ComboBox<PurchaseRuleDTO.Type> removeTypeBox = new ComboBox<>("Rule Type");
        removeTypeBox.setItems(
                PurchaseRuleDTO.Type.MIN_AGE,
                PurchaseRuleDTO.Type.MAX_TICKETS,
                PurchaseRuleDTO.Type.MIN_TICKETS);
        removeTypeBox.setWidthFull();

        IntegerField removeValueField = new IntegerField("Value");
        removeValueField.setMin(1);
        removeValueField.setWidthFull();

        Button removeRuleBtn = new Button("Remove Rule", e -> {
            PurchaseRuleDTO.Type ruleType = removeTypeBox.getValue();
            Integer val = removeValueField.getValue();
            if (ruleType == null) {
                showError("Please select a rule type.");
                return;
            }
            if (val == null || val < 1) {
                showError("Please enter a valid value.");
                return;
            }
            PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(ruleType, val);
            var result = presenter.removeRuleFromCompany(token, companyId, ruleDTO);
            if (result.getValue() != null && result.getValue()) {
                showSuccess("Rule removed successfully.");
                removeTypeBox.clear();
                removeValueField.clear();
                refreshPolicy.run();
            } else {
                showError(result.getMessage());
            }
        });
        removeRuleBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        content.add(divider2, removeHeader, removeTypeBox, removeValueField, removeRuleBtn);

        dialog.add(content);

        Button closeBtn = new Button("Close", e -> dialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(closeBtn);

        dialog.open();
    }

    // ── Order History Dialog ──────────────────────────────────────────────────

    private void openOrderHistoryDialog(String token) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Order History");
        dialog.setWidth("750px");

        var response = presenter.getOrdersByCompany(token, companyId);
        List<OrderDTO> orders = response.getValue();

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        if (orders == null || orders.isEmpty()) {
            content.add(new Paragraph("No orders found for this company."));
        } else {
            Grid<OrderDTO> grid = new Grid<>(OrderDTO.class, false);
            grid.addColumn(OrderDTO::getOrderId).setHeader("Order ID").setSortable(true).setFlexGrow(0).setWidth("100px");
            grid.addColumn(OrderDTO::getUserIdentifier).setHeader("Customer").setSortable(true).setFlexGrow(2);
            grid.addColumn(OrderDTO::getEventId).setHeader("Event ID").setSortable(true).setFlexGrow(0).setWidth("100px");
            grid.addColumn(o -> o.getTickets().size()).setHeader("Tickets").setFlexGrow(0).setWidth("90px");
            grid.addColumn(o -> String.format("₪%.2f", o.getTotalSum())).setHeader("Total").setSortable(true).setFlexGrow(1);
            grid.addColumn(o -> o.getStatus() != null ? o.getStatus().toString() : "").setHeader("Status").setSortable(true).setFlexGrow(1);
            grid.setItems(orders);
            grid.setWidthFull();
            grid.setHeight("400px");
            content.add(grid);
        }

        dialog.add(content);
        Button closeBtn = new Button("Close", e -> dialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(closeBtn);
        dialog.open();
    }

    // ── Sales Report Dialog ───────────────────────────────────────────────────

    private void openSalesReportDialog(String token) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Sales Report");
        dialog.setWidth("700px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        var response = presenter.generateSalesReport(token, companyId);
        SalesReportDTO report = response.getValue();

        if (report == null) {
            content.add(new Paragraph(response.getMessage() != null ? response.getMessage() : "Could not generate sales report."));
        } else {
            HorizontalLayout summary = new HorizontalLayout();
            summary.setSpacing(true);
            summary.add(summaryCard("Total Revenue", String.format("₪%.2f", report.getTotalRevenue())));
            summary.add(summaryCard("Total Tickets Sold", String.valueOf(report.getTotalTicketsSold())));
            content.add(summary);

            if (report.getEventRecords() != null && !report.getEventRecords().isEmpty()) {
                content.add(new H4("Breakdown by Event"));
                Grid<domain.dto.EventSalesRecordDTO> grid = new Grid<>(domain.dto.EventSalesRecordDTO.class, false);
                grid.addColumn(domain.dto.EventSalesRecordDTO::getEventId).setHeader("Event ID").setSortable(true).setFlexGrow(0).setWidth("100px");
                grid.addColumn(domain.dto.EventSalesRecordDTO::getEventName).setHeader("Event Name").setSortable(true).setFlexGrow(2);
                grid.addColumn(domain.dto.EventSalesRecordDTO::getCreatorId).setHeader("Creator ID").setSortable(true).setFlexGrow(0).setWidth("110px");
                grid.addColumn(domain.dto.EventSalesRecordDTO::getNumTicketsSold).setHeader("Tickets Sold").setSortable(true).setFlexGrow(1);
                grid.addColumn(r -> String.format("₪%.2f", r.getRevenue())).setHeader("Revenue").setSortable(true).setFlexGrow(1);
                grid.setItems(report.getEventRecords());
                grid.setWidthFull();
                grid.setHeight("300px");
                content.add(grid);
            }
        }

        dialog.add(content);
        Button closeBtn = new Button("Close", e -> dialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(closeBtn);
        dialog.open();
    }

    private VerticalLayout summaryCard(String label, String value) {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("min-width", "160px");
        Span lbl = new Span(label);
        lbl.getStyle().set("font-size", "0.8rem").set("color", "var(--lumo-secondary-text-color)");
        Span val = new Span(value);
        val.getStyle().set("font-size", "1.4rem").set("font-weight", "bold");
        card.add(lbl, val);
        return card;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void styleActionButton(Button button) {
        button.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        button.getStyle()
                .set("height", "48px")
                .set("font-weight", "800")
                .set("border-radius", "14px")
                .set("padding-left", "1.2rem")
                .set("padding-right", "1.2rem")
                .set("box-shadow", "0 3px 10px rgba(0,0,0,0.10)");
    }

    private String getToken() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        return (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);
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
