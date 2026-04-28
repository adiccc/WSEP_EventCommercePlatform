package domain.event;

public class SeatingTicket extends Ticket {
    private final int row;
    private final int col;
    public SeatingTicket(int ticketId,int row, int col) {
        super(ticketId);
        this.row = row;
        this.col = col;
    }
    public SeatingTicket(SeatingTicket seatingTicket) {
        this.col = seatingTicket.col;
        this.row = seatingTicket.row;
    }
}
