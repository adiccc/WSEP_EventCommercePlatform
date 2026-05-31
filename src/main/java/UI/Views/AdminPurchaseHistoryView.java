package UI.Views;

import DTO.AdminPurchaseHistoryDTO;
import UI.Presenters.AdminPurchaseHistoryPresenter;
import application.AdminService;
import application.Response;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

@Route(value = "admin/purchase-history", layout = MainLayout.class)
@PageTitle("Admin Purchase History")
@AnonymousAllowed
public class AdminPurchaseHistoryView extends VerticalLayout {

    private final AdminPurchaseHistoryPresenter presenter;
    private final Grid<AdminPurchaseHistoryDTO> grid = new Grid<>(AdminPurchaseHistoryDTO.class, false);

    private final ComboBox<ViewType> viewType = new ComboBox<>("View by");
    private final TextField purchaserField = new TextField("Purchaser Email");
    private final IntegerField companyIdField = new IntegerField("Company ID");
    private final IntegerField eventIdField = new IntegerField("Event ID");

    public AdminPurchaseHistoryView(AdminService adminService) {
        this.presenter = new AdminPurchaseHistoryPresenter(adminService);

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        buildHeader();
        buildFilters();
        buildGrid();
    }

    private void buildHeader() {
        H2 title = new H2("System Admin Purchase History");
        Paragraph subtitle = new Paragraph("View global purchase history by purchaser, company, event, or company and event.");
        subtitle.getStyle().set("color", "#64748b");

        add(title, subtitle);
    }

    private void buildFilters() {
        viewType.setItems(ViewType.values());
        viewType.setItemLabelGenerator(ViewType::getLabel);
        viewType.setValue(ViewType.PURCHASER);
        viewType.addValueChangeListener(event -> updateVisibleFields());

        purchaserField.setWidth("260px");

        companyIdField.setMin(1);
        companyIdField.setStepButtonsVisible(true);
        companyIdField.setWidth("180px");

        eventIdField.setMin(1);
        eventIdField.setStepButtonsVisible(true);
        eventIdField.setWidth("180px");

        Button searchButton = new Button("🔎 Search", event -> search());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout filters = new HorizontalLayout(
                viewType,
                purchaserField,
                companyIdField,
                eventIdField,
                searchButton
        );
        filters.setAlignItems(Alignment.END);
        filters.setWidthFull();

        VerticalLayout filterBox = new VerticalLayout(
                new H3("Filter Purchase History"),
                filters
        );
        filterBox.setPadding(true);
        filterBox.setSpacing(false);
        filterBox.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");

        add(filterBox);
        updateVisibleFields();
    }

    private void buildGrid() {
        grid.addColumn(AdminPurchaseHistoryDTO::getOrderId)
                .setHeader("Order ID")
                .setAutoWidth(true);

        grid.addColumn(AdminPurchaseHistoryDTO::getUserIdentifier)
                .setHeader("Purchaser")
                .setAutoWidth(true);

        grid.addColumn(AdminPurchaseHistoryDTO::getCompanyId)
                .setHeader("Company ID")
                .setAutoWidth(true);

        grid.addColumn(AdminPurchaseHistoryDTO::getCompanyName)
                .setHeader("Company")
                .setAutoWidth(true);

        grid.addColumn(AdminPurchaseHistoryDTO::getEventId)
                .setHeader("Event ID")
                .setAutoWidth(true);

        grid.addColumn(AdminPurchaseHistoryDTO::getEventName)
                .setHeader("Event")
                .setAutoWidth(true);

        grid.addColumn(AdminPurchaseHistoryDTO::getEventDate)
                .setHeader("Date")
                .setAutoWidth(true);

        grid.addColumn(AdminPurchaseHistoryDTO::getEventLocation)
                .setHeader("Location")
                .setAutoWidth(true);

        grid.addColumn(AdminPurchaseHistoryDTO::getStatus)
                .setHeader("Status")
                .setAutoWidth(true);

        grid.addColumn(AdminPurchaseHistoryDTO::getTicketCount)
                .setHeader("Tickets")
                .setAutoWidth(true);

        grid.addColumn(order -> formatPrice(order.getTotalSum()))
                .setHeader("Total")
                .setAutoWidth(true);

        grid.setWidthFull();
        grid.setHeight("520px");

        add(grid);
    }

    private void updateVisibleFields() {
        ViewType selected = viewType.getValue();

        purchaserField.setVisible(selected == ViewType.PURCHASER);
        companyIdField.setVisible(selected == ViewType.COMPANY || selected == ViewType.COMPANY_AND_EVENT);
        eventIdField.setVisible(selected == ViewType.EVENT || selected == ViewType.COMPANY_AND_EVENT);
    }

    private void search() {
        ViewType selected = viewType.getValue();

        if (selected == null) {
            showError("Please select a valid view type.");
            return;
        }

        List<String> usersFilter = null;
        List<Integer> eventsFilter = null;
        List<Integer> companiesFilter = null;

        switch (selected) {
            case PURCHASER -> {
                String purchaser = purchaserField.getValue();
                if (purchaser == null || purchaser.isBlank()) {
                    showError("Please enter a purchaser email.");
                    return;
                }
                usersFilter = List.of(purchaser.trim());
            }
            case COMPANY -> {
                Integer companyId = companyIdField.getValue();
                if (companyId == null || companyId < 1) {
                    showError("Please enter a valid company ID.");
                    return;
                }
                companiesFilter = List.of(companyId);
            }
            case EVENT -> {
                Integer eventId = eventIdField.getValue();
                if (eventId == null || eventId < 1) {
                    showError("Please enter a valid event ID.");
                    return;
                }
                eventsFilter = List.of(eventId);
            }
            case COMPANY_AND_EVENT -> {
                Integer companyId = companyIdField.getValue();
                Integer eventId = eventIdField.getValue();

                if (companyId == null || companyId < 1) {
                    showError("Please enter a valid company ID.");
                    return;
                }
                if (eventId == null || eventId < 1) {
                    showError("Please enter a valid event ID.");
                    return;
                }

                companiesFilter = List.of(companyId);
                eventsFilter = List.of(eventId);
            }
        }

        Response<List<AdminPurchaseHistoryDTO>> response = presenter.getGlobalOrders(
                getToken(),
                usersFilter,
                eventsFilter,
                companiesFilter
        );

        if (response.getValue() == null) {
            grid.setItems(List.of());
            showError(response.getMessage());
            return;
        }

        grid.setItems(response.getValue());

        if (response.getValue().isEmpty()) {
            showInfo(response.getMessage());
        } else {
            showSuccess("Purchase history loaded successfully.");
        }
    }

    private String getToken() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        return (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);
    }

    private String formatPrice(double price) {
        return String.format("NIS %.2f", price);
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showInfo(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
    }

    private void showError(String message) {
        Notification notification = Notification.show(
                message != null ? message : "An error occurred.",
                4000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private enum ViewType {
        PURCHASER("Purchaser"),
        COMPANY("Company"),
        EVENT("Event"),
        COMPANY_AND_EVENT("Company and Event");

        private final String label;

        ViewType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}