package UI.Views;

import UI.Presenters.CompanyPresenter;
import application.CompanyService;
import application.EventService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import java.time.LocalDateTime;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dataType.CategoryEvent;
import domain.dataType.EventSearchFilter;
import domain.dataType.GeographicalArea;
import domain.dto.CompanyDetailsDTO;
import domain.dto.EventDTO;

import java.util.List;

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

    public CompanyView(CompanyService companyService, EventService eventService) {
        this.presenter = new CompanyPresenter(companyService, eventService);
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
        Button back = new Button("← All Companies",
                e -> getUI().ifPresent(ui -> ui.navigate("")));
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
        return header;
    }

    private Span metaChip(String text) {
        Span chip = new Span(text);
        chip.getStyle()
                .set("font-size", "0.85rem")
                .set("color", "var(--lumo-secondary-text-color)");
        return chip;
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
            String t = (String) VaadinSession.getCurrent().getAttribute("token");
            loadEvents(t, buildFilter());
        });
        applyBtn.getElement().setAttribute("theme", "primary");

        Button clearBtn = new Button("Clear", e -> {
            keywordField.clear();
            areaBox.clear();
            categoryBox.clear();
            minPriceField.clear();
            maxPriceField.clear();
            String t = (String) VaadinSession.getCurrent().getAttribute("token");
            loadEvents(t, new EventSearchFilter());
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
}
