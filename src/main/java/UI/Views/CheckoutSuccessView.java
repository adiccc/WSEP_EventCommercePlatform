package UI.Views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

@Route(value = "checkout-success/:orderId", layout = MainLayout.class)
@PageTitle("Order Successful")
@AnonymousAllowed
public class CheckoutSuccessView extends VerticalLayout implements BeforeEnterObserver {

    private String orderId;
    private List<String> barcodes;

    public CheckoutSuccessView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);
        getStyle().set("background", "#f6f8fb");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void beforeEnter(BeforeEnterEvent event) {
        orderId = event.getRouteParameters().get("orderId").orElse("");
        if (orderId.isBlank()) {
            event.rerouteTo("home");
            return;
        }

        String sessionKey = "guestTickets_" + orderId;
        barcodes = (List<String>) VaadinSession.getCurrent().getAttribute(sessionKey);

        VaadinSession.getCurrent().setAttribute(sessionKey, null);

        buildPage();
    }

    private void buildPage() {
        removeAll();

        Div card = new Div();
        card.setMaxWidth("600px");
        card.setWidthFull();
        card.getStyle()
                .set("background", "white")
                .set("border", "1px solid #e2e8f0")
                .set("border-radius", "18px")
                .set("box-shadow", "0 8px 24px rgba(15, 23, 42, 0.08)")
                .set("padding", "2.5rem 2rem")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("gap", "1.5rem")
                .set("text-align", "center");

        H2 title = new H2("Payment Successful! 🎉");
        title.getStyle().set("margin", "0").set("color", "#10b981");

        Paragraph subtitle = new Paragraph("Thank you for your purchase. Your Order ID is #" + orderId + ".");
        subtitle.getStyle().set("margin", "0").set("color", "#64748b").set("font-size", "1.1rem");

        card.add(title, subtitle);

        if (barcodes != null && !barcodes.isEmpty()) {
            Paragraph instructions = new Paragraph("Please save these ticket codes for your event. You will need them for entry:");
            instructions.getStyle().set("font-weight", "600").set("color", "#0f172a").set("margin-top", "1rem");
            card.add(instructions);

            Div barcodesContainer = new Div();
            barcodesContainer.setWidthFull();
            barcodesContainer.getStyle()
                    .set("display", "flex")
                    .set("flex-direction", "column")
                    .set("gap", "0.75rem");

            for (String code : barcodes) {
                Div codeBox = new Div();
                codeBox.setText(code);
                codeBox.getStyle()
                        .set("background", "#f8fafc")
                        .set("border", "2px dashed #cbd5e1")
                        .set("border-radius", "12px")
                        .set("padding", "1rem")
                        .set("font-family", "monospace")
                        .set("font-size", "1.35rem")
                        .set("font-weight", "bold")
                        .set("letter-spacing", "2px")
                        .set("color", "#0369a1"); // כחול כהה לברקוד
                barcodesContainer.add(codeBox);
            }
            card.add(barcodesContainer);

        } else {
            Paragraph noCodes = new Paragraph("We are processing your tickets. If you don't receive them shortly, please contact support.");
            noCodes.getStyle().set("color", "#ef4444").set("background", "#fee2e2").set("padding", "1rem").set("border-radius", "8px");
            card.add(noCodes);
        }

        Button homeButton = new Button("Back to Home", e -> UI.getCurrent().navigate("home"));
        homeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        homeButton.setWidthFull();
        homeButton.getStyle().set("margin-top", "1rem");

        card.add(homeButton);
        add(card);
    }
}