package UI.Views;

import UI.Presenters.EventDetailsPresenter;
import UI.Presenters.PurchasePresenter;
import application.ActiveOrderService;
import application.CompanyService;
import application.EventCompanyManageService;
import application.EventService;
import application.IAuth;
import application.LotteryService;
import application.Response;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.EventDetailsDTO;
import DTO.DiscountDTO;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import domain.policy.DiscountPolicyType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Route(value = "event/:companyId/:eventId", layout = MainLayout.class)
@PageTitle("Event Details — EventCommerce")
@AnonymousAllowed
public class EventDetailsView extends VerticalLayout implements BeforeEnterObserver {

    private final PurchasePresenter purchasePresenter;
    private final EventDetailsPresenter presenter;
    private int companyId;
    private int eventId;
    private String token;

    private Button lotteryButton;
    private EventDetailsDTO currentDto;

    public EventDetailsView(
            EventService eventService,
            ActiveOrderService activeOrderService,
            EventCompanyManageService eventCompanyManageService,
            CompanyService companyService,
            LotteryService lotteryService,
            IAuth auth
    ) {

        this.presenter = new EventDetailsPresenter(
                eventService,
                eventCompanyManageService,
                companyService,
                lotteryService,
                auth
        );

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

        token = (String) VaadinSession.getCurrent()
                .getAttribute("token_" + tabId);

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

        currentDto = response.getValue();

        add(
                back,
                buildHeader(currentDto),
                buildInfoSection(currentDto),
                buildPolicySection(currentDto)
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

        Button updateDateButton =
                new Button("📅 Update Event Date");

        updateDateButton.getStyle()
                .set("margin-top", "1rem")
                .set("font-weight", "600")
                .set("background", "#0891b2")
                .set("color", "white")
                .set("padding", "0.8rem 1.4rem")
                .set("border-radius", "12px");

        updateDateButton.addClickListener(e -> openUpdateDateDialog(dto));
        Button updateSalesMethodButton =
                new Button("🎲 Update Sales Method");

        updateSalesMethodButton.getStyle()
                .set("margin-top", "1rem")
                .set("font-weight", "600")
                .set("background", "#ca8a04")
                .set("color", "white")
                .set("padding", "0.8rem 1.4rem")
                .set("border-radius", "12px");

        Button addSeatingAreasButton =
                new Button("🪑 Add Zones");

        addSeatingAreasButton.getStyle()
                .set("margin-top", "1rem")
                .set("font-weight", "600")
                .set("background", "#0f766e")
                .set("color", "white")
                .set("padding", "0.8rem 1.4rem")
                .set("border-radius", "12px");
        updateSalesMethodButton.addClickListener(e -> {

            if (!dto.hasLottery() && saleAlreadyStarted(dto)) {

                showError(
                        "Lottery sale cannot be enabled after ticket sales already started."
                );

                return;
            }

            UI.getCurrent().navigate(
                    "manage/company/" + companyId + "/event/" + eventId + "/sales-method"
            );
        });
        if (!dto.hasLottery() && saleAlreadyStarted(dto)) {
            updateSalesMethodButton.getStyle()
                    .set("opacity", "0.55")
                    .set("cursor", "not-allowed")
                    .set("filter", "grayscale(0.25)");
            updateSalesMethodButton.getElement().setAttribute(
                    "title",
                    "Lottery sale cannot be enabled after ticket sales already started."
            );
            updateSalesMethodButton.setText("🎲 Update Sales Method");
        }
        addSeatingAreasButton.addClickListener(e ->
                UI.getCurrent().navigate(
                        "manage/event/" + eventId + "/add-seating-area"
                )
        );

        Button deleteEventButton =
                new Button("🗑 Remove Event");

        deleteEventButton.getStyle()
                .set("margin-top", "1rem")
                .set("font-weight", "600")
                .set("background", "#dc2626")
                .set("color", "white")
                .set("padding", "0.8rem 1.4rem")
                .set("border-radius", "12px");

        deleteEventButton.getElement().setProperty(
                "title",
                "Remove this event and refund all related orders"
        );

        deleteEventButton.addClickListener(e ->
                openDeleteEventDialog()
        );

        Button manageDiscountsButton =
                new Button("🏷 Manage Discounts");

        manageDiscountsButton.getStyle()
                .set("margin-top", "1rem")
                .set("font-weight", "600")
                .set("background", "#9333ea")
                .set("color", "white")
                .set("padding", "0.8rem 1.4rem")
                .set("border-radius", "12px");

        manageDiscountsButton.addClickListener(e ->
                openDiscountManagementDialog(dto)
        );

        lotteryButton =
                new Button("🎲 Register to Lottery");

        lotteryButton.getStyle()
                .set("margin-top", "1rem")
                .set("font-weight", "600")
                .set("background", "#7c3aed")
                .set("color", "white")
                .set("padding", "0.8rem 1.4rem")
                .set("border-radius", "12px");

        lotteryButton.addClickListener(e -> registerToLottery());

        VerticalLayout actionsWrapper = new VerticalLayout();
        actionsWrapper.setPadding(false);
        actionsWrapper.setSpacing(true);
        actionsWrapper.setWidthFull();

        HorizontalLayout userActions = new HorizontalLayout();
        userActions.setSpacing(true);
        userActions.setWidthFull();
        userActions.getStyle()
                .set("flex-wrap", "wrap")
                .set("gap", "0.75rem");

        HorizontalLayout managementActions = new HorizontalLayout();
        managementActions.setSpacing(true);
        managementActions.setWidthFull();
        managementActions.getStyle()
                .set("flex-wrap", "wrap")
                .set("gap", "0.75rem");

        userActions.add(purchaseButton);

        String token = getToken();

        if (dto.hasLottery()
                && isMember()
                && canRegisterToLottery()) {

            userActions.add(lotteryButton);
        }

        if (presenter.canUpdateEventDate(token, companyId)) {
            managementActions.add(updateDateButton);
            managementActions.add(addSeatingAreasButton);
        }

        if (presenter.canManageEventDiscounts(token, companyId)) {
            managementActions.add(manageDiscountsButton);
        }

        if (presenter.canUpdateSalesMethod(token, companyId)) {
            managementActions.add(updateSalesMethodButton);
        }

        if (presenter.canDeleteEvent(token, companyId)) {
            managementActions.add(deleteEventButton);
        }

        actionsWrapper.add(userActions);

        if (managementActions.getComponentCount() > 0) {
            Span managementLabel = new Span("Event Management");
            managementLabel.getStyle()
                    .set("font-size", "0.85rem")
                    .set("font-weight", "700")
                    .set("color", "var(--lumo-secondary-text-color)");

            actionsWrapper.add(managementLabel, managementActions);
        }

        header.add(
                name,
                chips,
                actionsWrapper
        );

        return header;
    }

    private void openUpdateDateDialog(EventDetailsDTO dto) {

        Dialog dialog = new Dialog();

        dialog.setHeaderTitle("Update Event Date");
        dialog.setWidth("420px");

        DateTimePicker newDateField =
                new DateTimePicker("New Event Date");

        newDateField.setMin(LocalDateTime.now());
        newDateField.setWidthFull();

        try {
            newDateField.setValue(LocalDateTime.parse(dto.getDate()));
        } catch (Exception ignored) {
        }

        Button cancelButton =
                new Button("Cancel", e -> dialog.close());

        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button updateButton = new Button("Update", e -> {

            LocalDateTime newDate = newDateField.getValue();

            if (newDate == null) {
                showError("Please select a new event date.");
                return;
            }

            Response<Boolean> response =
                    presenter.updateEventDate(
                            getToken(),
                            eventId,
                            newDate
                    );

            if (response.getValue() != null
                    && response.getValue()) {

                dialog.close();

                showSuccess("Event date updated successfully.");

                loadEvent(companyId, eventId);

                return;
            }

            showError(response.getMessage());
        });

        updateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout content =
                new VerticalLayout(newDateField);

        content.setPadding(false);
        content.setSpacing(true);

        dialog.add(content);

        dialog.getFooter().add(cancelButton, updateButton);

        dialog.open();
    }

    private void openDeleteEventDialog() {

        Dialog dialog = new Dialog();

        dialog.setHeaderTitle("Remove Event");

        Paragraph warning = new Paragraph(
                "This action will deactivate the event, refund relevant orders and notify purchasers. This action cannot be undone."
        );

        warning.getStyle()
                .set("color", "var(--lumo-error-text-color)")
                .set("font-weight", "500");

        Button cancelButton =
                new Button("Cancel", e -> dialog.close());

        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button deleteButton =
                new Button("Remove Event", e -> {

                    Response<Boolean> response =
                            presenter.deleteEvent(
                                    getToken(),
                                    eventId
                            );

                    if (response.getValue() != null
                            && response.getValue()) {

                        dialog.close();

                        showSuccess("Event removed successfully.");

                        UI.getCurrent().navigate(
                                "company/" + companyId
                        );

                        return;
                    }

                    showError(response.getMessage());
                });

        deleteButton.addThemeVariants(
                ButtonVariant.LUMO_ERROR,
                ButtonVariant.LUMO_PRIMARY
        );

        dialog.add(warning);

        dialog.getFooter().add(
                cancelButton,
                deleteButton
        );

        dialog.open();
    }

    private void openDiscountManagementDialog(EventDetailsDTO dto) {

        Dialog dialog = new Dialog();

        dialog.setHeaderTitle("Manage Event Discounts");
        dialog.setWidth("520px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        Paragraph currentPolicy = new Paragraph(
                dto.getDiscountPolicy() != null
                        ? dto.getDiscountPolicy()
                        : "No discount policy defined."
        );

        currentPolicy.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "0.75rem 1rem")
                .set("white-space", "pre-wrap")
                .set("width", "100%");

        ComboBox<DiscountPolicyType> policyTypeBox =
                new ComboBox<>("Discount Policy Type");

        policyTypeBox.setItems(DiscountPolicyType.values());
        policyTypeBox.setWidthFull();

        Button changePolicyTypeButton =
                new Button("Change Policy Type", e -> {

                    DiscountPolicyType selectedType =
                            policyTypeBox.getValue();

                    if (selectedType == null) {
                        showError("Please select a discount policy type.");
                        return;
                    }

                    Response<Void> response =
                            presenter.changeEventDiscountPolicyType(
                                    getToken(),
                                    eventId,
                                    selectedType
                            );

                    if (response.getMessage() != null
                            && response.getMessage().toLowerCase().contains("error")) {
                        showError(response.getMessage());
                        return;
                    }

                    showSuccess("Discount policy type updated successfully.");
                    dialog.close();
                    loadEvent(companyId, eventId);
                });

        changePolicyTypeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        ComboBox<DiscountDTO.Type> discountTypeBox =
                new ComboBox<>("Discount Type");

        discountTypeBox.setItems(
                DiscountDTO.Type.VISUAL,
                DiscountDTO.Type.CODE_COUPON,
                DiscountDTO.Type.MIN_QUANTITY,
                DiscountDTO.Type.MAX_QUANTITY,
                DiscountDTO.Type.DATE_RANGE
        );

        discountTypeBox.setWidthFull();

        NumberField percentageField =
                new NumberField("Percentage");

        percentageField.setMin(0);
        percentageField.setMax(100);
        percentageField.setWidthFull();
        percentageField.setHelperText("Enter discount percentage between 0 and 100");

        TextField codeField =
                new TextField("Coupon Code");

        codeField.setWidthFull();
        codeField.setVisible(false);

        IntegerField quantityField =
                new IntegerField("Quantity");

        quantityField.setMin(1);
        quantityField.setWidthFull();
        quantityField.setVisible(false);

        DatePicker startDateField =
                new DatePicker("Start Date");

        startDateField.setWidthFull();
        startDateField.setVisible(false);

        DatePicker endDateField =
                new DatePicker("End Date");

        endDateField.setWidthFull();

        discountTypeBox.addValueChangeListener(e -> {

            DiscountDTO.Type selectedType = e.getValue();

            boolean isCoupon =
                    selectedType == DiscountDTO.Type.CODE_COUPON;

            boolean isQuantity =
                    selectedType == DiscountDTO.Type.MIN_QUANTITY
                            || selectedType == DiscountDTO.Type.MAX_QUANTITY;

            boolean isDateRange =
                    selectedType == DiscountDTO.Type.DATE_RANGE;

            codeField.setVisible(isCoupon);
            quantityField.setVisible(isQuantity);
            startDateField.setVisible(isDateRange);
            endDateField.setVisible(
                    selectedType == DiscountDTO.Type.VISUAL
                            || selectedType == DiscountDTO.Type.CODE_COUPON
                            || isDateRange
            );
        });

        Button addDiscountButton =
                new Button("Add Discount", e -> {

                    DiscountDTO discount =
                            buildDiscountFromFields(
                                    discountTypeBox,
                                    percentageField,
                                    codeField,
                                    quantityField,
                                    startDateField,
                                    endDateField
                            );

                    if (discount == null) {
                        return;
                    }

                    Response<Boolean> response =
                            presenter.addDiscountToEvent(
                                    getToken(),
                                    eventId,
                                    discount
                            );

                    if (Boolean.TRUE.equals(response.getValue())) {
                        showSuccess("Discount added successfully.");
                        dialog.close();
                        loadEvent(companyId, eventId);
                        return;
                    }

                    showError(response.getMessage());
                });

        addDiscountButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        Button removeDiscountButton =
                new Button("Remove Matching Discount", e -> {

                    DiscountDTO discount =
                            buildDiscountFromFields(
                                    discountTypeBox,
                                    percentageField,
                                    codeField,
                                    quantityField,
                                    startDateField,
                                    endDateField
                            );

                    if (discount == null) {
                        return;
                    }

                    Response<Boolean> response =
                            presenter.removeDiscountFromEvent(
                                    getToken(),
                                    eventId,
                                    discount
                            );

                    if (Boolean.TRUE.equals(response.getValue())) {
                        showSuccess("Discount removed successfully.");
                        dialog.close();
                        loadEvent(companyId, eventId);
                        return;
                    }

                    showError(response.getMessage());
                });

        removeDiscountButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        HorizontalLayout actions =
                new HorizontalLayout(
                        addDiscountButton,
                        removeDiscountButton
                );

        content.add(
                new H4("Current Discount Policy"),
                currentPolicy,
                new H4("Policy Type"),
                policyTypeBox,
                changePolicyTypeButton,
                new Hr(),
                new H4("Add or Remove Discount"),
                discountTypeBox,
                percentageField,
                codeField,
                quantityField,
                startDateField,
                endDateField,
                actions
        );

        dialog.add(content);

        Button closeButton =
                new Button("Close", e -> dialog.close());

        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(closeButton);

        dialog.open();
    }

