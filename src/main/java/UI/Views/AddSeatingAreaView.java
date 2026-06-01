package UI.Views;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import UI.Presenters.AddSeatingAreaPresenter;
import application.EventCompanyManageService;
import application.Response;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import domain.dataType.TicketStatus;
import domain.dto.EventMapDTO;

import java.util.ArrayList;
import java.util.List;

@Route(value = "event/:eventId(\\d+)/add-seating-area", layout = MainLayout.class)
@PageTitle("Add Seating Area")
@AnonymousAllowed
public class AddSeatingAreaView extends VerticalLayout implements BeforeEnterObserver {

    private final AddSeatingAreaPresenter presenter;
    private int eventId;
    private EventMapDTO existingMap;

    private final VerticalLayout seatingZonesContainer = new VerticalLayout();
    private final Div mapPreview = new Div();
    private final Button saveButton = new Button("Save New Areas");

    private final List<SeatingZoneForm> seatingZoneForms = new ArrayList<>();

    public AddSeatingAreaView(EventCompanyManageService eventCompanyManageService) {
        this.presenter = new AddSeatingAreaPresenter(eventCompanyManageService);
        setPadding(true);
        setSpacing(true);
        setWidthFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        eventId = Integer.parseInt(event.getRouteParameters().get("eventId").orElse("0"));
        buildPage();
    }

    private void buildPage() {
        removeAll();

        Response<EventMapDTO> mapResponse = presenter.getExistingEventMap(getToken(), eventId);
        if (mapResponse.getValue() == null) {
            add(new Button("← Back", e -> UI.getCurrent().getPage().getHistory().back()));
            showError(mapResponse.getMessage());
            add(new Paragraph("Cannot add seating areas: " + safeMessage(mapResponse.getMessage())));
            return;
        }

        existingMap = mapResponse.getValue();

        Button backButton = new Button("← Back", e -> UI.getCurrent().getPage().getHistory().back());
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        H2 title = new H2("Add Seating Areas");
        Paragraph subtitle = new Paragraph("View the existing event map and add new seating zones without changing purchased seats.");

        seatingZonesContainer.setPadding(false);

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setEnabled(false);
        saveButton.addClickListener(e -> submit());

        if (seatingZoneForms.isEmpty()) {
            addSeatingZone("New Seating Zone 1", 8, 10, 150.0, 270, 400);
        }

        add(backButton, title, subtitle, buildMainContent(), saveButton);

        refreshZoneContainers();
        refreshMapPreview();
        updateSaveButtonState();
    }

    private HorizontalLayout buildMainContent() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setAlignItems(Alignment.START);

        VerticalLayout formColumn = cardSection();
        formColumn.setWidth("42%");

        Button addSeatingZoneButton = new Button("Add Seating Zone", e -> {
            int nextIndex = seatingZoneForms.size() + 1;
            addSeatingZone("New Seating Zone " + nextIndex, 8, 10, 150.0, 270 + nextIndex * 80, 400);
            refreshZoneContainers();
            refreshMapPreview();
            updateSaveButtonState();
        });
        addSeatingZoneButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        formColumn.add(sectionTitle("New Seating Areas"), addSeatingZoneButton, seatingZonesContainer);

        VerticalLayout previewColumn = cardSection();
        previewColumn.setWidth("58%");

