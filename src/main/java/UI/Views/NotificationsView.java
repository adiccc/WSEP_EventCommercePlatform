package UI.Views;

import DTO.NotifyDTO;
import UI.Presenters.NotificationsPresenter;
import application.Response;
import application.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

@Route(value = "notifications", layout = MainLayout.class)
@PageTitle("Notifications")
@AnonymousAllowed
public class NotificationsView extends VerticalLayout {

    private final NotificationsPresenter presenter;
    private final VerticalLayout notificationsContainer;
    private final Button markAllAsReadButton;

    public NotificationsView(UserService userService) {
        this.presenter = new NotificationsPresenter(userService);

        setPadding(true);
        setSpacing(true);
        setWidthFull();

        H2 title = new H2("Notifications");
        title.getStyle().set("margin-bottom", "0");

        Paragraph subtitle = new Paragraph("Messages you missed while you were offline.");
        subtitle.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "0");

        markAllAsReadButton = new Button(
                "Mark all as read",
                VaadinIcon.CHECK.create(),
                event -> markAllAsRead()
        );
        markAllAsReadButton.getElement().setAttribute("theme", "primary");

        notificationsContainer = new VerticalLayout();
        notificationsContainer.setPadding(false);
        notificationsContainer.setSpacing(true);
        notificationsContainer.setWidthFull();

        add(
                title,
                subtitle,
                markAllAsReadButton,
                notificationsContainer
        );

        loadNotifications();
    }

    private void loadNotifications() {
        notificationsContainer.removeAll();

        String userEmail = getUserEmail();

        if (userEmail == null || userEmail.isBlank()) {
            markAllAsReadButton.setVisible(false);
            notificationsContainer.add(
                    buildEmptyState("Notifications are available only for logged-in members.")
            );
            return;
        }

        List<NotifyDTO> notifications = presenter.getDelayedNotifications(userEmail);

        if (notifications == null || notifications.isEmpty()) {
            markAllAsReadButton.setVisible(false);
            notificationsContainer.add(buildEmptyState("No missed notifications."));
            return;
        }

        markAllAsReadButton.setVisible(true);

        for (NotifyDTO notification : notifications) {
            notificationsContainer.add(buildNotificationCard(notification));
        }
    }

    private void markAllAsRead() {
        String userEmail = getUserEmail();

        if (userEmail == null || userEmail.isBlank()) {
            showError("Could not identify logged-in user.");
            return;
        }

        Response<Boolean> response = presenter.cleanDelayedNotifications(userEmail);

        if (response != null && Boolean.TRUE.equals(response.getValue())) {
            showSuccess("All notifications marked as read.");
            loadNotifications();
        } else {
            showError(response != null ? response.getMessage() : "Failed to clean notifications.");
        }
    }

    private String getUserEmail() {
        return (String) VaadinSession.getCurrent()
                .getAttribute("notificationUserIdentifier");
    }

    private Div buildNotificationCard(NotifyDTO notification) {
        Div card = new Div();

        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "1rem")
                .set("width", "100%")
                .set("box-sizing", "border-box");

        Span typeBadge = new Span(getNotificationType(notification));
        typeBadge.getStyle()
                .set("font-size", "0.75rem")
                .set("font-weight", "600")
                .set("color", "var(--lumo-primary-text-color)")
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("border-radius", "1rem")
                .set("padding", "0.25rem 0.6rem");

        Paragraph message = new Paragraph(getNotificationMessage(notification));
        message.getStyle()
                .set("margin-top", "0.75rem")
                .set("margin-bottom", "0");

        card.add(typeBadge, message);
        return card;
    }

    private String getNotificationMessage(NotifyDTO notification) {
        if (notification == null
                || notification.getPayload() == null
                || notification.getPayload().getMessage() == null
                || notification.getPayload().getMessage().isBlank()) {
            return "New notification";
        }

        return notification.getPayload().getMessage();
    }

    private String getNotificationType(NotifyDTO notification) {
        if (notification == null || notification.getType() == null) {
            return "GENERAL";
        }

        return notification.getType().name();
    }

    private Div buildEmptyState(String message) {
        Div empty = new Div();
        empty.setText(message);
        empty.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("padding", "2rem 0");

        return empty;
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