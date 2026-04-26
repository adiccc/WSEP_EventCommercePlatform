package domain.event;

import domain.dataType.TicketStatus;

import static domain.dataType.TicketStatus.AVAILABLE;

public abstract class Ticket {
    private final int ticketId;
    private TicketStatus status;

    public Ticket(int ticketId) {
        this.ticketId = ticketId;
        this.status = AVAILABLE;
    }

    public int getTicketId() {
        return ticketId;
    }

    public boolean setStatus(TicketStatus newStatus) {
        if (this.status == AVAILABLE) {
            this.status = newStatus;
            return true;
        }
        return false; //cannot change status if it's not available
    }
}