package UI.Views;

import DTO.CheckoutPriceDTO;
import DTO.PaymentDetailsDTO;
import UI.Presenters.CheckoutPresenter;
import application.ActiveOrderService;
import application.Response;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "checkout/:activeOrderId", layout = MainLayout.class)
@PageTitle("Checkout")
@AnonymousAllowed
public class CheckoutView extends VerticalLayout implements BeforeEnterObserver {

    private final CheckoutPresenter presenter;

    private int activeOrderId;
    private String token;
    private String appliedCouponCode;

    private final Span originalPrice = new Span();
    private final Span finalPrice = new Span();
    private final Paragraph eventDiscountDescription = new Paragraph();
    private final Paragraph companyDiscountDescription = new Paragraph();

    private final TextField couponCode = new TextField("Coupon code");

    private final PasswordField cardNumber = new PasswordField("Card number");
    private final TextField expirationDate = new TextField("Expiration date");
    private final PasswordField cvv = new PasswordField("CVV");
    private final TextField cardHolderId = new TextField("Card holder ID");
    private final IntegerField numberOfPayments = new IntegerField("Number of payments");

    public CheckoutView(ActiveOrderService activeOrderService) {
        this.presenter = new CheckoutPresenter(activeOrderService);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);
        getStyle().set("background", "#f6f8fb");

