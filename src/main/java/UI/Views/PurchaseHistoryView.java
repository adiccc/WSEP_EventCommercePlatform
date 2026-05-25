package UI.Views;

import DTO.PurchaseHistoryDTO;
import DTO.PurchasedTicketDTO;
import UI.Presenters.PurchaseHistoryPresenter;
import application.EventCompanyManageService;
import application.Response;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
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

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

import java.util.List;

@Route(value = "my-orders", layout = MainLayout.class)
@PageTitle("Purchase History")
public class PurchaseHistoryView extends VerticalLayout {

    private final PurchaseHistoryPresenter presenter;

    public PurchaseHistoryView(EventCompanyManageService eventCompanyManageService) {
        this.presenter = new PurchaseHistoryPresenter(eventCompanyManageService);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);
        getStyle().set("background", "#f6f8fb");

        Div page = new Div();
        page.setWidthFull();
        page.setMaxWidth("1100px");
        page.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "1.25rem");

        H2 title = new H2("My Purchase History");
        title.getStyle().set("margin-bottom", "0");

        Paragraph subtitle = new Paragraph("Review your past orders and purchased tickets.");
        subtitle.getStyle()
                .set("margin-top", "0")
                .set("color", "#64748b");

        page.add(title, subtitle);

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        Response<List<PurchaseHistoryDTO>> response =
                presenter.getPurchaseHistory(token);

        if (response.getValue() == null) {
            showError(response.getMessage());
            page.add(createEmptyState(response.getMessage()));
            add(page);
            return;
        }

        if (response.getValue().isEmpty()) {
            page.add(createEmptyState(response.getMessage()));
            add(page);
            return;
        }

        for (PurchaseHistoryDTO purchase : response.getValue()) {
            page.add(createOrderCard(purchase));
        }

        add(page);
    }

    private Div createOrderCard(PurchaseHistoryDTO purchase) {
        Div card = new Div();
        card.getStyle()
                .set("background", "white")
                .set("border", "1px solid #e2e8f0")
                .set("border-radius", "18px")
                .set("box-shadow", "0 8px 24px rgba(15, 23, 42, 0.08)")
                .set("padding", "0")
                .set("overflow", "hidden")
                .set("margin-bottom", "0.75rem");

        Div header = new Div();
        header.getStyle()
                .set("background", "#0f172a")
                .set("color", "white")
                .set("padding", "1rem 1.25rem");

        HorizontalLayout headerRow = new HorizontalLayout();
        headerRow.setWidthFull();
        headerRow.setAlignItems(Alignment.CENTER);
        headerRow.setJustifyContentMode(JustifyContentMode.BETWEEN);

        VerticalLayout orderTitleBlock = new VerticalLayout();
        orderTitleBlock.setPadding(false);
        orderTitleBlock.setSpacing(false);

        H3 orderTitle = new H3("Order #" + purchase.getOrderId());
        orderTitle.getStyle()
                .set("margin", "0")
                .set("color", "white");

        Paragraph eventName = new Paragraph(purchase.getEventName());
        eventName.getStyle()
                .set("margin", "0.25rem 0 0 0")
                .set("color", "#cbd5e1");

        orderTitleBlock.add(orderTitle, eventName);

        Span statusBadge = new Span(String.valueOf(purchase.getStatus()));
        statusBadge.getStyle()
                .set("background", "#e0f2fe")
                .set("color", "#075985")
                .set("border-radius", "999px")
                .set("padding", "0.35rem 0.75rem")
                .set("font-size", "0.85rem")
                .set("font-weight", "700");

        headerRow.add(orderTitleBlock, statusBadge);
        header.add(headerRow);

        Div body = new Div();
        body.getStyle()
                .set("padding", "1.25rem")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "1rem");

        HorizontalLayout detailsRow = new HorizontalLayout();
        detailsRow.setWidthFull();
        detailsRow.setSpacing(true);

        detailsRow.add(
                createInfoBox("Date", purchase.getEventDate()),
                createInfoBox("Location", purchase.getEventLocation()),
                createInfoBox("Tickets", String.valueOf(purchase.getTicketCount())),
                createInfoBox("Total Paid", formatPrice(purchase.getTotalSum()))
        );

        Div ticketsSection = new Div();
        ticketsSection.getStyle()
                .set("border-top", "1px solid #e2e8f0")
                .set("padding-top", "1rem");

        H4 ticketsTitle = new H4("Purchased Tickets");
        ticketsTitle.getStyle().set("margin", "0 0 0.75rem 0");

        Grid<PurchasedTicketDTO> ticketsGrid =
                new Grid<>(PurchasedTicketDTO.class, false);

        ticketsGrid.addColumn(PurchasedTicketDTO::getTicketId)
                .setHeader("Ticket ID")
                .setAutoWidth(true);

        ticketsGrid.addColumn(PurchasedTicketDTO::getZoneName)
                .setHeader("Zone")
                .setAutoWidth(true);

        ticketsGrid.addColumn(PurchasedTicketDTO::getTicketType)
                .setHeader("Type")
                .setAutoWidth(true);

        ticketsGrid.addColumn(ticket ->
                        ticket.getRow() == null ? "-" : ticket.getRow())
                .setHeader("Row")
                .setAutoWidth(true);

        ticketsGrid.addColumn(ticket ->
                        ticket.getCol() == null ? "-" : ticket.getCol())
                .setHeader("Seat")
                .setAutoWidth(true);

        ticketsGrid.addColumn(ticket -> formatPrice(ticket.getPriceAtPurchase()))
                .setHeader("Price")
                .setAutoWidth(true);

        ticketsGrid.setItems(purchase.getPurchasedTickets());
        ticketsGrid.setAllRowsVisible(true);
        ticketsGrid.setWidthFull();

        ticketsSection.add(ticketsTitle, ticketsGrid);
        body.add(detailsRow, ticketsSection);

        card.add(header, body);
        return card;
    }

    private Div createInfoBox(String label, String value) {
        Div box = new Div();
        box.getStyle()
                .set("background", "#f8fafc")
                .set("border", "1px solid #e2e8f0")
                .set("border-radius", "14px")
                .set("padding", "0.85rem 1rem")
                .set("flex", "1");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("display", "block")
                .set("font-size", "0.8rem")
                .set("font-weight", "700")
                .set("color", "#64748b")
                .set("margin-bottom", "0.25rem");

        Span valueSpan = new Span(value);
        valueSpan.getStyle()
                .set("display", "block")
                .set("font-size", "1rem")
                .set("font-weight", "700")
                .set("color", "#0f172a");

        box.add(labelSpan, valueSpan);
        return box;
    }

    private Div createEmptyState(String message) {
        Div emptyState = new Div();
        emptyState.getStyle()
                .set("background", "white")
                .set("border", "1px solid #e2e8f0")
                .set("border-radius", "18px")
                .set("box-shadow", "0 8px 24px rgba(15, 23, 42, 0.08)")
                .set("padding", "2rem")
                .set("text-align", "center");

        Paragraph text = new Paragraph(message);
        text.getStyle()
                .set("margin", "0")
                .set("color", "#64748b");

        emptyState.add(text);
        return emptyState;
    }

    private String formatPrice(double price) {
        return String.format("NIS %.2f", price);
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