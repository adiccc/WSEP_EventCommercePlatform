package UI.Views;

import UI.Presenters.EventDetailsPresenter;
import UI.Presenters.PurchasePresenter;
import application.EventService;
import application.LotteryService;
import application.Response;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.EventDetailsDTO;
import java.util.Locale;
import com.vaadin.flow.component.UI;
import application.ActiveOrderService;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Route(value = "event/:companyId/:eventId", layout = MainLayout.class)
@PageTitle("Event Details — EventCommerce")
@AnonymousAllowed
public class EventDetailsView extends VerticalLayout implements BeforeEnterObserver {
    private final PurchasePresenter purchasePresenter;
    private final EventDetailsPresenter presenter;
    private int companyId;
    private int eventId;

    public EventDetailsView(EventService eventService, ActiveOrderService activeOrderService, LotteryService lotteryService) {

        this.presenter = new EventDetailsPresenter(eventService, lotteryService);
        this.purchasePresenter = new PurchasePresenter(activeOrderService);

        setSpacing(true);
        setPadding(true);
        setWidthFull();

        getStyle()
                .set("background", "var(--lumo-contrast-5pct)");
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

        loadEvent(companyId, eventId);
    }

    private void loadEvent(int companyId, int eventId) {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        Button back = new Button(
                "← Back to Company",
                e -> getUI().ifPresent(
                        ui -> ui.navigate("company/" + companyId)
                )
        );

        back.getElement().setAttribute("theme", "tertiary");

        Response<EventDetailsDTO> response =
                presenter.getDetails(token, companyId, eventId);

        removeAll();

        if (response.getValue() == null) {

            add(
                    back,
                    new Paragraph(
                            "Error loading event: "
                                    + response.getMessage()
                    )
            );

            return;
        }

        EventDetailsDTO dto = response.getValue();

        add(
                back,
                buildHeader(dto),
                buildInfoSection(dto),
                buildPolicySection(dto)
        );
    }

    // =========================================================
    // Header
    // =========================================================

    private VerticalLayout buildHeader(EventDetailsDTO dto) {

        VerticalLayout header = new VerticalLayout();

        header.setPadding(true);
        header.setSpacing(true);

        header.getStyle()
                .set("background", "white")
                .set("border-radius", "20px")
                .set("padding", "2rem")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.08)");

        H1 name = new H1(dto.getName());

        name.getStyle()
                .set("margin", "0")
                .set("font-size", "2.2rem");

        HorizontalLayout chips = new HorizontalLayout();

        chips.setSpacing(true);

        chips.add(
                chip("📅 " + formatDate(dto.getDate())),
                chip("⏰ " + formatTime(dto.getDate())),
                chip("🗂 " + dto.getCategoryEvent()),
                chip("📍 " + dto.getLocation())
        );

        Button purchaseButton =
                new Button("🎟 Purchase Tickets");

        purchaseButton.getStyle()
                .set("margin-top", "1rem")
                .set("font-weight", "600")
                .set("background", "#2563eb")
                .set("color", "white")
                .set("padding", "0.8rem 1.4rem")
                .set("border-radius", "12px");

        purchaseButton.addClickListener(e -> handlePurchaseClick());

        Button lotteryButton =
                new Button("🎲 Register to Lottery");

        lotteryButton.getStyle()
                .set("margin-top", "1rem")
                .set("font-weight", "600")
                .set("background", "#7c3aed")
                .set("color", "white")
                .set("padding", "0.8rem 1.4rem")
                .set("border-radius", "12px");

        lotteryButton.addClickListener(e -> registerToLottery());

        HorizontalLayout actions = new HorizontalLayout();
        actions.setSpacing(true);

        actions.add(purchaseButton);

        if (dto.hasLottery()) {
            actions.add(lotteryButton);
        }

        header.add(
                name,
                chips,
                actions
        );