    private DiscountDTO buildDiscountFromFields(
            ComboBox<DiscountDTO.Type> discountTypeBox,
            NumberField percentageField,
            TextField codeField,
            IntegerField quantityField,
            DatePicker startDateField,
            DatePicker endDateField
    ) {

        DiscountDTO.Type type = discountTypeBox.getValue();
        Double percentage = percentageField.getValue();

        if (type == null) {
            showError("Please select a discount type.");
            return null;
        }

        if (percentage == null || percentage <= 0 || percentage > 100) {
            showError("Please enter a valid percentage between 1 and 100.");
            return null;
        }

        if (type == DiscountDTO.Type.VISUAL) {
            LocalDate endDate = endDateField.getValue();

            if (endDate == null) {
                showError("Please select an end date.");
                return null;
            }

            return new DiscountDTO(percentage, endDate);
        }

        if (type == DiscountDTO.Type.CODE_COUPON) {
            String code = codeField.getValue();

            if (code == null || code.isBlank()) {
                showError("Please enter a coupon code.");
                return null;
            }

            LocalDate endDate = endDateField.getValue();

            if (endDate == null) {
                showError("Please select an end date.");
                return null;
            }

            return new DiscountDTO(code.trim(), percentage, endDate);
        }

        if (type == DiscountDTO.Type.MIN_QUANTITY
                || type == DiscountDTO.Type.MAX_QUANTITY) {

            Integer quantity = quantityField.getValue();

            if (quantity == null || quantity < 1) {
                showError("Please enter a valid quantity.");
                return null;
            }

            return new DiscountDTO(type, percentage, quantity);
        }

        if (type == DiscountDTO.Type.DATE_RANGE) {
            LocalDate startDate = startDateField.getValue();
            LocalDate endDate = endDateField.getValue();

            if (startDate == null || endDate == null) {
                showError("Please select start and end dates.");
                return null;
            }

            if (endDate.isBefore(startDate)) {
                showError("End date cannot be before start date.");
                return null;
            }

            return new DiscountDTO(percentage, startDate, endDate);
        }

        showError("Unsupported discount type.");
        return null;
    }

