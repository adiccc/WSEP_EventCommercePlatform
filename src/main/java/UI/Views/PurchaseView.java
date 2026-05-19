package UI.Views;

import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import UI.Presenters.PurchasePresenter;
import application.ActiveOrderService;
import application.Response;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.*;

import java.util.*;

@Route(value = "purchase/:companyId/:eventId", layout = MainLayout.class)
@PageTitle("Purchase Tickets")
@AnonymousAllowed
public class PurchaseView extends VerticalLayout implements BeforeEnterObserver {

    private final PurchasePresenter presenter;

    private int companyId;
    private int eventId;

    private EventMapDTO eventMap;

    private final Map<String, List<SeatingTicketDTO>> selectedSeats = new HashMap<>();
    private final Map<String, Integer> selectedStanding = new HashMap<>();

    private final VerticalLayout selectedSummary = new VerticalLayout();
    private final Span totalLabel = new Span("0 tickets selected");

    public PurchaseView(ActiveOrderService service) {
        this.presenter = new PurchasePresenter(service);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = Integer.parseInt(event.getRouteParameters().get("companyId").orElse("0"));
        eventId = Integer.parseInt(event.getRouteParameters().get("eventId").orElse("0"));
        load();
    }

    private void load() {

        removeAll();

        String token = (String) VaadinSession.getCurrent().getAttribute("token");

        Response<EventMapDTO> response =
                presenter.enterPurchase(token, companyId, eventId);

        if (response.getValue() == null) {
            add(new Paragraph("Error: " + response.getMessage()));
            return;
        }

        this.eventMap = response.getValue();

        add(buildHeader());

        HorizontalLayout layout = new HorizontalLayout();
        layout.setSizeFull();

        layout.add(buildMapSection(), buildSummary(token));

        layout.setFlexGrow(3, layout.getComponentAt(0));
        layout.setFlexGrow(1, layout.getComponentAt(1));

        add(layout);
    }

    private HorizontalLayout buildHeader() {

        Button back = new Button("← Back",
                e -> UI.getCurrent().navigate("event/" + companyId + "/" + eventId));

        H2 title = new H2("Select Tickets");

        HorizontalLayout header = new HorizontalLayout(back, title);
        header.setWidthFull();
        header.expand(title);

        return header;
    }

    // =========================================================
    // MAP
    // =========================================================

    private VerticalLayout buildMapSection() {

        VerticalLayout wrapper = new VerticalLayout();
        wrapper.setWidthFull();

        wrapper.add(new H3("Seating Map"));

        for (SeatingZoneDTO zone : eventMap.getSeatingZones()) {

            String zoneName = zone.getName();

            wrapper.add(new H4("Zone: " + zoneName));

            FlexLayout grid = new FlexLayout();
            grid.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "repeat(" + zone.getCols() + ", 45px)")
                    .set("gap", "6px");

            for (int r = 1; r <= zone.getRows(); r++) {
                for (int c = 1; c <= zone.getCols(); c++) {

                    Button seat = new Button(r + "-" + c);

                    seat.setWidth("45px");
                    seat.setHeight("45px");

                    String key = zoneName + ":" + r + "-" + c;

                    seat.getStyle()
                            .set("background", "#4CAF50")
                            .set("color", "white");

                    int finalR = r;
                    int finalC = c;
                    seat.addClickListener(e -> {

                        List<SeatingTicketDTO> list =
                                selectedSeats.computeIfAbsent(zoneName, z -> new ArrayList<>());

                        Optional<SeatingTicketDTO> existing =
                                list.stream()
                                        .filter(s -> s.getRow() == finalR && s.getCol() == finalC)
                                        .findFirst();

                        if (existing.isPresent()) {
                            list.remove(existing.get());
                            seat.getStyle().set("background", "#4CAF50");
                        } else {
                            list.add(new SeatingTicketDTO(finalR, finalC));
                            seat.getStyle().set("background", "#2196F3");
                        }

                        refreshSummary();
                    });

                    grid.add(seat);
                }
            }

            wrapper.add(grid);
        }

        // ── Standing ─────────────────────────────

        wrapper.add(new Hr());
        wrapper.add(new H3("Standing"));

        for (StandingZoneDTO zone : eventMap.getStandingZones()) {

            String name = zone.getName();

            IntegerField field = new IntegerField(name + " tickets");
            field.setMin(0);
            field.setMax(zone.getCapacty());
            field.setValue(0);

            field.addValueChangeListener(e -> {
                selectedStanding.put(name, e.getValue() == null ? 0 : e.getValue());
                refreshSummary();
            });

            wrapper.add(field);
        }

        return wrapper;
    }

    // =========================================================
    // SUMMARY
    // =========================================================

    private VerticalLayout buildSummary(String token) {

        VerticalLayout box = new VerticalLayout();
        box.setWidth("350px");

        box.getStyle()
                .set("border", "1px solid #ddd")
                .set("border-radius", "12px")
                .set("padding", "1rem");

        Button continueBtn = new Button("Continue to Checkout");

        continueBtn.addClickListener(e -> {

            Response<Integer> res =
                    presenter.selectTickets(
                            token,
                            eventId,
                            selectedSeats,
                            selectedStanding
                    );

            if (res.getValue() == null) {
                Notification.show(res.getMessage());
                return;
            }

            UI.getCurrent().navigate("checkout/" + res.getValue());
        });

        box.add(new H3("Summary"), totalLabel, selectedSummary, continueBtn);

        return box;
    }

    // =========================================================
    // REFRESH
    // =========================================================

    private void refreshSummary() {

        selectedSummary.removeAll();

        int total = 0;

        for (var entry : selectedSeats.entrySet()) {
            for (SeatingTicketDTO s : entry.getValue()) {
                total++;
                selectedSummary.add(new Span(
                        entry.getKey() + " seat " + s.getRow() + "-" + s.getCol()
                ));
            }
        }

        for (var entry : selectedStanding.entrySet()) {
            total += entry.getValue();
            selectedSummary.add(new Span(
                    entry.getKey() + " standing x" + entry.getValue()
            ));
        }

        totalLabel.setText(total + " tickets selected");
    }
}