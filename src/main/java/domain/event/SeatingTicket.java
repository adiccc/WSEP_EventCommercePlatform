package domain.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "seating_tickets")
public class SeatingTicket extends Ticket {
    @Column(nullable = false)
    private int row;
    @Column(nullable = false)
    private int col;

    protected SeatingTicket() {
        // for JPA
    }

    public SeatingTicket(int row, int col) {
        super();
        this.row = row;
        this.col = col;
    }
    public SeatingTicket(SeatingTicket seatingTicket) {
        super(seatingTicket.getId(), seatingTicket.getStatus());
        setVersion(seatingTicket.getVersion());
        this.col = seatingTicket.col;
        this.row = seatingTicket.row;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }
}
