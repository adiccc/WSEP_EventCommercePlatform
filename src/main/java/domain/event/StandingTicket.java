package domain.event;

import java.util.List;

public class StandingTicket extends Ticket{
//    public StandingTicket(int ticketId) {
//        super(ticketId);
//    }
    public StandingTicket(){
        super();
    }
    public StandingTicket(Ticket ticket){
        super(ticket.getTicketId(), ticket.getStatus());
    }
}
