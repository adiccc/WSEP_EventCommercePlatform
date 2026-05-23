package UI.Views;

import DTO.NotifyDTO;
import DTO.NotifyType;
import application.ActiveOrderService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.shared.Registration;
import infrastructure.Broadcaster;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveObserver;

@Route(value = "waiting/:companyId/:eventId/:position", layout = MainLayout.class)
@PageTitle("Waiting Queue")
@AnonymousAllowed
public class WaitingQueueEventView extends VerticalLayout implements BeforeEnterObserver, BeforeLeaveObserver {
    private int companyId;
    private int eventId;
    private int position;

    private Registration eventQueueRegistration;
    private boolean leavingToPurchase = false;
    private final ActiveOrderService activeOrderService;

    public WaitingQueueEventView(ActiveOrderService activeOrderService) {
        this.activeOrderService = activeOrderService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = Integer.parseInt(
                event.getRouteParameters().get("companyId").orElse("0")
        );

        eventId = Integer.parseInt(
                event.getRouteParameters().get("eventId").orElse("0")
        );

        position = Integer.parseInt(
                event.getRouteParameters().get("position").orElse("0")
        );

        build();
        registerToEventQueueNotifications();
        saveEventQueueDataInBrowser();
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        if (leavingToPurchase) {
            return;
        }
        cancelEventQueueEntry();
    }

    private void cancelEventQueueEntry() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");

        if (tabId == null || tabId.isBlank()) {
            return;
        }

        String token = (String) VaadinSession.getCurrent()
                .getAttribute("eventQueueTabId_" + tabId);

        if (token == null || token.isBlank()) {
            return;
        }

        var response = activeOrderService.cancelEventQueueEntry(token, eventId);
        VaadinSession.getCurrent().setAttribute("eventQueueTabId_" + tabId, null);
        VaadinSession.getCurrent().setAttribute("eventQueueCompanyId_" + tabId, null);
        VaadinSession.getCurrent().setAttribute("eventQueueEventId_" + tabId, null);

        UI.getCurrent().getPage().executeJs(
                """
                sessionStorage.removeItem("eventCommerceEventQueueToken");
                sessionStorage.removeItem("eventCommerceEventQueueEventId");
                """
        );
    }

    private void registerToEventQueueNotifications() {
        unregisterFromBroadcaster();
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");

        String eventQueueTabId =
                (String) VaadinSession.getCurrent().getAttribute("eventQueueTabId_" + tabId);

        if (eventQueueTabId == null || eventQueueTabId.isBlank()) {
            showError("Missing event queue identifier. Please try entering the purchase again.");
            return;
        }

        UI ui = UI.getCurrent();

        eventQueueRegistration = Broadcaster.registerTab(eventQueueTabId, notification -> {
            if (ui != null) {
                ui.access(() -> handleNotification(notification));
            }
        });

        addDetachListener(event -> unregisterFromBroadcaster());
    }

    private void handleNotification(NotifyDTO notification) {
        if (notification == null || notification.getType() == null) {
            return;
        }

        if (notification.getType() == NotifyType.QUEUE_EVENT_TURN_ARRIVED) {
            handleEventQueueTurnArrived(notification);
        }
    }

    private void handleEventQueueTurnArrived(NotifyDTO notification) {
        Integer notifiedCompanyId = null;
        Integer notifiedEventId = null;

        if (notification.getPayload() != null) {
            notifiedCompanyId = notification.getPayload().getCompanyId();
            notifiedEventId = notification.getPayload().getEventId();
        }

        if (notifiedCompanyId != null && notifiedCompanyId != companyId) {
            return;
        }

        if (notifiedEventId != null && notifiedEventId != eventId) {
            return;
        }

        unregisterFromBroadcaster();
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        VaadinSession.getCurrent().setAttribute("eventQueueAdmitted_" + tabId, true);
        VaadinSession.getCurrent().setAttribute("eventQueueCompanyId_" + tabId, companyId);
        VaadinSession.getCurrent().setAttribute("eventQueueEventId_" + tabId, eventId);
        VaadinSession.getCurrent().setAttribute("eventQueueTabId_" + tabId, null);

        UI.getCurrent().getPage().executeJs(
                """
                sessionStorage.removeItem("eventCommerceEventQueueToken");
                sessionStorage.removeItem("eventCommerceEventQueueEventId");
                """
        );
        showSuccess(getMessageOrDefault(
                notification,
                "Your turn has arrived. Redirecting to ticket selection."
        ));
        leavingToPurchase = true;
        UI.getCurrent().navigate("purchase/" + companyId + "/" + eventId);
    }

    private void unregisterFromBroadcaster() {
        if (eventQueueRegistration != null) {
            eventQueueRegistration.remove();
            eventQueueRegistration = null;
        }
    }

    private void build() {
        removeAll();

        H1 title = new H1("You are in the waiting queue");

        Paragraph message = new Paragraph(
                "The event is currently full. Your position in line is:"
        );

        H2 positionText = new H2("#" + position);

        Paragraph helper = new Paragraph(
                "You will be moved automatically to ticket selection when it is your turn."
        );
        helper.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "0");

        Button refresh = new Button("Try Again", e -> {
            leavingToPurchase = true;
            UI.getCurrent().navigate("purchase/" + companyId + "/" + eventId);
        });

        Button back = new Button("Back to Event", e -> {
            unregisterFromBroadcaster();
            cancelEventQueueEntry();
            UI.getCurrent().navigate("event/" + companyId + "/" + eventId);
        });

        add(title, message, positionText, helper, refresh, back);
    }

    private String getMessageOrDefault(NotifyDTO notification, String defaultMessage) {
        if (notification == null
                || notification.getPayload() == null
                || notification.getPayload().getMessage() == null
                || notification.getPayload().getMessage().isBlank()) {
            return defaultMessage;
        }

        return notification.getPayload().getMessage();
    }

    private void saveEventQueueDataInBrowser() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");

        if (tabId == null || tabId.isBlank()) {
            return;
        }

        String token = (String) VaadinSession.getCurrent()
                .getAttribute("eventQueueTabId_" + tabId);

        if (token == null || token.isBlank()) {
            return;
        }

        UI.getCurrent().getPage().executeJs(
                """
                sessionStorage.setItem("eventCommerceEventQueueToken", $0);
                sessionStorage.setItem("eventCommerceEventQueueEventId", $1);
                """,
                token,
                String.valueOf(eventId)
        );
    }

    private void showSuccess(String text) {
        Notification notification = Notification.show(
                text,
                3000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String text) {
        Notification notification = Notification.show(
                text,
                4000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}