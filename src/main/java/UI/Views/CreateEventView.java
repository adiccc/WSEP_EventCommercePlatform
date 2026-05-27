package UI.Views;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import UI.Presenters.CreateEventPresenter;
import application.EventCompanyManageService;
import application.Response;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;

import java.time.LocalDateTime;
import java.util.List;

@Route(value = "company/:companyId(\\d+)/create-event", layout = MainLayout.class)
@PageTitle("Create Event")
@AnonymousAllowed
public class CreateEventView extends VerticalLayout implements BeforeEnterObserver {

    private final CreateEventPresenter presenter;
    private int companyId;

    private final TextField eventNameField = new TextField("Event Name");
    private final DateTimePicker eventDateField = new DateTimePicker("Event Date");
    private final DateTimePicker saleStartDateField = new DateTimePicker("Sale Start Date");
    private final Checkbox lotteryCheckbox = new Checkbox("Lottery event");
    private final ComboBox<GeographicalArea> locationBox = new ComboBox<>("Location");
    private final ComboBox<CategoryEvent> categoryBox = new ComboBox<>("Category");

    private final IntegerField stageXField = new IntegerField("Stage X");
    private final IntegerField stageYField = new IntegerField("Stage Y");
    private final IntegerField entry1XField = new IntegerField("Entry 1 X");
    private final IntegerField entry1YField = new IntegerField("Entry 1 Y");
    private final IntegerField entry2XField = new IntegerField("Entry 2 X");
    private final IntegerField entry2YField = new IntegerField("Entry 2 Y");

    private final TextField standingNameField = new TextField("Standing Zone Name");
    private final IntegerField standingCapacityField = new IntegerField("Standing Capacity");
    private final NumberField standingPriceField = new NumberField("Standing Price");
    private final IntegerField standingXField = new IntegerField("Standing X");
    private final IntegerField standingYField = new IntegerField("Standing Y");

    private final TextField seatingNameField = new TextField("Seating Zone Name");
    private final IntegerField seatingRowsField = new IntegerField("Rows");
    private final IntegerField seatingColsField = new IntegerField("Columns");
    private final NumberField seatingPriceField = new NumberField("Seating Price");
    private final IntegerField seatingXField = new IntegerField("Seating X");
    private final IntegerField seatingYField = new IntegerField("Seating Y");

    public CreateEventView(EventCompanyManageService eventCompanyManageService) {
        this.presenter = new CreateEventPresenter(eventCompanyManageService);

        setPadding(true);
        setSpacing(true);
        setWidthFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = Integer.parseInt(event.getRouteParameters().get("companyId").orElse("0"));
        buildPage();
    }

    private void buildPage() {
        removeAll();

        Button backButton = new Button("← Back to Company",
                e -> UI.getCurrent().navigate("company/" + companyId));
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        H2 title = new H2("Create Event");
        Paragraph subtitle = new Paragraph("Create event details and define the initial venue map.");

        configureFields();
        setDefaults();

        Button createButton = new Button("Create Event", e -> submit());
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(
                backButton,
                title,
                subtitle,
                buildEventDetailsSection(),
                new Hr(),
                buildMapSection(),
                createButton
        );
    }

    private void configureFields() {
        eventNameField.setWidthFull();

        eventDateField.setWidth("20rem");
        saleStartDateField.setWidth("20rem");

        locationBox.setItems(GeographicalArea.values());
        locationBox.setWidth("20rem");

        categoryBox.setItems(CategoryEvent.values());
        categoryBox.setWidth("20rem");

        configurePositiveInteger(stageXField);
        configurePositiveInteger(stageYField);
        configurePositiveInteger(entry1XField);
        configurePositiveInteger(entry1YField);
        configurePositiveInteger(entry2XField);
        configurePositiveInteger(entry2YField);

        standingNameField.setWidth("20rem");
        configurePositiveInteger(standingCapacityField);
        configurePositiveNumber(standingPriceField);
        configurePositiveInteger(standingXField);
        configurePositiveInteger(standingYField);

        seatingNameField.setWidth("20rem");
        configurePositiveInteger(seatingRowsField);
        configurePositiveInteger(seatingColsField);
        configurePositiveNumber(seatingPriceField);
        configurePositiveInteger(seatingXField);
        configurePositiveInteger(seatingYField);
    }

    private void configurePositiveInteger(IntegerField field) {
        field.setMin(0);
        field.setStepButtonsVisible(true);
        field.setWidth("10rem");
    }

    private void configurePositiveNumber(NumberField field) {
        field.setMin(0);
        field.setWidth("10rem");
    }

    private void setDefaults() {
        eventDateField.setValue(LocalDateTime.now().plusMonths(1));
        saleStartDateField.setValue(LocalDateTime.now().plusDays(1));

        stageXField.setValue(400);
        stageYField.setValue(20);

        entry1XField.setValue(50);
        entry1YField.setValue(100);
        entry2XField.setValue(750);
        entry2YField.setValue(100);

        standingNameField.setValue("General Standing");
        standingCapacityField.setValue(40);
        standingPriceField.setValue(80.0);
        standingXField.setValue(70);
        standingYField.setValue(450);

        seatingNameField.setValue("Regular");
        seatingRowsField.setValue(8);
        seatingColsField.setValue(10);
        seatingPriceField.setValue(150.0);
        seatingXField.setValue(270);
        seatingYField.setValue(400);
    }

