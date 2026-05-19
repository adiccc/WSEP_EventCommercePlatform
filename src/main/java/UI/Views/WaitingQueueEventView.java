package UI.Views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "waiting/:companyId/:eventId/:position", layout = MainLayout.class)
@PageTitle("Waiting Queue")
@AnonymousAllowed
public class WaitingQueueEventView extends VerticalLayout implements BeforeEnterObserver {

    private int companyId;
    private int eventId;
    private int position;

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
    }

    private void build() {
        removeAll();

        H1 title = new H1("You are in the waiting queue");

        Paragraph message = new Paragraph(
                "The event is currently full. Your position in line is:"
        );

        H2 positionText = new H2("#" + position);

        Button refresh = new Button("Try Again", e ->
                UI.getCurrent().navigate("purchase/" + companyId + "/" + eventId)
        );

        Button back = new Button("Back to Event", e ->
                UI.getCurrent().navigate("event/" + companyId + "/" + eventId)
        );

        add(title, message, positionText, refresh, back);
    }
}