package UI.Views;

import DTO.NotifyDTO;
import DTO.NotifyType;
import DTO.QueueEntryResultDTO;
import UI.Presenters.WebQueuePresenter;
import application.Response;
import application.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.shared.Registration;
import infrastructure.Broadcaster;
import com.vaadin.flow.component.ClientCallable;

@Route("")
@PageTitle("Waiting Room")
@AnonymousAllowed
public class WebQueueView extends VerticalLayout {

    private final WebQueuePresenter presenter;
    private boolean initialized = false;
    private Registration broadcasterRegistration;

    private final H1 title;
    private final Paragraph message;
    private final Span positionText;
    private final Button refreshButton;

    public WebQueueView(UserService userService) {
        this.presenter = new WebQueuePresenter(userService);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        title = new H1("Welcome to EventCommerce");

        message = new Paragraph("Checking your place in line...");
        message.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1rem");

        positionText = new Span();
        positionText.getStyle()
                .set("font-size", "1.3rem")
                .set("font-weight", "600")
                .set("color", "var(--lumo-primary-text-color)");

        refreshButton = new Button("Check my status", event -> checkCurrentStatus());
        refreshButton.setVisible(false);
        add(title, message, positionText, refreshButton);

        addDetachListener(event -> unregisterFromBroadcaster());
        addAttachListener(event -> initializeBrowserTab());
    }

    private void initializeBrowserTab() {
        getElement().executeJs(
                """
                const TAB_ID_KEY = "eventCommerceTabId";
                const QUEUE_TOKEN_KEY = "eventCommerceWebQueueToken";

                // Always generate a fresh tabId so duplicate tabs don't share session state
                let tabId;
                if (window.crypto && window.crypto.randomUUID) {
                    tabId = window.crypto.randomUUID();
                } else {
                    tabId = Date.now() + "-" + Math.random().toString(36).substring(2);
                }
                sessionStorage.setItem(TAB_ID_KEY, tabId);

                // Pass any saved queue token for resumption after a page refresh
                const savedQueueToken = sessionStorage.getItem(QUEUE_TOKEN_KEY) || "";

                this.$server.onBrowserTabReady(tabId, savedQueueToken);
                """
        );
    }

    @ClientCallable
    public void onBrowserTabReady(String tabId, String savedQueueToken) {
        if (initialized) {
            return;
        }

        initialized = true;
        UI.getCurrent().getElement().setProperty("currentTabId", tabId);

        initializeQueueState(savedQueueToken);
    }

    private void initializeQueueState(String savedQueueToken) {
        if (savedQueueToken != null && !savedQueueToken.isBlank()) {
            checkCurrentStatusAndRegister(savedQueueToken);
            return;
        }

        enterQueue();
    }

    private void checkCurrentStatusAndRegister(String webQueueToken) {
        Response<QueueEntryResultDTO> response =
                presenter.getQueueStatus(webQueueToken);

        QueueEntryResultDTO result =
                response != null ? response.getValue() : null;

        if (result == null) {
            showError(response != null ? response.getMessage() : "Failed to check queue status.");
            showQueueErrorState();
            return;
        }

        if (result.isAdmitted()) {
            admitUserAndNavigateToLogin(webQueueToken);
            return;
        }

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        VaadinSession.getCurrent().setAttribute("webQueueToken_" + tabId, webQueueToken);
        VaadinSession.getCurrent().setAttribute("notificationUserIdentifier_" + tabId, webQueueToken);
        VaadinSession.getCurrent().setAttribute("webQueueAdmitted_" + tabId, false);

        // Keep sessionStorage in sync so refresh can resume
        getElement().executeJs("sessionStorage.setItem('eventCommerceWebQueueToken', $0)", webQueueToken);

        showWaitingState(result.getPosition());
        registerToBroadcaster(webQueueToken);
    }

