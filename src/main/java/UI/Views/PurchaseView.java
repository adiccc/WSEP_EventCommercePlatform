package UI.Views;
import domain.dto.ActiveOrderSeatDTO;
import java.util.Optional;
import DTO.ElementPositionDTO;
import DTO.EnterPurchaseDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import com.vaadin.flow.shared.Registration;
import domain.activeOrder.STAGE;
import UI.Presenters.PurchasePresenter;
import application.ActiveOrderService;
import com.vaadin.flow.component.UI;
import application.Response;
import com.vaadin.flow.component.Component;
import domain.dataType.TicketStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.*;
import domain.dto.ActiveOrderSelectionDTO;

import java.util.*;

@Route(value = "purchase/:companyId/:eventId", layout = MainLayout.class)
@PageTitle("Purchase Tickets")
@AnonymousAllowed
public class PurchaseView extends VerticalLayout implements BeforeEnterObserver {

    private final PurchasePresenter presenter;
    private Integer activeOrderId;
    private final Span checkoutTimer = new Span();
    private Registration timerRegistration;
    private static final int OFFSET_X = 50;
    private static final int OFFSET_Y = 50;
    private String lotteryCode;
    private int companyId;
    private int eventId;
    private boolean editingMode;
    private final List<ActiveOrderSeatDTO> currentOrderSeats = new ArrayList<>();

    private final Map<String, List<SeatingTicketDTO>> seatingToRemove = new HashMap<>();
    private final Map<String, List<SeatingTicketDTO>> seatingToAdd = new HashMap<>();
    private final Map<String, Integer> currentStandingByZone = new HashMap<>();
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

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String lotteryCodeKey = "lotteryCode_" + companyId + "_" + eventId + "_" + tabId;

        lotteryCode = event.getLocation()
                .getQueryParameters()
                .getParameters()
                .getOrDefault("lotteryCode", List.of())
                .stream()
                .findFirst()
                .orElse(null);

        if (lotteryCode != null && !lotteryCode.isBlank()) {
            lotteryCode = lotteryCode.trim();

            VaadinSession.getCurrent().setAttribute(
                    lotteryCodeKey,
                    lotteryCode
            );
        } else {
            lotteryCode = (String) VaadinSession.getCurrent()
                    .getAttribute(lotteryCodeKey);
        }

