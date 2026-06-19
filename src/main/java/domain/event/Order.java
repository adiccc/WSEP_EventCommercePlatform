package domain.event;

import DTO.PurchaseHistoryDTO;
import DTO.PurchasedTicketDTO;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "order_id", nullable = false)
    private int orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "user_identifier", nullable = false)
    private String userIdentifier;

    // חשוב: אם אין @ManyToOne שמשתמש באותו event_id,
    // לא לשים insertable=false, updatable=false.
    @Column(name = "event_id", nullable = false)
    private Integer eventId;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "event_date", nullable = false)
    private String eventDate;

    @Column(name = "event_location", nullable = false)
    private String eventLocation;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "order_purchased_tickets",
            joinColumns = @JoinColumn(name = "order_id")
    )
    @OrderColumn(name = "ticket_index")
    private List<PurchasedTicketSnapshot> purchasedTickets = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "order_external_ticket_codes",
            joinColumns = @JoinColumn(name = "order_id")
    )
    @Column(name = "external_ticket_code")
    @OrderColumn(name = "code_index")
    private List<String> externalTicketCodes = new ArrayList<>();

    @Column(name = "total_sum", nullable = false)
    private double totalSum;

    @Column(name = "payment_confirmation_id", nullable = false)
    private String paymentConfirmationId;

    protected Order() {
        this.purchasedTickets = new ArrayList<>();
        this.externalTicketCodes = new ArrayList<>();
    }

    public Order(int orderId,
                 String userIdentifier,
                 Integer eventId,
                 String eventName,
                 String eventDate,
                 String eventLocation,
                 List<PurchasedTicketDTO> purchasedTickets,
                 double totalSum,
                 String paymentConfirmationId) {

        this(orderId,
                userIdentifier,
                eventId,
                eventName,
                eventDate,
                eventLocation,
                purchasedTickets,
                totalSum,
                paymentConfirmationId,
                new ArrayList<>());
    }

    public Order(int orderId,
                 String userIdentifier,
                 Integer eventId,
                 String eventName,
                 String eventDate,
                 String eventLocation,
                 List<PurchasedTicketDTO> purchasedTickets,
                 double totalSum,
                 String paymentConfirmationId,
                 List<String> externalTicketCodes) {

        this.orderId = orderId;
        this.userIdentifier = userIdentifier;
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.eventLocation = eventLocation;

        this.purchasedTickets = new ArrayList<>();
        if (purchasedTickets != null) {
            for (PurchasedTicketDTO ticket : purchasedTickets) {
                this.purchasedTickets.add(new PurchasedTicketSnapshot(ticket));
            }
        }

        this.externalTicketCodes = externalTicketCodes == null
                ? new ArrayList<>()
                : new ArrayList<>(externalTicketCodes);

        this.status = OrderStatus.APPROVED;
        this.totalSum = totalSum;
        this.paymentConfirmationId = paymentConfirmationId;
    }

    /**
     * בנאי תאימות לקוד הישן שלך.
     * לא שומרים את tickets כשדה נפרד, כי אפשר לגזור אותם מתוך purchasedTickets.
     */
    public Order(int orderId,
                 String userIdentifier,
                 Integer eventId,
                 String eventName,
                 String eventDate,
                 String eventLocation,
                 List<PurchasedTicketDTO> purchasedTickets,
                 List<Integer> tickets,
                 double totalSum,
                 String paymentConfirmationId,
                 List<String> externalTicketCodes) {

        this(orderId,
                userIdentifier,
                eventId,
                eventName,
                eventDate,
                eventLocation,
                purchasedTickets,
                totalSum,
                paymentConfirmationId,
                externalTicketCodes);
    }

    public Order(Order order) {
        this.orderId = order.orderId;
        this.userIdentifier = order.userIdentifier;
        this.eventId = order.eventId;
        this.eventName = order.eventName;
        this.eventDate = order.eventDate;
        this.eventLocation = order.eventLocation;

        this.purchasedTickets = new ArrayList<>();
        if (order.purchasedTickets != null) {
            for (PurchasedTicketSnapshot ticket : order.purchasedTickets) {
                this.purchasedTickets.add(new PurchasedTicketSnapshot(ticket));
            }
        }

        this.externalTicketCodes = order.externalTicketCodes == null
                ? new ArrayList<>()
                : new ArrayList<>(order.externalTicketCodes);

        this.status = order.status;
        this.totalSum = order.totalSum;
        this.paymentConfirmationId = order.paymentConfirmationId;
    }

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
        return purchasedTickets == null ? 0 : purchasedTickets.size();
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public Integer getEventId() {
        return eventId;
    }

    public List<Integer> getTickets() {
        List<Integer> ticketIds = new ArrayList<>();

        if (purchasedTickets != null) {
            for (PurchasedTicketSnapshot ticket : purchasedTickets) {
                ticketIds.add(ticket.getTicketId());
            }
        }

        return ticketIds;
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
        List<PurchasedTicketDTO> result = new ArrayList<>();

        if (purchasedTickets != null) {
            for (PurchasedTicketSnapshot ticket : purchasedTickets) {
                result.add(ticket.toDTO());
            }
        }

        return result;
    }

    public PurchaseHistoryDTO toPurchaseHistoryDTO() {
        List<PurchasedTicketDTO> enrichedTickets = new ArrayList<>();
        List<PurchasedTicketDTO> ticketsDTO = getPurchasedTickets();

        for (int i = 0; i < ticketsDTO.size(); i++) {
            PurchasedTicketDTO ticketDTO = ticketsDTO.get(i);

            String barcode = "-";
            if (externalTicketCodes != null
                    && !externalTicketCodes.isEmpty()
                    && i < externalTicketCodes.size()) {
                barcode = externalTicketCodes.get(i);
            }

            enrichedTickets.add(new PurchasedTicketDTO(
                    ticketDTO.getTicketId(),
                    ticketDTO.getZoneName(),
                    ticketDTO.getTicketType(),
                    ticketDTO.getRow(),
                    ticketDTO.getCol(),
                    ticketDTO.getPriceAtPurchase(),
                    barcode
            ));
        }

        return new PurchaseHistoryDTO(
                orderId,
                eventName,
                eventDate,
                eventLocation,
                status,
                enrichedTickets,
                totalSum
        );
    }

    public List<String> getExternalTicketCodes() {
        return externalTicketCodes == null
                ? new ArrayList<>()
                : new ArrayList<>(externalTicketCodes);
    }

    public void setExternalTicketCodes(List<String> externalTicketCodes) {
        this.externalTicketCodes = externalTicketCodes == null
                ? new ArrayList<>()
                : new ArrayList<>(externalTicketCodes);
    }
}