    private boolean isMember() {

        if (token == null || token.isBlank()) {
            return false;
        }

        Response<String> roleResponse =
                presenter.getRole(token);

        return "MEMBER".equals(roleResponse.getValue());
    }

    private boolean canRegisterToLottery() {

        Response<Boolean> response =
                presenter.canRegisterToLottery(
                        token,
                        eventId
                );

        if (response.getValue() == null) {
            showError(response.getMessage());
            return false;
        }

        return response.getValue();
    }

    private void registerToLottery() {

        String tabId =
                UI.getCurrent()
                        .getElement()
                        .getProperty("currentTabId");

        String token =
                (String) VaadinSession.getCurrent()
                        .getAttribute("token_" + tabId);

        Response<Boolean> response =
                presenter.registerUserToLottery(
                        token,
                        eventId
                );

        if (response.getValue() != null
                && response.getValue()) {

            Notification notification = Notification.show(
                    response.getMessage(),
                    4000,
                    Notification.Position.TOP_CENTER
            );

            notification.addThemeVariants(
                    NotificationVariant.LUMO_SUCCESS
            );

            lotteryButton.setEnabled(false);
            lotteryButton.setText("✅ Registered to Lottery");

            return;
        }

        Notification notification = Notification.show(
                response.getMessage(),
                5000,
                Notification.Position.TOP_CENTER
        );

        notification.addThemeVariants(
                NotificationVariant.LUMO_ERROR
        );
    }