        load(event);
    }

    private void load(BeforeEnterEvent event) {
        removeAll();

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);
        Response<EnterPurchaseDTO> response = presenter.enterPurchase(token, companyId, eventId, lotteryCode);

        if (response.getValue() == null) {
            Notification.show(response.getMessage());

            Button back = new Button("Back to Event", e ->
                    UI.getCurrent().navigate("event/" + companyId + "/" + eventId)
            );

            add(
                    new Paragraph("Error: " + response.getMessage()),
                    back
            );

            return;
        }

        if (response.getValue().isWaitingInQueue()) {
            VaadinSession.getCurrent().setAttribute("eventQueueTabId_" + tabId, token);
            VaadinSession.getCurrent().setAttribute("eventQueueCompanyId_" + tabId, companyId);
            VaadinSession.getCurrent().setAttribute("eventQueueEventId_" + tabId, eventId);

            event.rerouteTo(
                    "waiting/" + companyId + "/" + eventId + "/" + response.getValue().getQueuePosition()
            );
            return;
        }

        if (response.getValue().isExistingOrder()) {

            ActiveOrderDTO order = response.getValue().getActiveOrder();
            activeOrderId = order.getId();
            if (order.getStage() == STAGE.EDITING) {
                editingMode = true;
                Response<ActiveOrderSelectionDTO> selectionResponse =
                        presenter.getCurrentActiveOrderSelection(token);

                currentOrderSeats.clear();
                currentStandingByZone.clear();

                if (selectionResponse.getValue() != null) {
                    currentOrderSeats.addAll(selectionResponse.getValue().getSeats());
                    currentStandingByZone.putAll(selectionResponse.getValue().getStandingTicketsByZone());
                }

                Notification.show(
                        "You are editing your ticket selection. Changes are applied only after clicking Continue to Checkout."
                );
            } else {
                editingMode = false;
                Notification.show("You already started this order. Continue selecting tickets.");

            }
            if (order.getStage() == STAGE.CHECKING_OUT) {
                event.rerouteTo("checkout/" + order.getId());
                return;
            }

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

            for (int r = 0; r < zone.getRows(); r++) {
                for (int c = 0; c < zone.getCols(); c++) {
                    Button seat = new Button((r + 1) + "-" + (c + 1));

                    seat.setWidth("28px");
                    seat.setHeight("28px");

                    seat.getStyle()
                            .set("font-size", "10px")
                            .set("padding", "0")
                            .set("min-width", "28px");

                    String zoneName = zone.getName();
                    int finalR = r;
                    int finalC = c;

                    TicketStatus status = zone.getTicketStatus(finalR, finalC);

                    if (status == TicketStatus.SOLD) {
                        seat.getStyle()
                                .set("background", "#9E9E9E")
                                .set("color", "white");

                        seat.setEnabled(false);
                        seat.getElement().setProperty("title", "Sold");
                        grid.add(seat);
                        continue;
                    }

                    boolean isMySeat =
                            editingMode
                                    && isMyCurrentSeat(zoneName, finalR, finalC);

                    if (status == TicketStatus.LOCKED && !isMySeat) {
                        seat.getStyle()
                                .set("background", "#BDBDBD")
                                .set("color", "white");

                        seat.setEnabled(false);
                        seat.getElement().setProperty(
                                "title",
                                "Already selected by another user"
                        );

                        grid.add(seat);
                        continue;
                    }

                    if (isMySeat) {

                        seat.getStyle()
                                .set("background", "#7c3aed")
                                .set("color", "white");

                        seat.getElement().setProperty(
                                "title",
                                "Current ticket"
                        );

                        seat.addClickListener(e -> {

                            toggleSeat(
                                    seatingToRemove,
                                    zoneName,
                                    finalR,
                                    finalC
                            );

                            if (containsSeat(
                                    seatingToRemove,
                                    zoneName,
                                    finalR,
                                    finalC
                            )) {

                                seat.getStyle()
                                        .set("background", "#ef4444");

                                seat.getElement().setProperty(
                                        "title",
                                        "Will be removed"
                                );

                            } else {

                                seat.getStyle()
                                        .set("background", "#7c3aed");

                                seat.getElement().setProperty(
                                        "title",
                                        "Current ticket"
                                );
                            }

                            refreshSummary();
                        });

                        grid.add(seat);
                        continue;
                    }

                    seat.getStyle()
                            .set("background", "#4CAF50")
                            .set("color", "white");

                    seat.addClickListener(e -> {

                        if (editingMode) {

                            toggleSeat(
                                    seatingToAdd,
                                    zoneName,
                                    finalR,
                                    finalC
                            );

                            if (containsSeat(
                                    seatingToAdd,
                                    zoneName,
                                    finalR,
                                    finalC
                            )) {

                                seat.getStyle()
                                        .set("background", "#2196F3");

                            } else {

                                seat.getStyle()
                                        .set("background", "#4CAF50");
                            }

                            refreshSummary();
                            return;
                        }

                        List<SeatingTicketDTO> list =
                                selectedSeats.computeIfAbsent(
                                        zoneName,
                                        z -> new ArrayList<>()
                                );

                        Optional<SeatingTicketDTO> existing =
                                list.stream()
                                        .filter(s ->
                                                s.getRow() == finalR
                                                        && s.getCol() == finalC)
                                        .findFirst();

                        if (existing.isPresent()) {

                            list.remove(existing.get());

                            seat.getStyle()
                                    .set("background", "#4CAF50");

                        } else {

                            list.add(new SeatingTicketDTO(finalR, finalC));

                            seat.getStyle()
                                    .set("background", "#2196F3");
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

            Span capacity = new Span(
                    "Price: " + zone.getPrice()
                            + " | Available: " + zone.getAvailable()
                            + "/" + zone.getCapacty()
            );

            capacity.getStyle()
                    .set("display", "block")
                    .set("font-size", "12px")
                    .set("margin-bottom", "8px");

            IntegerField amount = new IntegerField("Tickets");
            amount.setMin(0);
            int currentAmount = editingMode
                    ? currentStandingByZone.getOrDefault(zone.getName(), 0)
                    : 0;

            amount.setMax(zone.getAvailable() + currentAmount);
            amount.setValue(currentAmount);

            if (editingMode && currentAmount > 0) {
                selectedStanding.put(zone.getName(), currentAmount);
            }
            amount.setStepButtonsVisible(true);
            amount.setWidthFull();

            if (!editingMode && zone.getAvailable() == 0) {
                amount.setEnabled(false);
                amount.setValue(0);
            }

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
            if (!editingMode && getSelectedTicketsCount() == 0) {
                Notification.show("Please select at least one ticket before continuing to checkout");
                return;
            }

            if (editingMode) {
                if (getFinalEditingTicketCount() == 0) {
                    Notification.show("Please keep at least one ticket in the order.");
                    return;
                }
                Response<ActiveOrderDTO> res =
                        presenter.editTicketSelection(
                                token,
                                seatingToRemove,
                                seatingToAdd,
                                selectedStanding
                        );

                if (res.getValue() == null) {
                    Notification.show(res.getMessage());
                    return;
                }

                UI.getCurrent().navigate("checkout/" + res.getValue().getId());
                return;
            }

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

        if (editingMode && activeOrderId != null) {
            checkoutTimer.getStyle()
                    .set("background", "#fff7ed")
                    .set("border", "1px solid #fed7aa")
                    .set("border-radius", "12px")
                    .set("padding", "0.75rem")
                    .set("font-weight", "700")
                    .set("color", "#9a3412")
                    .set("width", "calc(100% - 1.5rem)")
                    .set("box-sizing", "border-box");
            box.add(new H3("Summary"), checkoutTimer, totalLabel, selectedSummary, continueBtn);
            startCheckoutTimer(token);
            return box;
        }

        box.add(new H3("Summary"), totalLabel, selectedSummary, continueBtn);

        return box;
    }

    private void startCheckoutTimer(String token) {
        stopCheckoutTimer();

        UI ui = UI.getCurrent();
        ui.setPollInterval(1000);

        refreshCheckoutTimer(token);

        timerRegistration = ui.addPollListener(e -> refreshCheckoutTimer(token));
    }

    private void refreshCheckoutTimer(String token) {
        if (activeOrderId == null) {
            return;
        }

        Response<Long> response =
                presenter.getCheckoutRemainingSeconds(token, activeOrderId);

        if (response.getValue() == null) {
            checkoutTimer.setText("Could not load reservation timer");
            return;
        }

        long remainingSeconds = response.getValue();

        if (remainingSeconds <= 0) {
            stopCheckoutTimer();
            Notification.show("Your reserved tickets expired. Please select tickets again.");
            UI.getCurrent().navigate("event/" + companyId + "/" + eventId);
            return;
        }

        checkoutTimer.setText(
                "Tickets reserved for: " + formatRemainingTime(remainingSeconds)
        );
    }

    private String formatRemainingTime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format("%02d:%02d", minutes, seconds);
    }

    private void stopCheckoutTimer() {
        if (timerRegistration != null) {
            timerRegistration.remove();
            timerRegistration = null;
        }
    }

    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent detachEvent) {
        stopCheckoutTimer();
        super.onDetach(detachEvent);
    }

    private int getSelectedTicketsCount() {
        int total = 0;

        for (List<SeatingTicketDTO> seats : selectedSeats.values()) {
            total += seats.size();
        }

        for (Integer amount : selectedStanding.values()) {
            if (amount != null) {
                total += amount;
            }
        }

        return total;
    }

    private void refreshSummary() {
        selectedSummary.removeAll();

        int total = 0;
        if (editingMode) {
            for (var entry : seatingToAdd.entrySet()) {
                for (SeatingTicketDTO seat : entry.getValue()) {
                    total++;
                    selectedSummary.add(new Span(
                            "Add: " + entry.getKey() + " seat " + (seat.getRow() + 1) + "-" + (seat.getCol() + 1)
                    ));
                }
            }

            for (var entry : seatingToRemove.entrySet()) {
                for (SeatingTicketDTO seat : entry.getValue()) {
                    selectedSummary.add(new Span(
                            "Remove: " + entry.getKey() + " seat " + (seat.getRow() + 1) + "-" + (seat.getCol() + 1)
                    ));
                }
            }
            for (var entry : selectedStanding.entrySet()) {
                selectedSummary.add(new Span(
                        "Standing: " + entry.getKey() + " x" + entry.getValue()
                ));
                total += entry.getValue();
            }

            totalLabel.setText(
                    "Final order: " + getFinalEditingTicketCount() + " tickets"
            );
            return;
        }

        for (var entry : selectedSeats.entrySet()) {
            for (SeatingTicketDTO seat : entry.getValue()) {
                total++;

                selectedSummary.add(new Span(
                        entry.getKey() + " seat " + (seat.getRow() + 1) + "-" + (seat.getCol() + 1)
                ));
            }
        }

        for (var entry : selectedStanding.entrySet()) {
            total += entry.getValue();

            selectedSummary.add(new Span(
                    entry.getKey() + " standing x" + entry.getValue()
            ));
        }

        int finalCount = getFinalEditingTicketCount();

        totalLabel.setText(
                "Final order: " + finalCount + " tickets"
        );
    }

    private boolean isMyCurrentSeat(String zoneName, int row, int col) {
        return currentOrderSeats.stream().anyMatch(seat ->
                zoneName.equals(seat.getZoneName())
                        && seat.getRow() == row
                        && seat.getCol() == col
        );
    }

    private boolean containsSeat(
            Map<String, List<SeatingTicketDTO>> map,
            String zoneName,
            int row,
            int col
    ) {
        List<SeatingTicketDTO> seats = map.get(zoneName);

        if (seats == null) {
            return false;
        }

        return seats.stream().anyMatch(seat ->
                seat.getRow() == row
                        && seat.getCol() == col
        );
    }

    private void toggleSeat(
            Map<String, List<SeatingTicketDTO>> map,
            String zoneName,
            int row,
            int col
    ) {
        List<SeatingTicketDTO> seats =
                map.computeIfAbsent(zoneName, z -> new ArrayList<>());

        Optional<SeatingTicketDTO> existing =
                seats.stream()
                        .filter(seat ->
                                seat.getRow() == row
                                        && seat.getCol() == col)
                        .findFirst();

        if (existing.isPresent()) {
            seats.remove(existing.get());
        } else {
            seats.add(new SeatingTicketDTO(row, col));
        }

        if (seats.isEmpty()) {
            map.remove(zoneName);
        }
    }

    private int getFinalEditingTicketCount() {
        int currentSeats = currentOrderSeats.size();
        int removedSeats = 0;

        for (List<SeatingTicketDTO> seats : seatingToRemove.values()) {
            removedSeats += seats.size();
        }

        int addedSeats = 0;

        for (List<SeatingTicketDTO> seats : seatingToAdd.values()) {
            addedSeats += seats.size();
        }

        int standing = 0;

        for (Integer amount : selectedStanding.values()) {
            if (amount != null) {
                standing += amount;
            }
        }

        return currentSeats - removedSeats + addedSeats + standing;
    }
}