package domain.event;

public class SeatingTicket extends Ticket {
    private int row;
    private int col;
    public SeatingTicket(int row, int col) {
        super();
        this.row = row;
        this.col = col;
    }
}
