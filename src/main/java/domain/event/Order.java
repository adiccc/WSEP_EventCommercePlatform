package domain.event;

import DTO.PurchaseHistoryDTO;
import DTO.PurchasedTicketDTO;

import java.util.ArrayList;
import java.util.List;

public class Order {
    private OrderStatus status;
    private int orderId;
    private String userIdentifier; //for member will be an email, and for guest will be the generated string when purchased
    private Integer eventId;
    private String eventName;
    private String eventDate;
    private String eventLocation;
    private List<PurchasedTicketDTO> purchasedTickets;
    private List<Integer> tickets;
    private double totalSum;
    private String paymentConfirmationId;
    private List<String> externalTicketCodes = new ArrayList<>();


    public Order(int orderId,
                 String userIdentifier,
                 Integer eventId,
                 String eventName,
                 String eventDate,
                 String eventLocation,
                 List<PurchasedTicketDTO> purchasedTickets,
                 List<Integer> tickets, double totalSum,
                 String paymentConfirmationId, List<String> externalTicketCodes) {

        this.orderId = orderId;
        this.userIdentifier = userIdentifier;
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.eventLocation = eventLocation;
        this.purchasedTickets = purchasedTickets == null
                ? new ArrayList<>()
                : new ArrayList<>(purchasedTickets);
        this.tickets = new ArrayList<>(tickets);
        this.status = OrderStatus.APPROVED;
        this.totalSum = totalSum;
        this.paymentConfirmationId = paymentConfirmationId;
        this.externalTicketCodes = externalTicketCodes;
    }

    public Order(Order order) {
        this.orderId = order.orderId;
        this.userIdentifier = order.userIdentifier;
        this.eventId = order.eventId;
        this.eventName = order.eventName;
        this.eventDate = order.eventDate;
        this.eventLocation = order.eventLocation;
        this.purchasedTickets = order.purchasedTickets == null
                ? new ArrayList<>()
                : new ArrayList<>(order.purchasedTickets);
        this.tickets=new ArrayList<>(order.tickets);
        this.status = order.status;
        this.totalSum = order.totalSum;
        this.paymentConfirmationId = order.paymentConfirmationId;
        if (order.externalTicketCodes != null) {
            this.externalTicketCodes = new ArrayList<>(order.externalTicketCodes);
        } else {
            this.externalTicketCodes = new ArrayList<>();
        }    }

    public int getOrderId() {
        return orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public boolean canBeRefunded() {
        return status == OrderStatus.REFUND_REQUIRED;
    }

    public void markRefunded() {
        this.status = OrderStatus.REFUNDED;
    }

    public void markRefundRequired() {
        this.status = OrderStatus.REFUND_REQUIRED;
    }

    public double getTotalSum() {
        return totalSum;
    }

    public String getPaymentConfirmationId() {
        return paymentConfirmationId;
    }

    public int getNumOfTickets() {
        return tickets.size();
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public Integer getEventId() {
        return eventId;
    }

    public List<Integer> getTickets() {
        return new ArrayList<>(tickets);
    }

    public String getEventName() {
        return eventName;
    }

    public String getEventDate() {
        return eventDate;
    }

    public String getEventLocation() {
        return eventLocation;
    }

    public List<PurchasedTicketDTO> getPurchasedTickets() {
        return new ArrayList<>(purchasedTickets);
    }

    public PurchaseHistoryDTO toPurchaseHistoryDTO() {
        List<PurchasedTicketDTO> enrichedTickets = new ArrayList<>();
        for(int i=0; i<purchasedTickets.size(); i++) {
            PurchasedTicketDTO ticketDTO = purchasedTickets.get(i);
            String barcode = "-";
            if (externalTicketCodes != null && !externalTicketCodes.isEmpty() && i < externalTicketCodes.size()) {
                barcode = externalTicketCodes.get(i);
            }
            enrichedTickets.add(new PurchasedTicketDTO(ticketDTO.getTicketId(), ticketDTO.getZoneName(), ticketDTO.getTicketType(),
                    ticketDTO.getRow(), ticketDTO.getCol(), ticketDTO.getPriceAtPurchase(), barcode));
        }
        return new PurchaseHistoryDTO(
                orderId,
                eventName,
                eventDate,
                eventLocation,
                status,
                enrichedTickets,
                totalSum);
    }
    public List<String> getExternalTicketCodes() {
        return externalTicketCodes;
    }

    public void setExternalTicketCodes(List<String> externalTicketCodes) {
        this.externalTicketCodes = externalTicketCodes;
    }
}