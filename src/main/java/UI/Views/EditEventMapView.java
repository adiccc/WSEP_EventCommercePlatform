package UI.Views;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import UI.Presenters.EditEventMapPresenter;
import application.EventCompanyManageService;
import application.Response;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
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
import DTO.EventMapDTO;

import java.util.ArrayList;
import java.util.List;

@Route(value = "manage/event/:eventId(\\d+)/edit-map", layout = MainLayout.class)
@PageTitle("Edit Event Map")
@AnonymousAllowed
public class EditEventMapView extends VerticalLayout implements BeforeEnterObserver {

    private final EditEventMapPresenter presenter;
    private int eventId;
    private EventMapDTO existingMap;

    private final VerticalLayout standingZonesContainer = new VerticalLayout();
    private final VerticalLayout seatingZonesContainer = new VerticalLayout();
    private final Div mapPreview = new Div();
    private final Button saveButton = new Button("Add New Areas");

    private final List<StandingZoneForm> standingZoneForms = new ArrayList<>();
    private final List<SeatingZoneForm> seatingZoneForms = new ArrayList<>();


    public EditEventMapView(EventCompanyManageService eventCompanyManageService) {
        this.presenter = new EditEventMapPresenter(eventCompanyManageService);

        saveButton.addClickListener(e -> submit());

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
            Button backButton = new Button("← Back", e -> UI.getCurrent().getPage().getHistory().back());
            backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            add(backButton, new Paragraph("Cannot add areas: " + safeMessage(mapResponse.getMessage())));
            showError(mapResponse.getMessage());
            return;
        }

        existingMap = mapResponse.getValue();

        Button backButton = new Button("← Back", e -> UI.getCurrent().getPage().getHistory().back());
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        H2 title = new H2("Edit Event Map");
        title.getStyle()
                .set("font-size", "2rem")
                .set("font-weight", "800")
                .set("margin-bottom", "0");

