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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
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

import java.util.ArrayList;
import java.util.List;

@Route(value = "event/:eventId(\\d+)/add-seating-area", layout = MainLayout.class)
@PageTitle("Add Seating Area")
@AnonymousAllowed
public class AddSeatingAreaView extends VerticalLayout implements BeforeEnterObserver {

    private final AddSeatingAreaPresenter presenter;
    private int eventId;

    private final VerticalLayout standingZonesContainer = new VerticalLayout();
    private final VerticalLayout seatingZonesContainer = new VerticalLayout();
    private final Div mapPreview = new Div();
    private final Button saveButton = new Button("Save New Areas");

    private final List<StandingZoneForm> standingZoneForms = new ArrayList<>();
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

        Button backButton = new Button("← Back", e -> UI.getCurrent().getPage().getHistory().back());
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        H2 title = new H2("Add Seating Areas");
        title.getStyle()
                .set("font-size", "2rem")
                .set("font-weight", "800")
                .set("margin-bottom", "0");

        Paragraph subtitle = new Paragraph("Add one or more standing or seating zones to an active event that already has a venue map.");
        subtitle.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "0");

        standingZonesContainer.setPadding(false);
        seatingZonesContainer.setPadding(false);

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setEnabled(false);
        saveButton.addClickListener(e -> submit());
        saveButton.getStyle()
                .set("height", "46px")
                .set("font-weight", "800")
                .set("border-radius", "14px");

        if (standingZoneForms.isEmpty() && seatingZoneForms.isEmpty()) {
            addSeatingZone("Seating Zone 1", 8, 10, 150.0, 270, 400);
        }

        add(
                backButton,
                title,
                subtitle,
                buildMainContent(),
                saveButton
        );

        refreshZoneContainers();
        refreshMapPreview();
        updateSaveButtonState();
    }

    private HorizontalLayout buildMainContent() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.START);
        layout.getStyle().set("gap", "1.5rem");

        VerticalLayout formColumn = cardSection();
        formColumn.setWidth("50%");
        formColumn.add(
                sectionTitle("New Areas"),
                buildActionsRow(),
                new H3("Standing Zones"),
                standingZonesContainer,
                new H3("Seating Zones"),
                seatingZonesContainer
        );

        VerticalLayout previewColumn = cardSection();
        previewColumn.setWidth("50%");

        mapPreview.getStyle()
                .set("position", "relative")
                .set("height", "700px")
                .set("width", "100%")
                .set("overflow", "auto")
                .set("background", "linear-gradient(135deg, #f8f9fb, #eef1f5)")
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "18px")
                .set("box-shadow", "0 6px 18px rgba(0,0,0,0.08)");

        previewColumn.add(
                sectionTitle("Preview of New Areas"),
                new Paragraph("This preview shows only the areas that will be added now."),
                mapPreview
        );

        layout.add(formColumn, previewColumn);
        return layout;
    }

    private HorizontalLayout buildActionsRow() {
        Button addStandingZoneButton = new Button("Add Standing Zone", e -> {
            int nextIndex = nextStandingZoneIndex();
            int[] position = nextZonePosition(70, 450, 230, 160);

            addStandingZone("Standing Zone " + nextIndex, 30, 80.0, position[0], position[1]);
            refreshZoneContainers();
            refreshMapPreview();
            updateSaveButtonState();
        });
        addStandingZoneButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button addSeatingZoneButton = new Button("Add Seating Zone", e -> {
            int nextIndex = nextSeatingZoneIndex();
            int[] position = nextZonePosition(270, 400, 320, 210);

            addSeatingZone("Seating Zone " + nextIndex, 8, 10, 150.0, position[0], position[1]);
            refreshZoneContainers();
            refreshMapPreview();
            updateSaveButtonState();
        });
        addSeatingZoneButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        HorizontalLayout row = new HorizontalLayout(addStandingZoneButton, addSeatingZoneButton);
        row.setWidthFull();
        row.getStyle().set("flex-wrap", "wrap");
        return row;
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

        return false;
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

        return false;
    }

    private boolean isNear(Integer existingX, Integer existingY, int x, int y) {
        if (existingX == null || existingY == null) {
            return false;
        }

        return Math.abs(existingX - x) < 230 && Math.abs(existingY - y) < 170;
    }

    private void registerStandingZoneListeners(StandingZoneForm form) {
        addValidationAndPreviewListener(form.nameField);
        addValidationAndPreviewListener(form.capacityField);
        addValidationAndPreviewListener(form.priceField);
        addValidationAndPreviewListener(form.xField);
        addValidationAndPreviewListener(form.yField);
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
        try {
            saveButton.setEnabled(isFormValid(false));
        } catch (Exception e) {
            saveButton.setEnabled(false);
        }
    }

    private boolean isFormValid(boolean showErrors) {
        if (standingZoneForms.isEmpty() && seatingZoneForms.isEmpty()) {
            return fail(showErrors, "At least one area must be added.");
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

        Response<Boolean> response = presenter.addZonesToEventMap(
                token,
                eventId,
                buildStandingZoneDTOs(),
                buildSeatingZoneDTOs()
        );

        if (response.getValue() == null || !response.getValue()) {
            showError(safeMessage(response.getMessage()));
            return;
        }

        showSuccess("Areas added to event map successfully.");
        UI.getCurrent().getPage().getHistory().back();
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

        for (StandingZoneForm form : standingZoneForms) {
            addStandingPreview(canvas, form);
        }

        for (SeatingZoneForm form : seatingZoneForms) {
            addSeatingPreview(canvas, form);
        }

        mapPreview.add(canvas);
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