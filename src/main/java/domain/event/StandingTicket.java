package domain.event;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "standing_tickets")
public class StandingTicket extends Ticket{
//    public StandingTicket(int ticketId) {
//        super(ticketId);
//    }
    public StandingTicket(){
        super();
    }
    public StandingTicket(Ticket ticket) {
        super(ticket.getId(), ticket.getStatus());
        setVersion(ticket.getVersion());
    }
}
