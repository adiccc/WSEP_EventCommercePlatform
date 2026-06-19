package UI.Views;

import DTO.NotifyDTO;
import DTO.NotifyType;
import UI.Presenters.NotificationsPresenter;
import application.CompanyService;
import application.Response;
import application.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.component.UI;

import java.util.List;

@Route(value = "notifications", layout = MainLayout.class)
@PageTitle("Notifications")
@AnonymousAllowed
public class NotificationsView extends VerticalLayout {

    private final NotificationsPresenter presenter;
    private final CompanyService companyService;
    private final VerticalLayout notificationsContainer;
    private final Button markAllAsReadButton;

    public NotificationsView(UserService userService, CompanyService companyService) {
        this.presenter = new NotificationsPresenter(userService);
        this.companyService = companyService;

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
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");

        if (tabId == null || tabId.isBlank()) {
            return null;
        }

        return (String) VaadinSession.getCurrent()
                .getAttribute("notificationUserIdentifier_" + tabId);
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

        if (notification != null
                && NotifyType.ROLE_APPOINTMENT_REQUEST.equals(notification.getType())
                && notification.getPayload() != null
                && notification.getPayload().getCompanyId() != null) {

            int companyId = notification.getPayload().getCompanyId();
            // Detect which role this invite is for so we call the right endpoint — otherwise
            // a user who has BOTH pending invites would always trigger the owner path.
            String msg = notification.getPayload().getMessage() != null
                    ? notification.getPayload().getMessage().toLowerCase()
                    : "";
            boolean isManagerInvite = msg.contains("manager");

            Button acceptBtn = new Button("Accept", e -> respondToAppointment(card, companyId, true, isManagerInvite));
            acceptBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            acceptBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);

            Button rejectBtn = new Button("Reject", e -> respondToAppointment(card, companyId, false, isManagerInvite));
            rejectBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            HorizontalLayout actions = new HorizontalLayout(acceptBtn, rejectBtn);
            actions.getStyle().set("margin-top", "0.75rem");
            card.add(actions);
        }

        return card;
    }

    private void respondToAppointment(Div card, int companyId, boolean accept, boolean isManagerInvite) {
        String token = getToken();
        if (token == null || token.isBlank()) {
            showError("You must be logged in to respond to an appointment.");
            return;
        }

        // Call the endpoint matching the invite's role — never both, otherwise a user with both
        // pending invites would have the wrong role assigned (e.g. become owner on manager-accept).
        Response<Boolean> res = isManagerInvite
                ? companyService.respondToManagerAppointment(token, companyId, accept)
                : companyService.respondToOwnerAppointment(token, companyId, accept);
        boolean success = res != null && res.getValue() != null;

        if (!success) {
            String errorMsg = res != null ? res.getMessage() : "Failed to respond to appointment.";
            showError(errorMsg);
            return;
        }

        // The respond endpoint already marks the matching appointment notification as DELIVERED.
        // Do NOT call cleanDelayedNotifications here — that wipes the user's entire notification
        // history, including any other pending invite (e.g. manager) the user still needs to act on.
        loadNotifications();

        if (accept) {
            showSuccess("You have accepted the appointment.");
        } else {
            showSuccess("You have rejected the appointment.");
        }
    }

    private String getToken() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        if (tabId == null || tabId.isBlank()) {
            return null;
        }
        return (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);
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
                isLotteryCodeNotification(message) ? 10000 : 6000,
                Notification.Position.TOP_CENTER
        );

        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private boolean isLotteryCodeNotification(String message) {
        return message != null && message.contains("Your code is:");
    }

    private void showError(String message) {
        Notification notification = Notification.show(
                message,
                6000,
                Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}