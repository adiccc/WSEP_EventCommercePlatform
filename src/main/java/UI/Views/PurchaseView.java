package UI.Views;

import DTO.ElementPositionDTO;
import DTO.EnterPurchaseDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.activeOrder.STAGE;
import UI.Presenters.PurchasePresenter;
import application.ActiveOrderService;
import application.Response;
import com.vaadin.flow.component.Component;
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

    private static final int OFFSET_X = 50;
    private static final int OFFSET_Y = 50;

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

        Response<EnterPurchaseDTO> response =
                presenter.enterPurchase(token, companyId, eventId);

        if (response.getValue() == null) {
            add(new Paragraph("Error: " + response.getMessage()));
            return;
        }

        if (response.getValue().isWaitingInQueue()) {
            UI.getCurrent().navigate(
                    "waiting/" + companyId + "/" + eventId + "/" +
                            response.getValue().getQueuePosition()
            );
            return;
        }

        if (response.getValue().isExistingOrder()) {
            ActiveOrderDTO order = response.getValue().getActiveOrder();

            if (order.getStage() == STAGE.CHECKING_OUT) {
                UI.getCurrent().navigate("checkout/" + order.getId());
                return;
            }

            Notification.show("You already started this order. Continue selecting tickets.");
        }

        this.eventMap = response.getValue().getEventMap();

        add(buildHeader());

        Component map = buildMapSection();
        Component summary = buildSummary(token);

        HorizontalLayout layout = new HorizontalLayout(map, summary);
        layout.setSizeFull();
        layout.setSpacing(true);

        layout.setFlexGrow(1, map);
        layout.setFlexGrow(0, summary);

        add(layout);
        expand(layout);
    }

    private HorizontalLayout buildHeader() {
        Button back = new Button("← Back",
                e -> UI.getCurrent().navigate("event/" + companyId + "/" + eventId));

        H2 title = new H2("Select Tickets");

        HorizontalLayout header = new HorizontalLayout(back, title);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.expand(title);

        return header;
    }

    private Component buildMapSection() {
        Div wrapper = new Div();
        wrapper.setSizeFull();

        wrapper.getStyle()
                .set("position", "relative")
                .set("overflow", "auto")
                .set("border", "1px solid #ccc")
                .set("border-radius", "12px")
                .set("background", "#f7f7f7")
                .set("height", "700px");

        Div map = new Div();

        map.getStyle()
                .set("position", "relative")
                .set("width", "1600px")
                .set("height", "1200px")
                .set("transform", "scale(0.75)")
                .set("transform-origin", "top left");

        addStage(map);
        addEntries(map);
        addSeatingZones(map);
        addStandingZones(map);

        wrapper.add(map);
        return wrapper;
    }

    private void addStage(Div map) {
        if (eventMap.getStage() == null) {
            return;
        }

        Div stage = new Div();
        stage.setText("STAGE");

        stage.getStyle()
                .set("position", "absolute")
                .set("left", (eventMap.getStage().getX() + OFFSET_X) + "px")
                .set("top", (eventMap.getStage().getY() + OFFSET_Y) + "px")
                .set("z-index", "10")
                .set("background", "black")
                .set("color", "white")
                .set("padding", "14px 28px")
                .set("border-radius", "8px")
                .set("font-weight", "bold");

        map.add(stage);
    }

    private void addEntries(Div map) {
        if (eventMap.getEntries() == null) {
            return;
        }

        for (ElementPositionDTO entry : eventMap.getEntries()) {
            Div entryDiv = new Div();
            entryDiv.setText("ENTRY");

            entryDiv.getStyle()
                    .set("position", "absolute")
                    .set("left", (entry.getX() + OFFSET_X) + "px")
                    .set("top", (entry.getY() + OFFSET_Y) + "px")
                    .set("z-index", "5")
                    .set("background", "green")
                    .set("color", "white")
                    .set("padding", "8px 14px")
                    .set("border-radius", "8px")
                    .set("font-weight", "bold");

            map.add(entryDiv);
        }
    }

    private void addSeatingZones(Div map) {
        if (eventMap.getSeatingZones() == null) {
            return;
        }

        for (SeatingZoneDTO zone : eventMap.getSeatingZones()) {
            Div zoneDiv = new Div();

            zoneDiv.getStyle()
                    .set("position", "absolute")
                    .set("left", (zone.getPosition().getX() + OFFSET_X) + "px")
                    .set("top", (zone.getPosition().getY() + OFFSET_Y) + "px")
                    .set("border", "1px solid #999")
                    .set("padding", "8px")
                    .set("background", "white")
                    .set("z-index", "2")
                    .set("border-radius", "10px")
                    .set("box-shadow", "0 2px 6px rgba(0,0,0,0.12)");

            H4 title = new H4(zone.getName() + " - Price: " + zone.getPrice());
            title.getStyle()
                    .set("margin", "0 0 8px 0")
                    .set("font-size", "14px");

            Div grid = new Div();
            grid.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "repeat(" + zone.getCols() + ", 32px)")
                    .set("gap", "4px");

            for (int r = 1; r <= zone.getRows(); r++) {
                for (int c = 1; c <= zone.getCols(); c++) {
                    Button seat = new Button(r + "-" + c);

                    seat.setWidth("28px");
                    seat.setHeight("28px");

                    seat.getStyle()
                            .set("font-size", "10px")
                            .set("padding", "0")
                            .set("min-width", "28px")
                            .set("background", "#4CAF50")
                            .set("color", "white");

                    String zoneName = zone.getName();
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

            zoneDiv.add(title, grid);
            map.add(zoneDiv);
        }
    }

    private void addStandingZones(Div map) {
        if (eventMap.getStandingZones() == null) {
            return;
        }

        for (StandingZoneDTO zone : eventMap.getStandingZones()) {
            Div zoneDiv = new Div();

            zoneDiv.getStyle()
                    .set("position", "absolute")
                    .set("left", (zone.getPosition().getX() + OFFSET_X) + "px")
                    .set("top", (zone.getPosition().getY() + OFFSET_Y) + "px")
                    .set("width", "170px")
                    .set("border", "1px solid #777")
                    .set("padding", "10px")
                    .set("background", "#fff8dc")
                    .set("z-index", "2")
                    .set("border-radius", "10px")
                    .set("box-shadow", "0 2px 6px rgba(0,0,0,0.12)");

            H4 title = new H4(zone.getName());
            title.getStyle()
                    .set("margin", "0 0 6px 0")
                    .set("font-size", "14px");

            Span capacity = new Span("Price: " + zone.getPrice());
            capacity.getStyle()
                    .set("display", "block")
                    .set("font-size", "12px")
                    .set("margin-bottom", "8px");

            IntegerField amount = new IntegerField("Tickets");
            amount.setMin(0);
            amount.setMax(zone.getCapacty());
            amount.setValue(0);
            amount.setStepButtonsVisible(true);
            amount.setWidthFull();

            amount.addValueChangeListener(e -> {
                Integer value = e.getValue();

                if (value == null || value <= 0) {
                    selectedStanding.remove(zone.getName());
                } else {
                    selectedStanding.put(zone.getName(), value);
                }

                refreshSummary();
            });

            zoneDiv.add(title, capacity, amount);
            map.add(zoneDiv);
        }
    }

    private VerticalLayout buildSummary(String token) {
        VerticalLayout box = new VerticalLayout();
        box.setWidth("350px");
        box.setMinWidth("350px");

        box.getStyle()
                .set("border", "1px solid #ddd")
                .set("border-radius", "12px")
                .set("padding", "1rem")
                .set("z-index", "20")
                .set("background", "white");

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

    private void refreshSummary() {
        selectedSummary.removeAll();

        int total = 0;

        for (var entry : selectedSeats.entrySet()) {
            for (SeatingTicketDTO seat : entry.getValue()) {
                total++;

                selectedSummary.add(new Span(
                        entry.getKey() + " seat " + seat.getRow() + "-" + seat.getCol()
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