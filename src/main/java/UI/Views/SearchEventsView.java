package UI.Views;

import UI.Presenters.SearchEventsPresenter;
import application.EventService;
import application.Response;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import domain.dataType.EventSearchFilter;
import domain.dto.EventDTO;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;

import java.time.LocalDateTime;
import java.util.List;

@Route(value = "search", layout = MainLayout.class)
@PageTitle("Search Events")
public class SearchEventsView extends VerticalLayout {

    private final SearchEventsPresenter presenter;
    private final Grid<EventDTO> grid = new Grid<>(EventDTO.class, false);

    public SearchEventsView(EventService eventService) {
        this.presenter = new SearchEventsPresenter(eventService);

        H2 title = new H2("Search Events");

        TextField nameField = new TextField("Keyword");

// Category
        ComboBox<CategoryEvent> categoryBox = new ComboBox<>("Category");
        categoryBox.setItems(CategoryEvent.values());

// Location
        ComboBox<GeographicalArea> locationBox = new ComboBox<>("Location");
        locationBox.setItems(GeographicalArea.values());

// Date range
        DateTimePicker startDate = new DateTimePicker("Start Date");
        DateTimePicker endDate = new DateTimePicker("End Date");

// Price range
        NumberField minPrice = new NumberField("Min Price");
        NumberField maxPrice = new NumberField("Max Price");

        Button searchButton = new Button("Search", e ->
                search(
                        nameField.getValue(),
                        categoryBox.getValue(),
                        locationBox.getValue(),
                        startDate.getValue(),
                        endDate.getValue(),
                        minPrice.getValue(),
                        maxPrice.getValue()
                )
        );
        configureGrid();
        add(
                title,
                nameField,
                new HorizontalLayout(categoryBox, locationBox),
                new HorizontalLayout(startDate, endDate),
                new HorizontalLayout(minPrice, maxPrice),
                searchButton,
                grid
        );
        setSizeFull();
    }

    private void configureGrid() {
        grid.addColumn(EventDTO::getName).setHeader("Event Name");
        grid.addColumn(EventDTO::getCategoryEvent).setHeader("Category");

        grid.asSingleSelect().addValueChangeListener(event -> {
            EventDTO selected = event.getValue();
            if (selected != null) {
                getUI().ifPresent(ui ->
                        ui.navigate("event/" +
                                selected.getCompanyId() + "/" +
                                selected.getEventID()));
            }
        });

        grid.setSizeFull();
    }

    private void search(
            String keyword,
            CategoryEvent category,
            GeographicalArea location,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Double minPrice,
            Double maxPrice
    ) {
        String token = (String) VaadinSession.getCurrent()
                .getAttribute("token");

        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword(keyword);
        filter.setCategory(category);
        filter.setLocation(location);
        filter.setStartDate(startDate);
        filter.setEndDate(endDate);
        filter.setMinPrice(minPrice);
        filter.setMaxPrice(maxPrice);

        Response<List<EventDTO>> response = presenter.search(token, filter);

        if (response.getValue() != null) {
            grid.setItems(response.getValue());
        } else {
            grid.setItems();
            showError(response.getMessage());
        }
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