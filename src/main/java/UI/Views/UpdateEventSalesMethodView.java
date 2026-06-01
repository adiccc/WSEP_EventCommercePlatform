package UI.Views;

import UI.Presenters.UpdateEventSalesMethodPresenter;
import application.CompanyService;
import application.LotteryService;
import application.Response;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.LotteryDTO;

@Route(value = "manage/company/:companyId(\\d+)/event/:eventId(\\d+)/sales-method", layout = MainLayout.class)
@PageTitle("Update Sales Method")
@AnonymousAllowed
public class UpdateEventSalesMethodView extends VerticalLayout implements BeforeEnterObserver {

    private final UpdateEventSalesMethodPresenter presenter;

    private int companyId;
    private int eventId;

    private final RadioButtonGroup<String> saleMethodGroup = new RadioButtonGroup<>();
    private final VerticalLayout lotteryFields = new VerticalLayout();

    private final DateTimePicker registerWindowField = new DateTimePicker("Lottery Registration End Time");
    private final IntegerField capacityField = new IntegerField("Number of Winners");
    private final IntegerField expirationHoursField = new IntegerField("Purchase Authorization Expiration Hours");

    private final Button saveButton = new Button("Save Sales Method");

    public UpdateEventSalesMethodView(
            LotteryService lotteryService,
            CompanyService companyService
    ) {
        this.presenter = new UpdateEventSalesMethodPresenter(lotteryService, companyService);

        setPadding(true);
        setSpacing(true);
        setWidthFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = Integer.parseInt(event.getRouteParameters().get("companyId").orElse("0"));
        eventId = Integer.parseInt(event.getRouteParameters().get("eventId").orElse("0"));

        buildPage();
    }

    private void buildPage() {
        removeAll();

        Button backButton = new Button("← Back", e -> UI.getCurrent().getPage().getHistory().back());
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        if (!presenter.canUpdateSalesMethod(getToken(), companyId)) {
            add(backButton, new Paragraph("You do not have permission to update this event sales method."));
            return;
        }

        H2 title = new H2("Update Event Sales Method");
        Paragraph subtitle = new Paragraph("Choose whether this event will use regular sale or lottery sale.");

        configureFields();

        add(
                backButton,
                title,
                subtitle,
                buildMainSection(),
                saveButton
        );

        refreshLotteryFields();
        updateSaveButtonState();
    }

    private VerticalLayout buildMainSection() {
        VerticalLayout section = cardSection();

        section.add(
                new H3("Sales Method"),
                saleMethodGroup,
                lotteryFields
        );

        return section;
    }

    private void configureFields() {
        saleMethodGroup.setLabel("Sales Method");
        saleMethodGroup.setItems("Regular Sale", "Lottery Sale");
        saleMethodGroup.setValue("Regular Sale");
        saleMethodGroup.addValueChangeListener(e -> {
            refreshLotteryFields();
            updateSaveButtonState();
        });

        capacityField.setMin(1);
        capacityField.setStepButtonsVisible(true);
        capacityField.setWidth("18rem");

        expirationHoursField.setMin(1);
        expirationHoursField.setStepButtonsVisible(true);
        expirationHoursField.setValue(24);
        expirationHoursField.setWidth("18rem");

        registerWindowField.setWidth("22rem");

        capacityField.addValueChangeListener(e -> updateSaveButtonState());
        expirationHoursField.addValueChangeListener(e -> updateSaveButtonState());
        registerWindowField.addValueChangeListener(e -> updateSaveButtonState());

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setEnabled(false);
        saveButton.addClickListener(e -> submit());
    }

    private void refreshLotteryFields() {
        lotteryFields.removeAll();
        lotteryFields.setPadding(true);
        lotteryFields.setSpacing(true);

        if (!isLotteryMode()) {
            lotteryFields.setVisible(false);
            return;
        }

        lotteryFields.setVisible(true);
        lotteryFields.getStyle()
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("border", "1px solid var(--lumo-primary-color-50pct)")
                .set("border-radius", "18px");

        lotteryFields.add(
                new H3("Lottery Details"),
                registerWindowField,
                capacityField,
                expirationHoursField
        );
    }

    private void submit() {
        if (!isFormValid(true)) {
            return;
        }

        Response<Boolean> response;

        if (isLotteryMode()) {
            LotteryDTO lotteryDTO = new LotteryDTO(
                    eventId,
                    capacityField.getValue(),
                    registerWindowField.getValue(),
                    expirationHoursField.getValue()
            );

            response = presenter.updateToLotterySale(getToken(), eventId, lotteryDTO);
        } else {
            response = presenter.updateToRegularSale(getToken(), eventId);
        }

        if (response.getValue() != null && response.getValue()) {
            showSuccess(response.getMessage());
            UI.getCurrent().getPage().getHistory().back();
            return;
        }

        showError(response.getMessage());
    }

    private boolean isFormValid(boolean showErrors) {
        if (saleMethodGroup.getValue() == null) {
            return fail(showErrors, "Please select a sales method.");
        }

        if (!isLotteryMode()) {
            return true;
        }

        if (registerWindowField.getValue() == null) {
            return fail(showErrors, "Lottery registration end time is required.");
        }

        if (capacityField.getValue() == null || capacityField.getValue() < 1) {
            return fail(showErrors, "Number of winners must be at least 1.");
        }

        if (expirationHoursField.getValue() == null || expirationHoursField.getValue() < 1) {
            return fail(showErrors, "Expiration hours must be at least 1.");
        }

        return true;
    }

    private boolean isLotteryMode() {
        return "Lottery Sale".equals(saleMethodGroup.getValue());
    }

    private void updateSaveButtonState() {
        try {
            saveButton.setEnabled(isFormValid(false));
        } catch (Exception e) {
            saveButton.setEnabled(false);
        }
    }

    private boolean fail(boolean showErrors, String message) {
        if (showErrors) {
            showError(message);
        }
        return false;
    }

    private VerticalLayout cardSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.getStyle()
                .set("background", "white")
                .set("border-radius", "20px")
                .set("padding", "2rem")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.08)");
        return section;
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
                message != null ? message : "Operation failed.",
                4000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}