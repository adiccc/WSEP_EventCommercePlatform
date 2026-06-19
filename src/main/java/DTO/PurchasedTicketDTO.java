package DTO;

public class PurchasedTicketDTO {
    private final int ticketId;
    private final String zoneName;
    private final String ticketType;
    private final Integer row;
    private final Integer col;
    private final double priceAtPurchase;
    String barcode;
    public PurchasedTicketDTO(
            int ticketId,
            String zoneName,
            String ticketType,
            Integer row,
            Integer col,
            double priceAtPurchase
    ) {
        this(ticketId, zoneName, ticketType, row, col, priceAtPurchase, null);
    }

    public PurchasedTicketDTO(
            int ticketId,
            String zoneName,
            String ticketType,
            Integer row,
            Integer col,
            double priceAtPurchase,
            String barcode
    ) {
        this.ticketId = ticketId;
        this.zoneName = zoneName;
        this.ticketType = ticketType;
        this.row = row;
        this.col = col;
        this.priceAtPurchase = priceAtPurchase;
        this.barcode = barcode;
    }

    public int getTicketId() {
        return ticketId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public String getTicketType() {
        return ticketType;
    }

    public Integer getRow() {
        return row;
    }

    public Integer getCol() {
        return col;
    }

    public double getPriceAtPurchase() {
        return priceAtPurchase;
    }
    public String getBarcode() {
        return barcode;
    }
}