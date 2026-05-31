package domain.dto;

public class ActiveOrderSeatDTO {
    private final int ticketId;
    private final String zoneName;
    private final int row;
    private final int col;

    public ActiveOrderSeatDTO(int ticketId, String zoneName, int row, int col) {
        this.ticketId = ticketId;
        this.zoneName = zoneName;
        this.row = row;
        this.col = col;
    }

    public int getTicketId() {
        return ticketId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }
}