        mapPreview.getStyle()
                .set("position", "relative")
                .set("height", "700px")
                .set("width", "100%")
                .set("overflow", "auto")
                .set("background", "linear-gradient(135deg, #f8f9fb, #eef1f5)")
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "18px");

        previewColumn.add(
                sectionTitle("Existing Map + New Areas Preview"),
                new Paragraph("Existing areas are shown softly. New areas are shown prominently."),
                mapPreview
        );

        layout.add(formColumn, previewColumn);
        return layout;
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
        title.getStyle().set("margin", "0.25rem 0");
        return title;
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
        seatingZonesContainer.removeAll();
        for (SeatingZoneForm form : seatingZoneForms) {
            seatingZonesContainer.add(form.root);
        }
    }

    private void registerSeatingZoneListeners(SeatingZoneForm form) {
        addValidationAndPreviewListener(form.nameField);
        addValidationAndPreviewListener(form.rowsField);
        addValidationAndPreviewListener(form.columnsField);
        addValidationAndPreviewListener(form.priceField);
        addValidationAndPreviewListener(form.xField);
        addValidationAndPreviewListener(form.yField);
    }

    private void addValidationAndPreviewListener(AbstractField<?, ?> field) {
        field.addValueChangeListener(e -> {
            refreshMapPreview();
            updateSaveButtonState();
        });
    }

    private void updateSaveButtonState() {
        saveButton.setEnabled(isFormValid(false));
    }

    private boolean isFormValid(boolean showErrors) {
        if (seatingZoneForms.isEmpty()) {
            return fail(showErrors, "At least one seating area must be added.");
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

    private void submit() {
        if (!isFormValid(true)) {
            return;
        }

        Response<Boolean> response = presenter.addZonesToEventMap(
                getToken(),
                eventId,
                List.of(),
                buildSeatingZoneDTOs()
        );

        if (response.getValue() == null || !response.getValue()) {
            showError(safeMessage(response.getMessage()));
            return;
        }

        showSuccess("Seating areas added successfully.");
        UI.getCurrent().getPage().getHistory().back();
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

        drawExistingMap(canvas);

        for (SeatingZoneForm form : seatingZoneForms) {
            addSeatingPreview(canvas, form, false);
        }

        mapPreview.add(canvas);
    }

    private void drawExistingMap(Div canvas) {
        if (existingMap == null) {
            return;
        }

        ElementPositionDTO stage = existingMap.getStage();
        if (stage != null) {
            Div stageBox = previewBox("STAGE", "#111827", "white", "140px", "48px", "0.85");
            position(stageBox, (int)stage.getX(), (int)stage.getY());
            canvas.add(stageBox);
        }

        for (ElementPositionDTO entry : existingMap.getEntries()) {
            Div entryBox = previewBox("ENTRY", "#16a34a", "white", "100px", "38px", "0.55");
            position(entryBox, (int)entry.getX(), (int)entry.getY());
            canvas.add(entryBox);
        }

        for (StandingZoneDTO zone : existingMap.getStandingZones()) {
            Div standingBox = previewBox(
                    zone.getName() + " (" + zone.getAvailable() + "/" + zone.getCapacty() + ")",
                    "#facc15",
                    "#111827",
                    "190px",
                    "120px",
                    "0.45"
            );
            position(standingBox,(int) zone.getPosition().getX(), (int) zone.getPosition().getY());
            canvas.add(standingBox);
        }

        for (SeatingZoneDTO zone : existingMap.getSeatingZones()) {
            addExistingSeatingPreview(canvas, zone);
        }
    }

    private void addExistingSeatingPreview(Div canvas, SeatingZoneDTO zoneDto) {
        Div zone = new Div();
        zone.getStyle()
                .set("position", "absolute")
                .set("left", zoneDto.getPosition().getX() + "px")
                .set("top", zoneDto.getPosition().getY() + "px")
                .set("background", "rgba(255,255,255,0.55)")
                .set("border", "1px dashed var(--lumo-contrast-40pct)")
                .set("border-radius", "14px")
                .set("padding", "10px");

        Span title = new Span(zoneDto.getName());
        title.getStyle()
                .set("display", "block")
                .set("font-weight", "700")
                .set("margin-bottom", "8px");

        Div seats = new Div();
        int rows = zoneDto.getRows();
        int cols = zoneDto.getCols();
        int seatSize = cols > 20 ? 10 : cols > 14 ? 14 : 18;

        seats.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(" + Math.max(cols, 1) + ", " + seatSize + "px)")
                .set("gap", "4px");

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Div seat = new Div();
                TicketStatus status = zoneDto.getTicketStatus(row, col);

                seat.getStyle()
                        .set("width", seatSize + "px")
                        .set("height", seatSize + "px")
                        .set("border-radius", "6px")
                        .set("background", status == TicketStatus.AVAILABLE ? "#bfdbfe" : "#6b7280");

                seats.add(seat);
            }
        }

        zone.add(title, seats);
        canvas.add(zone);
    }

    private void addSeatingPreview(Div canvas, SeatingZoneForm form, boolean existing) {
        if (form.xField.getValue() == null || form.yField.getValue() == null) {
            return;
        }

        Div zone = new Div();
        zone.getStyle()
                .set("position", "absolute")
                .set("left", form.xField.getValue() + "px")
                .set("top", form.yField.getValue() + "px")
                .set("background", "white")
                .set("border", "2px solid var(--lumo-primary-color)")
                .set("border-radius", "14px")
                .set("padding", "10px")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.18)");

        Span title = new Span(isBlank(form.nameField.getValue()) ? "New Seating" : form.nameField.getValue());
        title.getStyle()
                .set("display", "block")
                .set("font-weight", "800")
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

    private Div previewBox(String text, String background, String color, String width, String height, String opacity) {
        Div box = new Div();
        box.setText(text);
        box.getStyle()
                .set("position", "absolute")
                .set("width", width)
                .set("height", height)
                .set("background", background)
                .set("color", color)
                .set("opacity", opacity)
                .set("border-radius", "14px")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("font-weight", "800");
        return box;
    }

    private void position(Component component, int x, int y) {
        component.getElement().getStyle()
                .set("left", x + "px")
                .set("top", y + "px");
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

    private String getToken() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        return (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);
    }

    private String safeMessage(String message) {
        return message != null && !message.isBlank()
                ? message
                : "No additional error details were provided.";
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 4000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
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
                seatingZoneForms.remove(this);
                refreshZoneContainers();
                refreshMapPreview();
                updateSaveButtonState();
            });
            removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

            HorizontalLayout row1 = new HorizontalLayout(nameField, rowsField, columnsField, priceField);
            row1.setWidthFull();
            row1.getStyle().set("flex-wrap", "wrap");

            HorizontalLayout row2 = new HorizontalLayout(xField, yField, removeButton);
            row2.setWidthFull();
            row2.getStyle().set("flex-wrap", "wrap");
            row2.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

            root.add(new H4("New Seating Zone"), row1, row2);
        }
    }
}