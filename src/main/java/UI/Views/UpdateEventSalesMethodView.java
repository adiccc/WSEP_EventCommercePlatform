package UI.Views;

import UI.Presenters.UpdateEventSalesMethodPresenter;
import application.CompanyService;
import application.EventService;
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
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.EventDetailsDTO;
import domain.dto.LotteryDTO;

@Route(value = "manage/company/:companyId(\\d+)/event/:eventId(\\d+)/sales-method", layout = MainLayout.class)
@PageTitle("Update Sales Method")
@AnonymousAllowed
public class UpdateEventSalesMethodView extends VerticalLayout implements BeforeEnterObserver {

    private final UpdateEventSalesMethodPresenter presenter;

    private int companyId;
    private int eventId;

    private EventDetailsDTO currentEvent;

    private final VerticalLayout lotterySection = new VerticalLayout();

    private final IntegerField lotteryCapacityField =
            new IntegerField("Lottery Capacity");

    private final DateTimePicker lotteryDateField =
            new DateTimePicker("Lottery Draw Time");

    private final IntegerField lotteryExpirationHoursField =
            new IntegerField("Code Expiration Hours");

    public UpdateEventSalesMethodView(
            LotteryService lotteryService,
            CompanyService companyService,
            EventService eventService
    ) {
        this.presenter = new UpdateEventSalesMethodPresenter(
                lotteryService,
                companyService,
                eventService
        );

        setPadding(true);
        setSpacing(true);
        setWidthFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {

        companyId = Integer.parseInt(
                event.getRouteParameters()
                        .get("companyId")
                        .orElse("0")
        );

        eventId = Integer.parseInt(
                event.getRouteParameters()
                        .get("eventId")
                        .orElse("0")
        );

        buildPage();
    }

    private void buildPage() {

        removeAll();

        Button backButton =
                new Button(
                        "← Back",
                        e -> UI.getCurrent()
                                .getPage()
                                .getHistory()
                                .back()
                );

        backButton.addThemeVariants(
                ButtonVariant.LUMO_TERTIARY
        );

        if (!presenter.canUpdateSalesMethod(getToken(), companyId)) {

            add(
                    backButton,
                    new Paragraph(
                            "You do not have permission to update this event sales method."
                    )
            );

            return;
        }

        Response<EventDetailsDTO> response =
                presenter.getEventDetails(
                        getToken(),
                        companyId,
                        eventId
                );

        if (response.getValue() == null) {

            add(
                    backButton,
                    new Paragraph(
                            "Failed loading event: "
                                    + response.getMessage()
                    )
            );

            return;
        }

        currentEvent = response.getValue();

        H2 title =
                new H2("Update Event Sales Method");

        title.getStyle()
                .set("font-size", "2rem")
                .set("font-weight", "800");

        Paragraph subtitle =
                new Paragraph(
                        currentEvent.hasLottery()
                                ? "This event currently uses lottery sale."
                                : "This event currently uses regular sale."
                );

        subtitle.getStyle()
                .set(
                        "color",
                        "var(--lumo-secondary-text-color)"
                );

        configureFields();

        add(
                backButton,
                title,
                subtitle,
                buildMainSection()
        );
    }

    private VerticalLayout buildMainSection() {

        VerticalLayout section =
                cardSection();

        if (!currentEvent.hasLottery() && saleAlreadyStarted()) {
            section.add(
                    new H3("Switch To Lottery Sale"),
                    new Paragraph("This event sale has already started, so it cannot be changed to lottery sale.")
            );

            return section;
        }

        if (currentEvent.hasLottery()) {

            section.add(
                    new H3("Switch To Regular Sale"),
                    new Paragraph(
                            "This will cancel the lottery if winners were not notified yet."
                    ),
                    buildSwitchToRegularButton()
            );

            return section;
        }

        buildLotterySection();

        section.add(
                new H3("Switch To Lottery Sale"),
                new Paragraph(
                        "Configure the lottery settings for this event."
                ),
                lotterySection,
                buildSwitchToLotteryButton()
        );

        return section;
    }

    private void configureFields() {

        lotteryCapacityField.setMin(1);
        lotteryCapacityField.setStepButtonsVisible(true);
        lotteryCapacityField.setWidth("18rem");

        lotteryDateField.setWidth("20rem");

        lotteryExpirationHoursField.setMin(1);
        lotteryExpirationHoursField.setStepButtonsVisible(true);
        lotteryExpirationHoursField.setValue(24);
        lotteryExpirationHoursField.setWidth("18rem");
    }

    private void buildLotterySection() {

        lotterySection.removeAll();

        lotterySection.setPadding(true);
        lotterySection.setSpacing(true);

        lotterySection.getStyle()
                .set(
                        "background",
                        "var(--lumo-primary-color-10pct)"
                )
                .set(
                        "border",
                        "1px solid var(--lumo-primary-color-50pct)"
                )
                .set(
                        "border-radius",
                        "18px"
                );

        HorizontalLayout row =
                new HorizontalLayout(
                        lotteryCapacityField,
                        lotteryDateField,
                        lotteryExpirationHoursField
                );

        row.setWidthFull();
        row.getStyle().set("flex-wrap", "wrap");

        lotterySection.add(
                new H3("🎲 Lottery Setup"),
                new Paragraph(
                        "Fill all lottery fields before saving."
                ),
                row
        );
    }

    private Button buildSwitchToRegularButton() {

        Button button =
                new Button(
                        "Switch To Regular Sale"
                );

        button.addThemeVariants(
                ButtonVariant.LUMO_PRIMARY,
                ButtonVariant.LUMO_ERROR
        );

        button.addClickListener(e -> {

            Response<Boolean> response =
                    presenter.updateToRegularSale(
                            getToken(),
                            eventId
                    );

            if (response.getValue() != null
                    && response.getValue()) {

                showSuccess(response.getMessage());

                UI.getCurrent()
                        .getPage()
                        .getHistory()
                        .back();

                return;
            }

            showError(response.getMessage());
        });

        return button;
    }

    private Button buildSwitchToLotteryButton() {

        Button button =
                new Button(
                        "Switch To Lottery Sale"
                );

        button.addThemeVariants(
                ButtonVariant.LUMO_PRIMARY
        );

        button.addClickListener(e -> submitLottery());

        return button;
    }

    private void submitLottery() {

        if (!isLotteryValid(true)) {
            return;
        }

        LotteryDTO lotteryDTO =
                new LotteryDTO(
                        eventId,
                        lotteryCapacityField.getValue(),
                        lotteryDateField.getValue(),
                        lotteryExpirationHoursField.getValue()
                );

        Response<Boolean> response =
                presenter.updateToLotterySale(
                        getToken(),
                        eventId,
                        lotteryDTO
                );

        if (response.getValue() != null
                && response.getValue()) {

            showSuccess(response.getMessage());

            UI.getCurrent()
                    .getPage()
                    .getHistory()
                    .back();

            return;
        }

        showError(response.getMessage());
    }

    private boolean isLotteryValid(boolean showErrors) {

        if (lotteryCapacityField.getValue() == null
                || lotteryCapacityField.getValue() < 1) {

            return fail(
                    showErrors,
                    "Lottery capacity must be at least 1."
            );
        }

        if (lotteryDateField.getValue() == null) {

            return fail(
                    showErrors,
                    "Lottery draw time is required."
            );
        }

        if (lotteryExpirationHoursField.getValue() == null
                || lotteryExpirationHoursField.getValue() < 1) {

            return fail(
                    showErrors,
                    "Code expiration hours must be at least 1."
            );
        }

        return true;
    }

    private boolean fail(
            boolean showErrors,
            String message
    ) {

        if (showErrors) {
            showError(message);
        }

        return false;
    }

    private VerticalLayout cardSection() {

        VerticalLayout section =
                new VerticalLayout();

        section.setPadding(true);
        section.setSpacing(true);

        section.getStyle()
                .set("background", "white")
                .set("border-radius", "20px")
                .set("padding", "2rem")
                .set(
                        "box-shadow",
                        "0 4px 12px rgba(0,0,0,0.08)"
                );

        return section;
    }

    private String getToken() {

        String tabId =
                UI.getCurrent()
                        .getElement()
                        .getProperty("currentTabId");

        return (String) VaadinSession.getCurrent()
                .getAttribute("token_" + tabId);
    }

    private boolean saleAlreadyStarted() {
        try {
            return currentEvent.getSaleStartDate() != null
                    && !java.time.LocalDateTime.parse(currentEvent.getSaleStartDate()).isAfter(java.time.LocalDateTime.now());
        } catch (Exception e) {
            return false;
        }
    }

    private void showSuccess(String message) {

        Notification notification =
                Notification.show(
                        message,
                        3000,
                        Notification.Position.TOP_CENTER
                );

        notification.addThemeVariants(
                NotificationVariant.LUMO_SUCCESS
        );
    }

    private void showError(String message) {

        Notification notification =
                Notification.show(
                        message != null
                                ? message
                                : "Operation failed.",
                        4000,
                        Notification.Position.TOP_CENTER
                );

        notification.addThemeVariants(
                NotificationVariant.LUMO_ERROR
        );
    }
}