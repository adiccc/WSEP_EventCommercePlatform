package UI.Views;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import UI.Presenters.CreateEventPresenter;
import application.EventCompanyManageService;
import application.LotteryService;
import application.Response;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
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
import java.util.ArrayList;
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

    private final IntegerField lotteryCapacityField = new IntegerField("Lottery Capacity");
    private final DateTimePicker lotteryDateField = new DateTimePicker("Lottery Draw Time");
    private final IntegerField lotteryExpirationHoursField = new IntegerField("Code Expiration Hours");

    private final VerticalLayout lotterySection = new VerticalLayout();
    private final VerticalLayout standingZonesContainer = new VerticalLayout();
    private final VerticalLayout seatingZonesContainer = new VerticalLayout();
    private final Div mapPreview = new Div();
    private final Button createButton = new Button("🎫 Create Event");

    private final List<StandingZoneForm> standingZoneForms = new ArrayList<>();
    private final List<SeatingZoneForm> seatingZoneForms = new ArrayList<>();

    public CreateEventView(
            EventCompanyManageService eventCompanyManageService,
            LotteryService lotteryService
    ) {
        this.presenter = new CreateEventPresenter(eventCompanyManageService, lotteryService);

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

        H2 title = new H2("🎫 Create Event");
        title.getStyle()
                .set("font-size", "2rem")
                .set("font-weight", "800")
                .set("margin-bottom", "0");

        Paragraph subtitle = new Paragraph("Create event details, define the venue map, and optionally configure lottery sales.");
        subtitle.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "0");

        configureFields();
        initializeDefaultZonesIfNeeded();
        registerLiveValidation();
        registerMapPreviewUpdates();

        createButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        createButton.setEnabled(false);
        createButton.addClickListener(e -> submit());
        createButton.getStyle()
                .set("height", "48px")
                .set("font-weight", "800")
                .set("border-radius", "14px")
                .set("padding-left", "1.2rem")
                .set("padding-right", "1.2rem")
                .set("box-shadow", "0 3px 10px rgba(0,0,0,0.10)");

        add(
                backButton,
                title,
                subtitle,
                buildEventDetailsSection(),
                new Hr(),
                buildVenueSetupWithPreview(),
                createButton
        );

        refreshLotterySectionVisibility();
        refreshZoneContainers();
        refreshMapPreview();
        updateCreateButtonState();
    }

    private VerticalLayout buildEventDetailsSection() {
        VerticalLayout section = cardSection();

        HorizontalLayout dates = new HorizontalLayout(eventDateField, saleStartDateField);
        dates.setWidthFull();

        HorizontalLayout metadata = new HorizontalLayout(locationBox, categoryBox);
        metadata.setWidthFull();

        section.add(
                sectionTitle("✨ Event Details"),
                eventNameField,
                dates,
                metadata,
                lotteryCheckbox,
                lotterySection
        );

        return section;
    }

    private HorizontalLayout buildVenueSetupWithPreview() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.START);
        layout.getStyle()
                .set("overflow", "visible")
                .set("gap", "1.5rem");
        VerticalLayout formColumn = new VerticalLayout();
        formColumn.setPadding(false);
        formColumn.setSpacing(true);
        formColumn.getStyle()
                .set("min-width", "0")
                .set("max-width", "50%");
        formColumn.add(buildMapSection());

        VerticalLayout previewColumn = buildPreviewColumn();
        previewColumn.setWidth("50%");
        previewColumn.getStyle()
                .set("min-width", "0")
                .set("max-width", "50%");

        layout.add(formColumn, previewColumn);
        layout.setFlexGrow(1, formColumn);
        layout.setFlexGrow(1, previewColumn);

        return layout;
    }

    private VerticalLayout buildMapSection() {
        VerticalLayout section = cardSection();

        HorizontalLayout stageRow = new HorizontalLayout(stageXField, stageYField);
        HorizontalLayout entry1Row = new HorizontalLayout(entry1XField, entry1YField);
        HorizontalLayout entry2Row = new HorizontalLayout(entry2XField, entry2YField);

        Button addStandingZoneButton = new Button("➕ Add Standing Zone", e -> {
            addStandingZone("Standing Zone " + (standingZoneForms.size() + 1), 30, 80.0, 100, 450);
            refreshZoneContainers();
            refreshMapPreview();
            updateCreateButtonState();
        });
        addStandingZoneButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button addSeatingZoneButton = new Button("➕ Add Seating Zone", e -> {
            addSeatingZone("Seating Zone " + (seatingZoneForms.size() + 1), 8, 10, 150.0, 300, 400);
            refreshZoneContainers();
            refreshMapPreview();
            updateCreateButtonState();
        });
        addSeatingZoneButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        standingZonesContainer.setPadding(false);
        seatingZonesContainer.setPadding(false);

        section.add(
                sectionTitle("🏟 Venue Map Setup"),
                new Paragraph("Define the stage, entries, standing zones, and seating zones."),
                new H3("Stage"),
                stageRow,
                new H3("Entries"),
                entry1Row,
                entry2Row,
                new H3("Standing Zones"),
                standingZonesContainer,
                addStandingZoneButton,
                new H3("Seating Zones"),
                seatingZonesContainer,
                addSeatingZoneButton
        );

        return section;
    }

    private VerticalLayout buildPreviewColumn() {
        VerticalLayout preview = cardSection();
        preview.setPadding(true);
        preview.setSpacing(true);

        H3 title = sectionTitle("🗺 Venue Preview");
        Paragraph helper = new Paragraph("Preview updates automatically according to the map fields.");
        helper.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "0");

        mapPreview.getStyle()
                .set("position", "relative")
                .set("height", "700px")
                .set("width", "100%")
                .set("overflow", "auto")
                .set("background", "linear-gradient(135deg, #f8f9fb, #eef1f5)")
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "18px")
                .set("box-shadow", "0 6px 18px rgba(0,0,0,0.08)");

        preview.add(title, helper, mapPreview);
        return preview;
    }

    private void buildLotterySection() {
        lotterySection.removeAll();
        lotterySection.setPadding(true);
        lotterySection.setSpacing(true);
        lotterySection.getStyle()
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("border", "1px solid var(--lumo-primary-color-50pct)")
                .set("border-radius", "18px");

        HorizontalLayout row = new HorizontalLayout(
                lotteryCapacityField,
                lotteryDateField,
                lotteryExpirationHoursField
        );
        row.setWidthFull();

        lotterySection.add(
                sectionTitle("🎲 Lottery Setup"),
                new Paragraph("Lottery draw time must be in the future and before the sale start date."),
                row
        );
    }

    private VerticalLayout cardSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "18px")
                .set("box-shadow", "0 4px 14px rgba(0,0,0,0.06)");
        return section;
    }

    private H3 sectionTitle(String text) {
        H3 title = new H3(text);
        title.getStyle()
                .set("font-size", "1.35rem")
                .set("font-weight", "800")
                .set("margin", "0.25rem 0");
        return title;
    }

    private void configureFields() {
        eventNameField.setWidthFull();
        eventNameField.setClearButtonVisible(true);

        eventDateField.setWidth("20rem");
        saleStartDateField.setWidth("20rem");

        locationBox.setItems(GeographicalArea.values());
        locationBox.setWidth("20rem");

        categoryBox.setItems(CategoryEvent.values());
        categoryBox.setWidth("20rem");

        configureCoordinate(stageXField);
        configureCoordinate(stageYField);
        configureCoordinate(entry1XField);
        configureCoordinate(entry1YField);
        configureCoordinate(entry2XField);
        configureCoordinate(entry2YField);

        configurePositiveInteger(lotteryCapacityField);
        lotteryDateField.setWidth("20rem");
        configurePositiveInteger(lotteryExpirationHoursField);
        lotteryExpirationHoursField.setValue(24);

        lotteryCheckbox.addValueChangeListener(e -> {
            refreshLotterySectionVisibility();
            updateCreateButtonState();
        });
    }

    private void configureCoordinate(IntegerField field) {
        field.setMin(0);
        field.setMax(1000);
        field.setStepButtonsVisible(true);
        field.setWidth("10rem");
    }

    private void configurePositiveInteger(IntegerField field) {
        field.setMin(1);
        field.setStepButtonsVisible(true);
        field.setWidth("10rem");
    }

    private void configurePositiveNumber(NumberField field) {
        field.setMin(0.01);
        field.setWidth("10rem");
    }

    private void initializeDefaultZonesIfNeeded() {
        if (standingZoneForms.isEmpty()) {
            stageXField.setValue(400);
            stageYField.setValue(20);

            entry1XField.setValue(50);
            entry1YField.setValue(100);
            entry2XField.setValue(750);
            entry2YField.setValue(100);

            addStandingZone("General Standing", 40, 80.0, 70, 450);
            addSeatingZone("Regular", 8, 10, 150.0, 270, 400);
        }
    }

    private void addStandingZone(String name, int capacity, double price, int x, int y) {
        StandingZoneForm form = new StandingZoneForm();

        form.nameField.setValue(name);
        form.capacityField.setValue(capacity);
        form.priceField.setValue(price);
        form.xField.setValue(x);
        form.yField.setValue(y);

        standingZoneForms.add(form);
        registerStandingZoneListeners(form);
    }

    private void addSeatingZone(String name, int rows, int columns, double price, int x, int y) {
        SeatingZoneForm form = new SeatingZoneForm();

        form.nameField.setValue(name);
        form.rowsField.setValue(rows);
        form.columnsField.setValue(columns);
        form.priceField.setValue(price);
        form.xField.setValue(x);
        form.yField.setValue(y);

        seatingZoneForms.add(form);
        registerSeatingZoneListeners(form);
    }

    private void refreshZoneContainers() {
        standingZonesContainer.removeAll();
        for (StandingZoneForm form : standingZoneForms) {
            standingZonesContainer.add(form.root);
        }

        seatingZonesContainer.removeAll();
        for (SeatingZoneForm form : seatingZoneForms) {
            seatingZonesContainer.add(form.root);
        }
    }

    private void refreshLotterySectionVisibility() {
        lotterySection.removeAll();

        if (Boolean.TRUE.equals(lotteryCheckbox.getValue())) {
            buildLotterySection();
            lotterySection.setVisible(true);
        } else {
            lotterySection.setVisible(false);
        }
    }

    private void registerLiveValidation() {
        addValidationListener(eventNameField);
        addValidationListener(eventDateField);
        addValidationListener(saleStartDateField);
        addValidationListener(locationBox);
        addValidationListener(categoryBox);

        addValidationListener(stageXField);
        addValidationListener(stageYField);
        addValidationListener(entry1XField);
        addValidationListener(entry1YField);
        addValidationListener(entry2XField);
        addValidationListener(entry2YField);

        addValidationListener(lotteryCapacityField);
        addValidationListener(lotteryDateField);
        addValidationListener(lotteryExpirationHoursField);
    }

    private void registerMapPreviewUpdates() {
        addMapListener(stageXField);
        addMapListener(stageYField);
        addMapListener(entry1XField);
        addMapListener(entry1YField);
        addMapListener(entry2XField);
        addMapListener(entry2YField);
    }

    private void registerStandingZoneListeners(StandingZoneForm form) {
        addValidationListener(form.nameField);
        addValidationListener(form.capacityField);
        addValidationListener(form.priceField);
        addValidationListener(form.xField);
        addValidationListener(form.yField);

        addMapListener(form.nameField);
        addMapListener(form.capacityField);
        addMapListener(form.priceField);
        addMapListener(form.xField);
        addMapListener(form.yField);
    }

    private void registerSeatingZoneListeners(SeatingZoneForm form) {
        addValidationListener(form.nameField);
        addValidationListener(form.rowsField);
        addValidationListener(form.columnsField);
        addValidationListener(form.priceField);
        addValidationListener(form.xField);
        addValidationListener(form.yField);

        addMapListener(form.nameField);
        addMapListener(form.rowsField);
        addMapListener(form.columnsField);
        addMapListener(form.priceField);
        addMapListener(form.xField);
        addMapListener(form.yField);
    }

    private void addValidationListener(AbstractField<?, ?> field) {
        field.addValueChangeListener(e -> updateCreateButtonState());
    }

    private void addMapListener(AbstractField<?, ?> field) {
        field.addValueChangeListener(e -> refreshMapPreview());
    }

    private void updateCreateButtonState() {
        try {
            createButton.setEnabled(isFormValid(false));
        } catch (Exception e) {
            createButton.setEnabled(false);
        }
    }

    private boolean isFormValid(boolean showErrors) {
        if (isBlank(eventNameField.getValue())) {
            return fail(showErrors, "Event name is required.");
        }

        if (eventDateField.getValue() == null) {
            return fail(showErrors, "Event date is required.");
        }

        if (saleStartDateField.getValue() == null) {
            return fail(showErrors, "Sale start date is required.");
        }

        if (locationBox.getValue() == null) {
            return fail(showErrors, "Location is required.");
        }

        if (categoryBox.getValue() == null) {
            return fail(showErrors, "Category is required.");
        }

        if (!isMapValid(showErrors)) {
            return false;
        }

        if (Boolean.TRUE.equals(lotteryCheckbox.getValue()) && !isLotteryValid(showErrors)) {
            return false;
        }

        return true;
    }


    private boolean isMapValid(boolean showErrors) {
        if (!hasCoordinate(stageXField, "Stage X", showErrors)) return false;
        if (!hasCoordinate(stageYField, "Stage Y", showErrors)) return false;
        if (!hasCoordinate(entry1XField, "Entry 1 X", showErrors)) return false;
        if (!hasCoordinate(entry1YField, "Entry 1 Y", showErrors)) return false;
        if (!hasCoordinate(entry2XField, "Entry 2 X", showErrors)) return false;
        if (!hasCoordinate(entry2YField, "Entry 2 Y", showErrors)) return false;

        if (standingZoneForms.isEmpty()) {
            return fail(showErrors, "At least one standing zone is required.");
        }

        if (seatingZoneForms.isEmpty()) {
            return fail(showErrors, "At least one seating zone is required.");
        }

        for (StandingZoneForm form : standingZoneForms) {
            if (isBlank(form.nameField.getValue())) return fail(showErrors, "Standing zone name is required.");
            if (!hasPositiveInteger(form.capacityField, "Standing capacity", showErrors)) return false;
            if (!hasPositiveNumber(form.priceField, "Standing price", showErrors)) return false;
            if (!hasCoordinate(form.xField, "Standing X", showErrors)) return false;
            if (!hasCoordinate(form.yField, "Standing Y", showErrors)) return false;
        }

        for (SeatingZoneForm form : seatingZoneForms) {
            if (isBlank(form.nameField.getValue())) return fail(showErrors, "Seating zone name is required.");
            if (!hasPositiveInteger(form.rowsField, "Rows", showErrors)) return false;
            if (!hasPositiveInteger(form.columnsField, "Columns", showErrors)) return false;
            if (!hasPositiveNumber(form.priceField, "Seating price", showErrors)) return false;
            if (!hasCoordinate(form.xField, "Seating X", showErrors)) return false;
            if (!hasCoordinate(form.yField, "Seating Y", showErrors)) return false;
        }

        return true;
    }

    private boolean isLotteryValid(boolean showErrors) {
        if (!hasPositiveInteger(lotteryCapacityField, "Lottery capacity", showErrors)) {
            return false;
        }

        if (lotteryDateField.getValue() == null) {
            return fail(showErrors, "Lottery draw time is required.");
        }

        return hasPositiveInteger(lotteryExpirationHoursField, "Code expiration hours", showErrors);
    }


    private boolean hasCoordinate(IntegerField field, String label, boolean showErrors) {
        if (field.getValue() == null || field.getValue() < 0) {
            return fail(showErrors, label + " must be zero or greater.");
        }
        return true;
    }

    private boolean hasPositiveInteger(IntegerField field, String label, boolean showErrors) {
        if (field.getValue() == null || field.getValue() < 1) {
            return fail(showErrors, label + " must be at least 1.");
        }
        return true;
    }

    private boolean hasPositiveNumber(NumberField field, String label, boolean showErrors) {
        if (field.getValue() == null || field.getValue() <= 0) {
            return fail(showErrors, label + " must be positive.");
        }
        return true;
    }

    private boolean fail(boolean showErrors, String message) {
        if (showErrors) {
            showError(message);
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void submit() {
        if (!isFormValid(true)) {
            return;
        }

        String token = getToken();

        Response<Integer> createResponse = presenter.createEvent(
                token,
                companyId,
                eventDateField.getValue(),
                eventNameField.getValue().trim(),
                saleStartDateField.getValue(),
                Boolean.TRUE.equals(lotteryCheckbox.getValue()),
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
                buildStandingZoneDTOs(),
                buildSeatingZoneDTOs()
        );

        if (mapResponse.getValue() == null || !mapResponse.getValue()) {
            showError("Event was created but is not active because venue map setup failed: " + mapResponse.getMessage());
            return;
        }

        if (Boolean.TRUE.equals(lotteryCheckbox.getValue())) {
            Response<Boolean> lotteryResponse = presenter.createLottery(
                    token,
                    eventId,
                    lotteryCapacityField.getValue(),
                    lotteryDateField.getValue(),
                    lotteryExpirationHoursField.getValue()
            );

            if (lotteryResponse.getValue() == null || !lotteryResponse.getValue()) {
                showError("Event and venue map were created, but the event is not active because lottery setup failed: " + lotteryResponse.getMessage());
                return;
            }
        }

        showSuccess("Event created successfully.");
        UI.getCurrent().navigate("company/" + companyId);
    }


    private List<StandingZoneDTO> buildStandingZoneDTOs() {
        List<StandingZoneDTO> zones = new ArrayList<>();

        for (StandingZoneForm form : standingZoneForms) {
            zones.add(new StandingZoneDTO(
                    form.capacityField.getValue(),
                    form.nameField.getValue().trim(),
                    form.priceField.getValue(),
                    new ElementPositionDTO(form.xField.getValue(), form.yField.getValue())
            ));
        }

        return zones;
    }

    private List<SeatingZoneDTO> buildSeatingZoneDTOs() {
        List<SeatingZoneDTO> zones = new ArrayList<>();

        for (SeatingZoneForm form : seatingZoneForms) {
            zones.add(new SeatingZoneDTO(
                    form.rowsField.getValue(),
                    form.columnsField.getValue(),
                    form.nameField.getValue().trim(),
                    form.priceField.getValue(),
                    new ElementPositionDTO(form.xField.getValue(), form.yField.getValue())
            ));
        }

        return zones;
    }

    private void refreshMapPreview() {
        mapPreview.removeAll();

        Div canvas = new Div();
        canvas.getStyle()
                .set("position", "relative")
                .set("width", "1200px")
                .set("height", "900px")
                .set("min-width", "1200px")
                .set("min-height", "900px");

        addStagePreview(canvas);
        addEntryPreview(canvas, entry1XField.getValue(), entry1YField.getValue(), "ENTRY 1");
        addEntryPreview(canvas, entry2XField.getValue(), entry2YField.getValue(), "ENTRY 2");

        for (StandingZoneForm form : standingZoneForms) {
            addStandingPreview(canvas, form);
        }

        for (SeatingZoneForm form : seatingZoneForms) {
            addSeatingPreview(canvas, form);
        }

        mapPreview.add(canvas);
    }

    private void addStagePreview(Div canvas) {
        if (stageXField.getValue() == null || stageYField.getValue() == null) {
            return;
        }

        Div stage = previewBox("STAGE", "#111827", "white", "140px", "48px");
        position(stage, stageXField.getValue(), stageYField.getValue());
        canvas.add(stage);
    }

    private void addEntryPreview(Div canvas, Integer x, Integer y, String label) {
        if (x == null || y == null) {
            return;
        }

        Div entry = previewBox(label, "#16a34a", "white", "100px", "38px");
        position(entry, x, y);
        canvas.add(entry);
    }

    private void addStandingPreview(Div canvas, StandingZoneForm form) {
        if (form.xField.getValue() == null || form.yField.getValue() == null) {
            return;
        }

        String label = isBlank(form.nameField.getValue()) ? "Standing" : form.nameField.getValue();

        Div zone = previewBox(label, "#facc15", "#111827", "190px", "120px");
        position(zone, form.xField.getValue(), form.yField.getValue());
        canvas.add(zone);
    }

    private void addSeatingPreview(Div canvas, SeatingZoneForm form) {
        if (form.xField.getValue() == null || form.yField.getValue() == null) {
            return;
        }

        Div zone = new Div();
        zone.getStyle()
                .set("position", "absolute")
                .set("left", form.xField.getValue() + "px")
                .set("top", form.yField.getValue() + "px")
                .set("background", "white")
                .set("border", "1px solid var(--lumo-contrast-30pct)")
                .set("border-radius", "14px")
                .set("padding", "10px")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.12)");

        Span title = new Span(isBlank(form.nameField.getValue()) ? "Seating" : form.nameField.getValue());
        title.getStyle()
                .set("display", "block")
                .set("font-weight", "700")
                .set("margin-bottom", "8px");

        Div seats = new Div();

        int rows = form.rowsField.getValue() != null ? form.rowsField.getValue() : 0;
        int cols = form.columnsField.getValue() != null ? form.columnsField.getValue() : 0;
        int seatSize = cols > 20 ? 10 : cols > 14 ? 14 : 18;

        seats.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(" + Math.max(cols, 1) + ", " + seatSize + "px)")
                .set("gap", "4px");

        for (int i = 0; i < rows * cols; i++) {
            Div seat = new Div();
            seat.getStyle()
                    .set("width", seatSize + "px")
                    .set("height", seatSize + "px")
                    .set("border-radius", "6px")
                    .set("background", "#60a5fa");
            seats.add(seat);
        }

        zone.add(title, seats);
        canvas.add(zone);
    }

    private Div previewBox(String text, String background, String color, String width, String height) {
        Div box = new Div();
        box.setText(text);
        box.getStyle()
                .set("position", "absolute")
                .set("width", width)
                .set("height", height)
                .set("background", background)
                .set("color", color)
                .set("border-radius", "14px")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("font-weight", "800")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.14)");
        return box;
    }

    private void position(Component component, int x, int y) {
        component.getElement().getStyle()
                .set("left", x + "px")
                .set("top", y + "px");
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
        Notification notification = new Notification();
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setPosition(Notification.Position.TOP_CENTER);
        notification.setDuration(0);

        Span text = new Span(message != null ? message : "An error occurred.");

        Button closeButton = new Button("✕", event -> notification.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

        HorizontalLayout layout = new HorizontalLayout(text, closeButton);
        layout.setAlignItems(Alignment.CENTER);
        layout.setSpacing(true);

        notification.add(layout);
        notification.open();
    }


    private class StandingZoneForm {
        private final VerticalLayout root = cardSection();
        private final TextField nameField = new TextField("Standing Zone Name");
        private final IntegerField capacityField = new IntegerField("Standing Capacity");
        private final NumberField priceField = new NumberField("Standing Price");
        private final IntegerField xField = new IntegerField("Standing X");
        private final IntegerField yField = new IntegerField("Standing Y");

        private StandingZoneForm() {
            nameField.setWidth("20rem");
            configurePositiveInteger(capacityField);
            configurePositiveNumber(priceField);
            configureCoordinate(xField);
            configureCoordinate(yField);

            Button removeButton = new Button("Remove", e -> {
                if (standingZoneForms.size() <= 1) {
                    showError("At least one standing zone is required.");
                    return;
                }

                standingZoneForms.remove(this);
                refreshZoneContainers();
                refreshMapPreview();
                updateCreateButtonState();
            });
            removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

            HorizontalLayout row1 = new HorizontalLayout(nameField, capacityField, priceField);
            row1.setWidthFull();
            row1.getStyle().set("flex-wrap", "wrap");


            HorizontalLayout row2 = new HorizontalLayout(xField, yField, removeButton);
            row2.setWidthFull();
            row2.getStyle().set("flex-wrap", "wrap");
            row2.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

            root.add(new H4("Standing Zone"), row1, row2);
        }
    }

    private class SeatingZoneForm {
        private final VerticalLayout root = cardSection();
        private final TextField nameField = new TextField("Seating Zone Name");
        private final IntegerField rowsField = new IntegerField("Rows");
        private final IntegerField columnsField = new IntegerField("Columns");
        private final NumberField priceField = new NumberField("Seating Price");
        private final IntegerField xField = new IntegerField("Seating X");
        private final IntegerField yField = new IntegerField("Seating Y");

        private SeatingZoneForm() {
            nameField.setWidth("20rem");
            configurePositiveInteger(rowsField);
            configurePositiveInteger(columnsField);
            configurePositiveNumber(priceField);
            configureCoordinate(xField);
            configureCoordinate(yField);

            Button removeButton = new Button("Remove", e -> {
                if (seatingZoneForms.size() <= 1) {
                    showError("At least one seating zone is required.");
                    return;
                }

                seatingZoneForms.remove(this);
                refreshZoneContainers();
                refreshMapPreview();
                updateCreateButtonState();
            });
            removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

            HorizontalLayout row1 = new HorizontalLayout(nameField, rowsField, columnsField, priceField);
            row1.setWidthFull();
            row1.getStyle().set("flex-wrap", "wrap");

            HorizontalLayout row2 = new HorizontalLayout(xField, yField, removeButton);
            row2.setWidthFull();
            row2.getStyle().set("flex-wrap", "wrap");
            row2.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

            root.add(new H4("Seating Zone"), row1, row2);
        }
    }
}