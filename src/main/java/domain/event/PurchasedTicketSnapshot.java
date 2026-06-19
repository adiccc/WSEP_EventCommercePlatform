package domain.event;

import DTO.PurchasedTicketDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class PurchasedTicketSnapshot {

    @Column(name = "ticket_id", nullable = false)
    private int ticketId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Column(name = "ticket_type", nullable = false)
    private String ticketType;

    @Column(name = "seat_row")
    private Integer row;

    @Column(name = "seat_col")
    private Integer col;

    @Column(name = "price_at_purchase", nullable = false)
    private double priceAtPurchase;

    protected PurchasedTicketSnapshot() {
    }

    public PurchasedTicketSnapshot(PurchasedTicketDTO dto) {
        this.ticketId = dto.getTicketId();
        this.zoneName = dto.getZoneName();
        this.ticketType = dto.getTicketType();
        this.row = dto.getRow();
        this.col = dto.getCol();
        this.priceAtPurchase = dto.getPriceAtPurchase();
    }

    public PurchasedTicketSnapshot(PurchasedTicketSnapshot other) {
        this.ticketId = other.ticketId;
        this.zoneName = other.zoneName;
        this.ticketType = other.ticketType;
        this.row = other.row;
        this.col = other.col;
        this.priceAtPurchase = other.priceAtPurchase;
    }

    public int getTicketId() {
        return ticketId;
    }

    public PurchasedTicketDTO toDTO() {
        return new PurchasedTicketDTO(
                ticketId,
                zoneName,
                ticketType,
                row,
                col,
                priceAtPurchase
        );
    }
}