        configureFields();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        try {
            activeOrderId = Integer.parseInt(
                    event.getRouteParameters().get("activeOrderId").orElse("0")
            );
        } catch (NumberFormatException e) {
            showError("Invalid checkout page");
            event.rerouteTo("home");
            return;
        }

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        buildPage();
        prepareCheckout();
    }

    private void configureFields() {
        cardNumber.setAutocomplete(Autocomplete.OFF);
        cardNumber.getElement().setAttribute("autocomplete", "new-password");
        cardNumber.setRevealButtonVisible(true);

        cvv.setAutocomplete(Autocomplete.OFF);
        cvv.getElement().setAttribute("autocomplete", "new-password");
        cvv.setRevealButtonVisible(true);

        couponCode.setAutocomplete(Autocomplete.OFF);
        couponCode.getElement().setAttribute("autocomplete", "off");

        expirationDate.setAutocomplete(Autocomplete.OFF);
        cardHolderId.setAutocomplete(Autocomplete.OFF);

        cardNumber.setPlaceholder("Card number");
        expirationDate.setPlaceholder("MM/YY");
        cvv.setPlaceholder("CVV");
        cardHolderId.setPlaceholder("ID number");

        numberOfPayments.setMin(1);
        numberOfPayments.setValue(1);
        numberOfPayments.setStepButtonsVisible(true);

        cardNumber.setWidthFull();
        expirationDate.setWidthFull();
        cvv.setWidthFull();
        cardHolderId.setWidthFull();
        numberOfPayments.setWidthFull();
        couponCode.setWidthFull();
    }

    private void buildPage() {
        removeAll();

        Div page = new Div();
        page.setWidthFull();
        page.setMaxWidth("980px");
        page.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "1rem");

        H2 title = new H2("Checkout");
        title.getStyle().set("margin", "0");

        Paragraph subtitle = new Paragraph("Review your order price and complete payment.");
        subtitle.getStyle()
                .set("margin", "0")
                .set("color", "#64748b");

        VerticalLayout titleBlock = new VerticalLayout(title, subtitle);
        titleBlock.setPadding(false);
        titleBlock.setSpacing(false);

        HorizontalLayout header = new HorizontalLayout(titleBlock);        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.expand(titleBlock);

        Div priceCard = createCard();
        H3 priceTitle = new H3("Order Summary");

        Div finalPriceBox = new Div(finalPrice);
        finalPriceBox.getStyle()
                .set("background", "#e0f2fe")
                .set("border-radius", "14px")
                .set("padding", "1rem")
                .set("font-size", "1.35rem")
                .set("font-weight", "700")
                .set("color", "#075985");

        priceCard.add(
                priceTitle,
                labelValue("Original price", originalPrice),
                finalPriceBox,
                eventDiscountDescription,
                companyDiscountDescription
        );

        Button applyCoupon = new Button("Apply Coupon", e -> applyCoupon());
        applyCoupon.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout couponRow = new HorizontalLayout(couponCode, applyCoupon);
        couponRow.setWidthFull();
        couponRow.setAlignItems(Alignment.END);
        couponRow.expand(couponCode);

        Div paymentCard = createCard();
        H3 paymentTitle = new H3("Payment Details");

        HorizontalLayout paymentRow1 = new HorizontalLayout(cardNumber, expirationDate);
        paymentRow1.setWidthFull();

        HorizontalLayout paymentRow2 = new HorizontalLayout(cvv, cardHolderId, numberOfPayments);
        paymentRow2.setWidthFull();

        paymentCard.add(paymentTitle, paymentRow1, paymentRow2);

        Button pay = new Button("Pay Now", e -> pay());
        pay.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        pay.setWidth("220px");

        HorizontalLayout actions = new HorizontalLayout(pay);
        actions.setWidthFull();
        actions.setJustifyContentMode(JustifyContentMode.END);

        page.add(header, priceCard, couponRow, paymentCard, actions);
        add(page);
    }

    private Div createCard() {
        Div card = new Div();
        card.getStyle()
                .set("background", "white")
                .set("border", "1px solid #e2e8f0")
                .set("border-radius", "18px")
                .set("box-shadow", "0 8px 24px rgba(15, 23, 42, 0.08)")
                .set("padding", "1.5rem");
        return card;
    }

    private HorizontalLayout labelValue(String label, Span value) {
        Span labelSpan = new Span(label + ":");
        labelSpan.getStyle()
                .set("font-weight", "600")
                .set("color", "#475569");

        HorizontalLayout row = new HorizontalLayout(labelSpan, value);
        row.setAlignItems(Alignment.CENTER);
        row.setSpacing(true);
        return row;
    }

    private void prepareCheckout() {
        Response<CheckoutPriceDTO> response =
                presenter.prepareCheckout(token, activeOrderId);

        if (response.getValue() == null) {
            showError(response.getMessage());
            add(new Paragraph(response.getMessage()));
            return;
        }

        appliedCouponCode = null;
        updatePrice(response.getValue());
    }

    private void applyCoupon() {
        Response<CheckoutPriceDTO> response =
                presenter.applyCheckoutCoupon(token, activeOrderId, couponCode.getValue());

        if (response.getValue() == null) {
            showError(response.getMessage());
            return;
        }

        appliedCouponCode = couponCode.getValue();
        updatePrice(response.getValue());
        showSuccess("Coupon applied");
    }

    private void pay() {
        PaymentDetailsDTO paymentDetails = new PaymentDetailsDTO(
                cardNumber.getValue(),
                expirationDate.getValue(),
                cvv.getValue(),
                cardHolderId.getValue(),
                numberOfPayments.getValue(),
                appliedCouponCode
        );

        Response<Integer> response =
                presenter.checkoutAndPayment(token, activeOrderId, paymentDetails);

        if (response.getValue() == null) {
            showError(response.getMessage());

            if ("Ticket issuance failed".equals(response.getMessage())) {
                UI.getCurrent().navigate("my-orders");
            }

            return;
        }

        showSuccess("Payment completed successfully");
        UI.getCurrent().navigate("my-orders");
    }

    private void updatePrice(CheckoutPriceDTO price) {
        originalPrice.setText(formatPrice(price.getOriginalPrice()));
        finalPrice.setText("Final price: " + formatPrice(price.getFinalPrice()));
        eventDiscountDescription.setText("Event discounts: " + price.getEventDiscountDescription());
        companyDiscountDescription.setText("Company discounts: " + price.getCompanyDiscountDescription());
    }

    private String formatPrice(double price) {
        return String.format("NIS %.2f", price);
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(
                message,
                3000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
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