    private void handlePurchaseClick() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String currentToken = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        String role = presenter.getRole(currentToken).getValue();
        if ("GUEST".equals(role)) {
            int requiredAge = getRequiredMinAge(currentToken);
            if (requiredAge > 0) {
                openAgeConfirmationDialog(requiredAge, this::proceedToPurchase);
                return;
            }
        }

        proceedToPurchase();
    }

    private void proceedToPurchase() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String currentToken = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        Response<Boolean> response =
                purchasePresenter.isRequiredLotteryCode(currentToken, companyId, eventId);

        if (response.getValue() == null) {
            Notification.show(response.getMessage());
            return;
        }

        if (response.getValue()) {
            openLotteryCodeDialog();
            return;
        }

        UI.getCurrent().navigate(
                "purchase/" + companyId + "/" + eventId
        );
    }

    private int getRequiredMinAge(String currentToken) {
        String eventPolicy = currentDto != null ? currentDto.getPurchasePolicy() : null;
        return presenter.getRequiredMinAge(currentToken, companyId, eventPolicy);
    }

    private void openAgeConfirmationDialog(int requiredAge, Runnable onConfirm) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Age Verification Required");
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);

        Paragraph msg = new Paragraph(
                "This event requires attendees to be at least " + requiredAge
                        + " years old. Do you confirm that you meet this age requirement?");

        Button confirmBtn = new Button("Yes, I confirm", e -> {
            dialog.close();
            onConfirm.run();
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

        Button cancelBtn = new Button("No, cancel", e -> dialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        HorizontalLayout buttons = new HorizontalLayout(confirmBtn, cancelBtn);
        dialog.add(msg, buttons);
        dialog.open();
    }

    private void openLotteryCodeDialog() {

        Dialog dialog = new Dialog();

        H3 title =
                new H3("Lottery Code Required");

        Paragraph message = new Paragraph(
                "This event is currently open only for lottery winners. Please enter your lottery code."
        );

        TextField codeField =
                new TextField("Lottery code");

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
                codeField.setErrorMessage(
                        "Please enter your lottery code"
                );

                return;
            }

            String cleanedCode = code.trim();

            String tabId =
                    UI.getCurrent()
                            .getElement()
                            .getProperty("currentTabId");

            String token =
                    (String) VaadinSession.getCurrent()
                            .getAttribute("token_" + tabId);

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
                codeField.setErrorMessage(
                        response.getMessage()
                );

                return;
            }

            dialog.close();

            UI.getCurrent().navigate(
                    "purchase/" + companyId + "/" + eventId,
                    QueryParameters.simple(
                            Map.of(
                                    "lotteryCode",
                                    cleanedCode
                            )
                    )
            );
        });

        Button cancel =
                new Button("Cancel", e -> dialog.close());

        HorizontalLayout actions =
                new HorizontalLayout(cancel, submit);

        actions.setJustifyContentMode(
                FlexComponent.JustifyContentMode.END
        );

        actions.setWidthFull();

        VerticalLayout content =
                new VerticalLayout(
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

    private boolean saleAlreadyStarted(EventDetailsDTO dto) {
        try {
            return dto.getSaleStartDate() != null
                    && !LocalDateTime.parse(dto.getSaleStartDate()).isAfter(LocalDateTime.now());
        } catch (Exception e) {
            return false;
        }
    }

    private String getToken() {

        String tabId =
                UI.getCurrent()
                        .getElement()
                        .getProperty("currentTabId");

        return (String) VaadinSession.getCurrent()
                .getAttribute("token_" + tabId);
    }

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

        Span labelSpan = new Span(label);

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

    private void showSuccess(String message) {

        Notification notification = Notification.show(
                message,
                3000,
                Notification.Position.TOP_CENTER
        );

        notification.addThemeVariants(
                NotificationVariant.LUMO_SUCCESS
        );
    }

    private void showError(String message) {

        Notification notification = Notification.show(
                message,
                4000,
                Notification.Position.TOP_CENTER
        );

        notification.addThemeVariants(
                NotificationVariant.LUMO_ERROR
        );
    }
}