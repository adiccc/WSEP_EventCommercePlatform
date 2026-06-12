package UI.Views;

import UI.Presenters.SearchEventsPresenter;
import application.EventService;
import application.Response;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dataType.CategoryEvent;
import domain.dataType.EventSearchFilter;
import domain.dataType.GeographicalArea;
import com.vaadin.flow.component.UI;
import domain.dto.EventDTO;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.timepicker.TimePicker;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Route(value = "search", layout = MainLayout.class)
@PageTitle("Search Events")
@AnonymousAllowed
public class SearchEventsView extends VerticalLayout {

    private final SearchEventsPresenter presenter;
    private final Grid<EventDTO> grid = new Grid<>(EventDTO.class, false);

    private final TextField nameField = new TextField("Keyword");
    private final ComboBox<CategoryEvent> categoryBox = new ComboBox<>("Category");
    private final ComboBox<GeographicalArea> locationBox = new ComboBox<>("Location");
    private final DatePicker startDate = new DatePicker("Start Date");
    private final TimePicker startTime = new TimePicker("Start Time");
    private final DatePicker endDate = new DatePicker("End Date");
    private final TimePicker endTime = new TimePicker("End Time");
    private final NumberField minPrice = new NumberField("Min Price");
    private final NumberField maxPrice = new NumberField("Max Price");

    public SearchEventsView(EventService eventService) {
        this.presenter = new SearchEventsPresenter(eventService);

        setPadding(true);
        setSpacing(true);
        setWidthFull();

        H2 title = new H2("Search Events");

        configureFields();
        configureGrid();

        Button searchButton = new Button("Search", e -> search(buildFilter()));
        searchButton.getElement().setAttribute("theme", "primary");

        Button clearButton = new Button("Clear", e -> {
            nameField.clear();
            categoryBox.clear();
            locationBox.clear();
            minPrice.clear();
            maxPrice.clear();
            startDate.clear();
            startTime.clear();
            endDate.clear();
            endTime.clear();
            search(new EventSearchFilter());
        });

        HorizontalLayout row1 = new HorizontalLayout(nameField);
        row1.setDefaultVerticalComponentAlignment(Alignment.END);

        HorizontalLayout row2 = new HorizontalLayout(categoryBox, locationBox);
        row2.setDefaultVerticalComponentAlignment(Alignment.END);

        HorizontalLayout row3 = new HorizontalLayout(startDate, startTime, endDate, endTime);
        row3.setDefaultVerticalComponentAlignment(Alignment.END);

        HorizontalLayout row4 = new HorizontalLayout(minPrice, maxPrice, searchButton, clearButton);
        row4.setDefaultVerticalComponentAlignment(Alignment.END);

        add(
                title,
                row1,
                row2,
                row3,
                row4,
                grid
        );

        search(new EventSearchFilter());
    }

    private void configureFields() {
        nameField.setPlaceholder("Search event name...");
        nameField.setClearButtonVisible(true);
        nameField.setWidth("14rem");

        categoryBox.setItems(CategoryEvent.values());
        categoryBox.setClearButtonVisible(true);
        categoryBox.setWidth("12rem");

        locationBox.setItems(GeographicalArea.values());
        locationBox.setClearButtonVisible(true);
        locationBox.setWidth("12rem");

        minPrice.setMin(0);
        minPrice.setWidth("10rem");

        maxPrice.setMin(0);
        maxPrice.setWidth("10rem");

        startDate.setWidth("10rem");
        startTime.setWidth("8rem");

        endDate.setWidth("10rem");
        endTime.setWidth("8rem");

        startDate.addValueChangeListener(e -> {
            if (e.getValue() != null && startTime.getValue() == null) {
                startTime.setValue(LocalTime.of(0, 0));
            }
        });

        endDate.addValueChangeListener(e -> {
            if (e.getValue() != null && endTime.getValue() == null) {
                endTime.setValue(LocalTime.of(0, 0));
            }
        });
    }

    private void configureGrid() {
        grid.addColumn(EventDTO::getName)
                .setHeader("Event Name")
                .setFlexGrow(2)
                .setSortable(true);

        grid.addColumn(EventDTO::getCategoryEvent)
                .setHeader("Category")
                .setFlexGrow(1)
                .setSortable(true);

        grid.addColumn(EventDTO::getLocation)
                .setHeader("Area")
                .setFlexGrow(1)
                .setSortable(true);

        grid.addColumn(event -> formatDateTime(event.getEventDate()))
                .setHeader("Date")
                .setFlexGrow(1)
                .setSortable(true);

        grid.setWidthFull();
        grid.setHeight("400px");

        grid.addItemClickListener(event -> {
            EventDTO selected = event.getItem();

            if (selected != null) {
                getUI().ifPresent(ui ->
                        ui.navigate("event/" +
                                selected.getCompanyId() + "/" +
                                selected.getEventID()));
            }
        });
    }

    private String formatDateTime(String rawDateTime) {
        if (rawDateTime == null || rawDateTime.isBlank()) {
            return "";
        }

        try {
            LocalDateTime parsed = LocalDateTime.parse(rawDateTime);
            return parsed.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            return rawDateTime;
        }
    }

    private EventSearchFilter buildFilter() {
        EventSearchFilter filter = new EventSearchFilter();

        String keyword = nameField.getValue();
        filter.setKeyword(keyword == null || keyword.isBlank() ? null : keyword.trim());

        filter.setCategory(categoryBox.getValue());
        filter.setLocation(locationBox.getValue());
        filter.setStartDate(toDateTime(startDate.getValue(), startTime.getValue()));
        filter.setEndDate(toDateTime(endDate.getValue(), endTime.getValue()));
        filter.setMinPrice(minPrice.getValue());
        filter.setMaxPrice(maxPrice.getValue());

        return filter;
    }

    private LocalDateTime toDateTime(LocalDate date, LocalTime time) {
        if (date == null) {
            return null;
        }

        return LocalDateTime.of(
                date,
                time == null ? LocalTime.of(0, 0) : time
        );
    }

    private void search(EventSearchFilter filter) {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        Response<List<EventDTO>> response = presenter.search(token, filter);

        if (response.getValue() != null) {
            grid.setItems(response.getValue());
        } else {
            grid.setItems(List.of());
            showInfo(response.getMessage() != null ? response.getMessage() : "No events found.");
        }
    }

    private void showInfo(String message) {
        Notification notification = Notification.show(
                message,
                3000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
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