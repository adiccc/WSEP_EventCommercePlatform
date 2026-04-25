package domain.event;

import domain.dataType.TicketStatus;

import static domain.dataType.TicketStatus.AVAILABLE;

public abstract class Ticket {
    private TicketStatus status;

    public Ticket(){
        this.status = AVAILABLE;
    }
}