package UI.Views;

import DTO.NotifyDTO;
import DTO.NotifyType;
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

@Route(value = "waiting/:companyId/:eventId/:position", layout = MainLayout.class)
@PageTitle("Waiting Queue")
@AnonymousAllowed
public class WaitingQueueEventView extends VerticalLayout implements BeforeEnterObserver {

    private int companyId;
    private int eventId;
    private int position;

    private Registration eventQueueRegistration;

    public WaitingQueueEventView() {
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
    }

    private void registerToEventQueueNotifications() {
        unregisterFromBroadcaster();

        String eventQueueTabId =
                (String) VaadinSession.getCurrent().getAttribute("eventQueueTabId");

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

        VaadinSession.getCurrent().setAttribute("eventQueueAdmitted", true);
        VaadinSession.getCurrent().setAttribute("eventQueueCompanyId", companyId);
        VaadinSession.getCurrent().setAttribute("eventQueueEventId", eventId);

        showSuccess(getMessageOrDefault(
                notification,
                "Your turn has arrived. Redirecting to ticket selection."
        ));

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

        Button refresh = new Button("Try Again", e ->
                UI.getCurrent().navigate("purchase/" + companyId + "/" + eventId)
        );

        Button back = new Button("Back to Event", e -> {
            unregisterFromBroadcaster();
            VaadinSession.getCurrent().setAttribute("eventQueueTabId", null);
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