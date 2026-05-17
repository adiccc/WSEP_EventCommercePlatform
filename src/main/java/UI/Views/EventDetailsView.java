package UI.Views;

import UI.Presenters.EventDetailsPresenter;
import application.EventService;
import application.Response;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import domain.dto.EventDetailsDTO;

@Route(value = "event/:companyId/:eventId", layout = MainLayout.class)
@PageTitle("Event Details")
public class EventDetailsView extends VerticalLayout
        implements BeforeEnterObserver {

    private final EventDetailsPresenter presenter;

    public EventDetailsView(EventService eventService) {
        this.presenter = new EventDetailsPresenter(eventService);
        setSpacing(true);
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        int companyId = Integer.parseInt(
                event.getRouteParameters()
                        .get("companyId")
                        .orElseThrow()
        );

        int eventId = Integer.parseInt(
                event.getRouteParameters()
                        .get("eventId")
                        .orElseThrow()
        );

        loadEvent(companyId, eventId);
    }

    private void loadEvent(int companyId, int eventId) {
        String token = (String) VaadinSession.getCurrent()
                .getAttribute("token");

        Response<EventDetailsDTO> response =
                presenter.getDetails(token, companyId, eventId);

        removeAll();

        if (response.getValue() == null) {
            showError(response.getMessage());
            return;
        }

        EventDetailsDTO dto = response.getValue();

        add(
                new H2(dto.getName()),
                new Paragraph("Category: " + dto.getCategoryEvent()),
                new Paragraph("Location: " + dto.getLocation())
        );
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