    private VerticalLayout buildEventDetailsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);

        HorizontalLayout dates = new HorizontalLayout(eventDateField, saleStartDateField);
        HorizontalLayout metadata = new HorizontalLayout(locationBox, categoryBox);

        section.add(
                new H3("Event Details"),
                eventNameField,
                dates,
                metadata,
                lotteryCheckbox
        );

        return section;
    }

    private VerticalLayout buildMapSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);

        HorizontalLayout stageRow = new HorizontalLayout(stageXField, stageYField);
        HorizontalLayout entry1Row = new HorizontalLayout(entry1XField, entry1YField);
        HorizontalLayout entry2Row = new HorizontalLayout(entry2XField, entry2YField);

        HorizontalLayout standingRow1 = new HorizontalLayout(standingNameField, standingCapacityField, standingPriceField);
        HorizontalLayout standingRow2 = new HorizontalLayout(standingXField, standingYField);

        HorizontalLayout seatingRow1 = new HorizontalLayout(seatingNameField, seatingRowsField, seatingColsField, seatingPriceField);
        HorizontalLayout seatingRow2 = new HorizontalLayout(seatingXField, seatingYField);

        section.add(
                new H3("Venue Map"),
                new Paragraph("Define one standing zone and one seating zone for the initial venue map."),
                stageRow,
                entry1Row,
                entry2Row,
                new H3("Standing Zone"),
                standingRow1,
                standingRow2,
                new H3("Seating Zone"),
                seatingRow1,
                seatingRow2
        );

        return section;
    }

    private void submit() {
        String token = getToken();

        if (!validateInput()) {
            return;
        }

        Response<Integer> createResponse = presenter.createEvent(
                token,
                companyId,
                eventDateField.getValue(),
                eventNameField.getValue().trim(),
                saleStartDateField.getValue(),
                lotteryCheckbox.getValue(),
                locationBox.getValue(),
                categoryBox.getValue()
        );

        if (createResponse.getValue() == null) {
            showError(createResponse.getMessage());
            return;
        }

        int eventId = createResponse.getValue();

        Response<Boolean> mapResponse = presenter.defineVenueAndSeatingMap(
                token,
                eventId,
                new ElementPositionDTO(stageXField.getValue(), stageYField.getValue()),
                List.of(
                        new ElementPositionDTO(entry1XField.getValue(), entry1YField.getValue()),
                        new ElementPositionDTO(entry2XField.getValue(), entry2YField.getValue())
                ),
                List.of(new StandingZoneDTO(
                        standingCapacityField.getValue(),
                        standingNameField.getValue().trim(),
                        standingPriceField.getValue(),
                        new ElementPositionDTO(standingXField.getValue(), standingYField.getValue())
                )),
                List.of(new SeatingZoneDTO(
                        seatingRowsField.getValue(),
                        seatingColsField.getValue(),
                        seatingNameField.getValue().trim(),
                        seatingPriceField.getValue(),
                        new ElementPositionDTO(seatingXField.getValue(), seatingYField.getValue())
                ))
        );

        if (mapResponse.getValue() == null || !mapResponse.getValue()) {
            showError(mapResponse.getMessage());
            return;
        }

        showSuccess("Event created successfully.");
        UI.getCurrent().navigate("company/" + companyId);
    }

    private boolean validateInput() {
        if (eventNameField.getValue() == null || eventNameField.getValue().isBlank()) {
            showError("Event name is required.");
            return false;
        }

        if (eventDateField.getValue() == null) {
            showError("Event date is required.");
            return false;
        }

        if (saleStartDateField.getValue() == null) {
            showError("Sale start date is required.");
            return false;
        }

        if (!eventDateField.getValue().isAfter(LocalDateTime.now())) {
            showError("Event date must be in the future.");
            return false;
        }

        if (saleStartDateField.getValue().isAfter(eventDateField.getValue())) {
            showError("Sale start date must be before event date.");
            return false;
        }

        if (locationBox.getValue() == null) {
            showError("Location is required.");
            return false;
        }

        if (categoryBox.getValue() == null) {
            showError("Category is required.");
            return false;
        }

        if (standingNameField.getValue() == null || standingNameField.getValue().isBlank()) {
            showError("Standing zone name is required.");
            return false;
        }

        if (seatingNameField.getValue() == null || seatingNameField.getValue().isBlank()) {
            showError("Seating zone name is required.");
            return false;
        }

        if (!hasPositive(standingCapacityField, "Standing capacity")) return false;
        if (!hasPositivePrice(standingPriceField, "Standing price")) return false;
        if (!hasPositive(seatingRowsField, "Rows")) return false;
        if (!hasPositive(seatingColsField, "Columns")) return false;
        if (!hasPositivePrice(seatingPriceField, "Seating price")) return false;

        return true;
    }

    private boolean hasPositive(IntegerField field, String label) {
        if (field.getValue() == null || field.getValue() < 1) {
            showError(label + " must be at least 1.");
            return false;
        }
        return true;
    }

    private boolean hasPositivePrice(NumberField field, String label) {
        if (field.getValue() == null || field.getValue() <= 0) {
            showError(label + " must be positive.");
            return false;
        }
        return true;
    }

    private String getToken() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        return (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
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