        Paragraph subtitle = new Paragraph("View the current map, add new areas, or remove existing available areas.");        subtitle.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "0");

        standingZonesContainer.setPadding(false);
        seatingZonesContainer.setPadding(false);

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setEnabled(false);
        saveButton.getStyle()
                .set("height", "48px")
                .set("font-weight", "800")
                .set("border-radius", "14px")
                .set("padding-left", "1.2rem")
                .set("padding-right", "1.2rem");

        initializeDefaultsIfNeeded();

        add(
                backButton,
                title,
                subtitle,
                buildVenueSetupWithPreview(),
                buildExistingZonesSection(),
                saveButton
        );

        refreshZoneContainers();
        refreshMapPreview();
        updateSaveButtonState();
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

        Button addStandingZoneButton = new Button("➕ Add Standing Zone", e -> {
            int nextIndex = nextStandingZoneIndex();
            int[] nextPosition = nextStandingZonePosition();

            addStandingZone("Standing Zone " + nextIndex, 30, 80.0, nextPosition[0], nextPosition[1]);
            refreshZoneContainers();
            refreshMapPreview();
            updateSaveButtonState();
        });
        addStandingZoneButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button addSeatingZoneButton = new Button("➕ Add Seating Zone", e -> {
            int nextIndex = nextSeatingZoneIndex();
            int[] nextPosition = nextSeatingZonePosition();

            addSeatingZone("Seating Zone " + nextIndex, 8, 10, 150.0, nextPosition[0], nextPosition[1]);
            refreshZoneContainers();
            refreshMapPreview();
            updateSaveButtonState();
        });
        addSeatingZoneButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        HorizontalLayout actions = new HorizontalLayout(addStandingZoneButton, addSeatingZoneButton);
        actions.setWidthFull();
        actions.getStyle().set("flex-wrap", "wrap");

        section.add(
                sectionTitle("🏟 Map Areas"),
                new Paragraph("Add new standing or seating zones. Existing areas can be removed only if all their tickets are still available."),
                actions,
                new H3("Standing Zones"),
                standingZonesContainer,
                new H3("Seating Zones"),
                seatingZonesContainer
        );

        return section;
    }

    private VerticalLayout buildExistingZonesSection() {
        VerticalLayout section = cardSection();

        section.add(
                sectionTitle("🗑 Existing Areas"),
                new Paragraph("Removing an existing area is applied immediately after confirmation. Adding new areas is saved with the button below."),
                new H3("Existing Standing Zones")
        );

        if (existingMap.getStandingZones().isEmpty()) {
            section.add(new Paragraph("No existing standing zones."));
        } else {
            for (StandingZoneDTO zone : existingMap.getStandingZones()) {
                section.add(buildExistingStandingZoneRow(zone));
            }
        }

        section.add(new H3("Existing Seating Zones"));

        if (existingMap.getSeatingZones().isEmpty()) {
            section.add(new Paragraph("No existing seating zones."));
        } else {
            for (SeatingZoneDTO zone : existingMap.getSeatingZones()) {
                section.add(buildExistingSeatingZoneRow(zone));
            }
        }

        return section;
    }

    private HorizontalLayout buildExistingStandingZoneRow(StandingZoneDTO zone) {
        Span details = new Span(
                zone.getName()
                        + " | Available: "
                        + zone.getAvailable()
                        + "/"
                        + zone.getCapacty()
        );

        details.getStyle()
                .set("font-weight", "700");

        Button removeButton = new Button("Remove", e -> removeExistingZone(zone.getZoneId()));
        removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        HorizontalLayout row = new HorizontalLayout(details, removeButton);
        row.setWidthFull();
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        row.getStyle()
                .set("justify-content", "space-between")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "12px")
                .set("padding", "0.75rem 1rem")
                .set("background", "var(--lumo-contrast-5pct)");

        return row;
    }

    private HorizontalLayout buildExistingSeatingZoneRow(SeatingZoneDTO zone) {
        Span details = new Span(
                zone.getName()
                        + " | "
                        + zone.getRows()
                        + " x "
                        + zone.getCols()
                        + " seats"
        );

        details.getStyle()
                .set("font-weight", "700");

        Button removeButton = new Button("Remove", e -> removeExistingZone(zone.getZoneId()));
        removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        HorizontalLayout row = new HorizontalLayout(details, removeButton);
        row.setWidthFull();
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        row.getStyle()
                .set("justify-content", "space-between")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "12px")
                .set("padding", "0.75rem 1rem")
                .set("background", "var(--lumo-contrast-5pct)");

        return row;
    }

    private void removeExistingZone(int zoneId) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Remove Area");

        Paragraph warning = new Paragraph(
                "This will remove the area immediately if all tickets in it are still available. This action is saved right away."
        );

        warning.getStyle()
                .set("color", "var(--lumo-error-text-color)")
                .set("font-weight", "500");

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button removeButton = new Button("Remove", e -> {
            Response<Boolean> response = presenter.removeZoneFromEventMap(
                    getToken(),
                    eventId,
                    zoneId
            );

            if (Boolean.TRUE.equals(response.getValue())) {
                dialog.close();
                showSuccess("Zone removed successfully.");
                standingZoneForms.clear();
                seatingZoneForms.clear();
                buildPage();
                return;
            }

            showError(safeMessage(response.getMessage()));
        });

        removeButton.addThemeVariants(
                ButtonVariant.LUMO_ERROR,
                ButtonVariant.LUMO_PRIMARY
        );

        dialog.add(warning);
        dialog.getFooter().add(cancelButton, removeButton);
        dialog.open();
    }


    private VerticalLayout buildPreviewColumn() {
        VerticalLayout preview = cardSection();
        preview.setPadding(true);
        preview.setSpacing(true);

        H3 title = sectionTitle("🗺 Event Map Preview");
        Paragraph helper = new Paragraph(
                "Existing areas are faded and new areas are highlighted."
        );
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

    private void initializeDefaultsIfNeeded() {
        if (!standingZoneForms.isEmpty() || !seatingZoneForms.isEmpty()) {
            return;
        }

        int[] position = nextSeatingZonePosition();
        addSeatingZone("Seating Zone 1", 8, 10, 150.0, position[0], position[1]);
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

    private int nextStandingZoneIndex() {
        int index = 1;

        while (standingZoneNameExists("Standing Zone " + index)) {
            index++;
        }

        return index;
    }

    private boolean standingZoneNameExists(String name) {
        for (StandingZoneForm form : standingZoneForms) {
            if (name.equals(form.nameField.getValue())) {
                return true;
            }
        }

        if (existingMap != null) {
            for (StandingZoneDTO zone : existingMap.getStandingZones()) {
                if (name.equals(zone.getName())) {
                    return true;
                }
            }
        }

        return false;
    }

    private int nextSeatingZoneIndex() {
        int index = 1;

        while (seatingZoneNameExists("Seating Zone " + index)) {
            index++;
        }

        return index;
    }

    private boolean seatingZoneNameExists(String name) {
        for (SeatingZoneForm form : seatingZoneForms) {
            if (name.equals(form.nameField.getValue())) {
                return true;
            }
        }

        if (existingMap != null) {
            for (SeatingZoneDTO zone : existingMap.getSeatingZones()) {
                if (name.equals(zone.getName())) {
                    return true;
                }
            }
        }

        return false;
    }

    private int[] nextStandingZonePosition() {
        return nextZonePosition(70, 450, 230, 160);
    }

    private int[] nextSeatingZonePosition() {
        return nextZonePosition(270, 400, 320, 210);
    }

    private int[] nextZonePosition(int baseX, int baseY, int stepX, int stepY) {
        int nextX = baseX;
        int nextY = baseY;

        while (isPreviewPositionOccupied(nextX, nextY)) {
            nextX += stepX;

            if (nextX > 950) {
                nextX = baseX;
                nextY += stepY;
            }
        }

        return new int[]{nextX, nextY};
    }

    private boolean isPreviewPositionOccupied(int x, int y) {
        for (StandingZoneForm form : standingZoneForms) {
            if (isNear(form.xField.getValue(), form.yField.getValue(), x, y)) {
                return true;
            }
        }

        for (SeatingZoneForm form : seatingZoneForms) {
            if (isNear(form.xField.getValue(), form.yField.getValue(), x, y)) {
                return true;
            }
        }

        if (existingMap != null) {
            for (StandingZoneDTO zone : existingMap.getStandingZones()) {
                if (isNear(zone.getPosition().getX(), zone.getPosition().getY(), x, y)) {
                    return true;
                }
            }

            for (SeatingZoneDTO zone : existingMap.getSeatingZones()) {
                if (isNear(zone.getPosition().getX(), zone.getPosition().getY(), x, y)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isNear(Number existingX, Number existingY, int x, int y) {
        if (existingX == null || existingY == null) {
            return false;
        }

        return Math.abs(existingX.doubleValue() - x) < 230
                && Math.abs(existingY.doubleValue() - y) < 170;
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

    private void registerStandingZoneListeners(StandingZoneForm form) {
        addValidationAndMapListener(form.nameField);
        addValidationAndMapListener(form.capacityField);
        addValidationAndMapListener(form.priceField);
        addValidationAndMapListener(form.xField);
        addValidationAndMapListener(form.yField);
    }

    private void registerSeatingZoneListeners(SeatingZoneForm form) {
        addValidationAndMapListener(form.nameField);
        addValidationAndMapListener(form.rowsField);
        addValidationAndMapListener(form.columnsField);
        addValidationAndMapListener(form.priceField);
        addValidationAndMapListener(form.xField);
        addValidationAndMapListener(form.yField);
    }

    private void addValidationAndMapListener(AbstractField<?, ?> field) {
        field.addValueChangeListener(e -> {
            refreshMapPreview();
            updateSaveButtonState();
        });
    }

    private void updateSaveButtonState() {
        try {
            saveButton.setEnabled(isFormValid(false));
        } catch (Exception e) {
            saveButton.setEnabled(false);
        }
    }

    private boolean isFormValid(boolean showErrors) {
        if (standingZoneForms.isEmpty() && seatingZoneForms.isEmpty()) {
            return fail(showErrors, "At least one standing zone or seating zone is required.");
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

    private void submit() {
        if (!isFormValid(true)) {
            return;
        }

        Response<Boolean> response = presenter.addZonesToEventMap(
                getToken(),
                eventId,
                buildStandingZoneDTOs(),
                buildSeatingZoneDTOs()
        );

        if (response.getValue() == null || !response.getValue()) {
            showError(safeMessage(response.getMessage()));
            return;
        }

        showSuccess("Areas added successfully.");
        UI.getCurrent().getPage().getHistory().back();
    }

    private List<StandingZoneDTO> buildStandingZoneDTOs() {
        List<StandingZoneDTO> zones = new ArrayList<>();

        for (StandingZoneForm form : standingZoneForms) {
            zones.add(new StandingZoneDTO(
                    -1,
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
                    -1,
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

        addExistingMapPreview(canvas);

        for (StandingZoneForm form : standingZoneForms) {
            addStandingPreview(canvas, form, false);
        }

        for (SeatingZoneForm form : seatingZoneForms) {
            addSeatingPreview(canvas, form, false);
        }

        mapPreview.add(canvas);
    }

    private void addExistingMapPreview(Div canvas) {
        if (existingMap == null) {
            return;
        }

        ElementPositionDTO stage = existingMap.getStage();
        if (stage != null) {
            Div stageBox = previewBox("STAGE", "#111827", "white", "140px", "48px", "0.65");
            position(stageBox, (int) stage.getX(), (int) stage.getY());
            canvas.add(stageBox);
        }

        for (ElementPositionDTO entry : existingMap.getEntries()) {
            Div entryBox = previewBox("ENTRY", "#16a34a", "white", "100px", "38px", "0.45");
            position(entryBox, (int) entry.getX(), (int) entry.getY());
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
            position(standingBox, (int) zone.getPosition().getX(), (int) zone.getPosition().getY());
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
                .set("background", "rgba(255,255,255,0.45)")
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

    private void addStandingPreview(Div canvas, StandingZoneForm form, boolean existing) {
        if (form.xField.getValue() == null || form.yField.getValue() == null) {
            return;
        }

        String label = isBlank(form.nameField.getValue()) ? "Standing" : form.nameField.getValue();

        Div zone = previewBox(label, "#facc15", "#111827", "190px", "120px", existing ? "0.45" : "1");
        position(zone, form.xField.getValue(), form.yField.getValue());
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

        Span title = new Span(isBlank(form.nameField.getValue()) ? "Seating" : form.nameField.getValue());
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
                .set("font-weight", "800")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.14)");
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
                standingZoneForms.remove(this);
                refreshZoneContainers();
                refreshMapPreview();
                updateSaveButtonState();
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

            root.add(new H4("Seating Zone"), row1, row2);
        }
    }
}