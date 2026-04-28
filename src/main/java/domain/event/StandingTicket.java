package domain.event;

public class StandingTicket extends Ticket{
    public StandingTicket(int ticketId) {
        super(ticketId);
    }
    public StandingTicket(Ticket ticket){
        super(ticket.getTicketId());
    }
}
