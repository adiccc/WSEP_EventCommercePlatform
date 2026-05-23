package UI.Views;

import DTO.PurchaseHistoryDTO;
import DTO.PurchasedTicketDTO;
import UI.Presenters.PurchaseHistoryPresenter;
import application.EventCompanyManageService;
import application.Response;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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

        add(new H2("My Purchase History"));

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        Response<List<PurchaseHistoryDTO>> response =
                presenter.getPurchaseHistory(token);

        if (response.getValue() == null) {
            showError(response.getMessage());
            add(new Paragraph(response.getMessage()));
            return;
        }

        if (response.getValue().isEmpty()) {
            add(new Paragraph(response.getMessage()));
            return;
        }

        for (PurchaseHistoryDTO purchase : response.getValue()) {

            VerticalLayout orderCard = new VerticalLayout();
            orderCard.getStyle()
                    .set("border", "1px solid #ccc")
                    .set("border-radius", "10px")
                    .set("padding", "1rem");

            orderCard.add(
                    new H4("Order #" + purchase.getOrderId()),
                    new Paragraph("Event: " + purchase.getEventName()),
                    new Paragraph("Date: " + purchase.getEventDate()),
                    new Paragraph("Location: " + purchase.getEventLocation()),
                    new Paragraph("Status: " + purchase.getStatus()),
                    new Paragraph("Total Paid: ₪" + purchase.getTotalSum())
            );

            Grid<PurchasedTicketDTO> ticketsGrid =
                    new Grid<>(PurchasedTicketDTO.class, false);

            ticketsGrid.addColumn(PurchasedTicketDTO::getTicketId)
                    .setHeader("Ticket ID");

            ticketsGrid.addColumn(PurchasedTicketDTO::getZoneName)
                    .setHeader("Zone");

            ticketsGrid.addColumn(PurchasedTicketDTO::getTicketType)
                    .setHeader("Type");

            ticketsGrid.addColumn(ticket ->
                            ticket.getRow() == null ? "-" : ticket.getRow())
                    .setHeader("Row");

            ticketsGrid.addColumn(ticket ->
                            ticket.getCol() == null ? "-" : ticket.getCol())
                    .setHeader("Seat");

            ticketsGrid.addColumn(PurchasedTicketDTO::getPriceAtPurchase)
                    .setHeader("Price");

            ticketsGrid.setItems(purchase.getPurchasedTickets());

            orderCard.add(ticketsGrid);

            add(orderCard);
        }
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