    private void enterQueue() {
        Response<QueueEntryResultDTO> response = presenter.enterQueue();

        if (response == null || response.getValue() == null) {
            showError(response != null ? response.getMessage() : "Failed to enter queue.");
            showQueueErrorState();
            return;
        }

        QueueEntryResultDTO result = response.getValue();
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = result.getToken();
        VaadinSession.getCurrent().setAttribute("webQueueToken_" + tabId, token);
        VaadinSession.getCurrent().setAttribute("notificationUserIdentifier_" + tabId, token);

        // Persist token so a page refresh can resume queue position
        getElement().executeJs("sessionStorage.setItem('eventCommerceWebQueueToken', $0)", token);

        if (result.isAdmitted()) {
            admitUserAndNavigateToLogin(token);
            return;
        }

        VaadinSession.getCurrent().setAttribute("webQueueAdmitted_" + tabId, false);
        showWaitingState(result.getPosition());
        registerToBroadcaster(token);
    }

    private void registerToBroadcaster(String queueToken) {
        unregisterFromBroadcaster();

        UI ui = UI.getCurrent();

        broadcasterRegistration = Broadcaster.registerTab(queueToken, notification -> {
            if (ui != null) {
                ui.access(() -> handleNotification(notification));
            }
        });
    }

    private void handleNotification(NotifyDTO notification) {
        if (notification == null || notification.getType() == null) {
            return;
        }

        if (notification.getType() == NotifyType.QUEUE_WEB_TURN_ARRIVED) {
            handleWebQueueTurnArrived(notification);
        }
    }

    private void handleWebQueueTurnArrived(NotifyDTO notification) {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String webQueueToken = (String) VaadinSession.getCurrent().getAttribute("webQueueToken_" + tabId);

        if (webQueueToken == null || webQueueToken.isBlank()) {
            showError("Queue session is missing. Please refresh and enter the queue again.");
            showQueueErrorState();
            return;
        }

        Response<QueueEntryResultDTO> statusResponse =
                presenter.getQueueStatus(webQueueToken);

        QueueEntryResultDTO queueStatus =
                statusResponse != null ? statusResponse.getValue() : null;

        if (queueStatus == null || !queueStatus.isAdmitted()) {
            showError("You are not admitted yet. Please wait for your turn.");
            checkCurrentStatus();
            return;
        }

        admitUserAndNavigateToLogin(webQueueToken);
    }

    private void checkCurrentStatus() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String webQueueToken = (String) VaadinSession.getCurrent().getAttribute("webQueueToken_" + tabId);

        if (webQueueToken == null || webQueueToken.isBlank()) {
            showError("Queue session is missing. Entering the queue again.");
            enterQueue();
            return;
        }

        Response<QueueEntryResultDTO> response =
                presenter.getQueueStatus(webQueueToken);

        QueueEntryResultDTO result =
                response != null ? response.getValue() : null;

        if (result == null) {
            showError(response != null ? response.getMessage() : "Failed to check queue status.");
            return;
        }

        if (result.isAdmitted()) {
            admitUserAndNavigateToLogin(webQueueToken);
        } else {
            showWaitingState(result.getPosition());
        }
    }

    private void admitUserAndNavigateToLogin(String webQueueToken) {
        unregisterFromBroadcaster();

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        VaadinSession.getCurrent().setAttribute("webQueueToken_" + tabId, webQueueToken);
        VaadinSession.getCurrent().setAttribute("webQueueAdmitted_" + tabId, true);
        VaadinSession.getCurrent().setAttribute("notificationUserIdentifier_" + tabId, null);

        // Clear queue token from sessionStorage so a later duplicate/new tab starts fresh
        getElement().executeJs("sessionStorage.removeItem('eventCommerceWebQueueToken')");

        showSuccess("Your turn has arrived. Please sign in or continue as guest.");

        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.navigate("login");
        }
    }

    private void unregisterFromBroadcaster() {
        if (broadcasterRegistration != null) {
            broadcasterRegistration.remove();
            broadcasterRegistration = null;
        }
    }

    private void showWaitingState(int position) {
        title.setText("You are in line");
        message.setText("Please wait. We will move you forward automatically when it is your turn.");

        if (position > 0) {
            positionText.setText("Your position in line: #" + position);
        } else {
            positionText.setText("Waiting...");
        }

        refreshButton.setText("Refresh position");
        refreshButton.setVisible(true);
    }

    private void showQueueErrorState() {
        title.setText("Queue unavailable");
        message.setText("Could not identify your queue session.");
        positionText.setText("");
        refreshButton.setText("Try again");
        refreshButton.setVisible(true);
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