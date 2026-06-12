package DTO;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TicketSupplyRequestDTO {
    private final List<Integer> tickets;

    private String customerId;
    private String eventId;
    private String zoneName;
    private boolean isSeating;
    private List<PurchasedTicketDTO> purchasedTickets;

    public TicketSupplyRequestDTO(List<Integer> tickets) {
        this.tickets = tickets == null ? new ArrayList<>() : new ArrayList<>(tickets);
        this.purchasedTickets = new ArrayList<>();
    }
    public TicketSupplyRequestDTO(String customerId, String eventId, String zoneName,
                                  boolean isSeating, List<PurchasedTicketDTO> purchasedTickets) {
        this.tickets = purchasedTickets == null ? new ArrayList<>() :
                purchasedTickets.stream().map(PurchasedTicketDTO::getTicketId).collect(Collectors.toList());

        this.customerId = customerId;
        this.eventId = eventId;
        this.zoneName = zoneName;
        this.isSeating = isSeating;
        this.purchasedTickets = purchasedTickets == null ? new ArrayList<>() : new ArrayList<>(purchasedTickets);
    }

    public List<Integer> getTickets() {
        return new ArrayList<>(tickets);
    }
    public String getCustomerId() { return customerId; }
    public String getEventId() { return eventId; }
    public String getZoneName() { return zoneName; }
    public boolean isSeating() { return isSeating; }
    public List<PurchasedTicketDTO> getPurchasedTickets() {
        return new ArrayList<>(purchasedTickets);
    }

}