        return header;
    }

    private void registerToLottery() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");

        String token =
                (String) VaadinSession.getCurrent()
                        .getAttribute("token_" + tabId);

        Response<Boolean> response =
                presenter.registerUserToLottery(
                        token,
                        eventId
                );

        if (response.getValue() != null && response.getValue()) {
            Notification notification = Notification.show(
                    response.getMessage(),
                    4000,
                    Notification.Position.TOP_CENTER
            );
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            return;
        }

        Notification notification = Notification.show(
                response.getMessage(),
                5000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private void handlePurchaseClick() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        Response<Boolean> response =
                purchasePresenter.isRequiredLotteryCode(token, companyId, eventId);

        if (response.getValue() == null) {
            Notification.show(response.getMessage());
            return;
        }

        if (response.getValue()) {
            openLotteryCodeDialog();
            return;
        }

        UI.getCurrent().navigate("purchase/" + companyId + "/" + eventId);
    }

    private void openLotteryCodeDialog() {
        Dialog dialog = new Dialog();

        H3 title = new H3("Lottery Code Required");

        Paragraph message = new Paragraph(
                "This event is currently open only for lottery winners. Please enter your lottery code."
        );

        TextField codeField = new TextField("Lottery code");
        codeField.setWidthFull();
        codeField.setClearButtonVisible(true);
        codeField.addValueChangeListener(e -> {
            codeField.setInvalid(false);
            codeField.setErrorMessage(null);
        });
        Button submit = new Button("Continue", e -> {
            String code = codeField.getValue();

            if (code == null || code.isBlank()) {
                codeField.setInvalid(true);
                codeField.setErrorMessage("Please enter your lottery code");
                return;
            }

            String cleanedCode = code.trim();

            String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
            String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

            Response<Boolean> response =
                    purchasePresenter.validateLotteryCode(
                            token,
                            companyId,
                            eventId,
                            cleanedCode
                    );

            if (response.getValue() == null) {
                Notification.show(response.getMessage());
                return;
            }

            if (!response.getValue()) {
                codeField.setInvalid(true);
                codeField.setErrorMessage(response.getMessage());
                return;
            }

            dialog.close();

            UI.getCurrent().navigate(
                    "purchase/" + companyId + "/" + eventId,
                    QueryParameters.simple(Map.of("lotteryCode", cleanedCode))
            );
        });

        Button cancel = new Button("Cancel", e -> dialog.close());

        HorizontalLayout actions = new HorizontalLayout(cancel, submit);
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        actions.setWidthFull();

        VerticalLayout content = new VerticalLayout(
                title,
                message,
                codeField,
                actions
        );

        content.setPadding(false);
        content.setSpacing(true);
        content.setWidth("400px");

        dialog.add(content);
        dialog.open();
    }

    private VerticalLayout buildInfoSection(EventDetailsDTO dto) {

        VerticalLayout section =
                section("Event Information");

        section.add(
                infoRow(
                        "Sale Starts",
                        formatDateTime(dto.getSaleStartDate())
                )
        );

        return section;
    }

    // =========================================================
    // Policy Section
    // =========================================================

    private VerticalLayout buildPolicySection(EventDetailsDTO dto) {

        VerticalLayout section =
                section("Policies");

        section.add(
                infoRow(
                        "Purchase Policy",
                        dto.getPurchasePolicy()
                )
        );

        section.add(
                infoRow(
                        "Discount Policy",
                        dto.getDiscountPolicy()
                )
        );

        return section;
    }

    // =========================================================
    // Helpers
    // =========================================================

    private VerticalLayout section(String title) {

        VerticalLayout layout =
                new VerticalLayout();

        layout.setPadding(true);
        layout.setSpacing(false);

        layout.getStyle()
                .set("background", "white")
                .set("border-radius", "20px")
                .set("padding", "1.5rem")
                .set("box-shadow", "0 2px 10px rgba(0,0,0,0.05)");

        H3 header = new H3(title);

        header.getStyle()
                .set("margin-top", "0")
                .set("margin-bottom", "1rem");

        layout.add(header);

        return layout;
    }

    private Span chip(String text) {

        Span chip = new Span(text);

        chip.getStyle()
                .set("font-size", "0.95rem")
                .set("padding", "0.45rem 0.9rem")
                .set("border-radius", "999px")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("font-weight", "500");

        return chip;
    }

    private HorizontalLayout infoRow(
            String label,
            String value
    ) {

        Span labelSpan =
                new Span(label);

        labelSpan.getStyle()
                .set("font-weight", "600")
                .set("min-width", "180px")
                .set("color", "var(--lumo-secondary-text-color)");

        Span valueSpan =
                new Span(
                        value != null
                                ? value
                                : "—"
                );

        HorizontalLayout row =
                new HorizontalLayout(
                        labelSpan,
                        valueSpan
                );

        row.setWidthFull();

        row.setAlignItems(
                FlexComponent.Alignment.START
        );

        row.getStyle()
                .set("padding", "0.5rem 0");

        return row;
    }

    private String formatDate(String rawDate) {

        try {

            LocalDateTime parsed =
                    LocalDateTime.parse(rawDate);

            return parsed.format(
                    DateTimeFormatter.ofPattern(
                            "dd MMM yyyy",
                            Locale.ENGLISH
                    )
            );

        } catch (Exception e) {

            return rawDate;
        }
    }

    private String formatTime(String rawDate) {

        try {

            LocalDateTime parsed =
                    LocalDateTime.parse(rawDate);

            return parsed.format(
                    DateTimeFormatter.ofPattern(
                            "HH:mm",
                            Locale.ENGLISH
                    )
            );

        } catch (Exception e) {

            return "";
        }
    }

    private String formatDateTime(String rawDateTime) {

        try {

            LocalDateTime parsed =
                    LocalDateTime.parse(rawDateTime);

            return parsed.format(
                    DateTimeFormatter.ofPattern(
                            "dd MMM yyyy • HH:mm",
                            Locale.ENGLISH
                    )
            );

        } catch (Exception e) {

            return rawDateTime;
        }
    }
}