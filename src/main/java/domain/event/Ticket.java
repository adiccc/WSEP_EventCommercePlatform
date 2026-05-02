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

    public Ticket(int ticketId, TicketStatus status) {
        this.ticketId = ticketId;
        this.status = status;
    }

    public int getTicketId() {
        return ticketId;
    }

    public boolean setStatusFromAvailable(TicketStatus newStatus) {
        if (this.status == AVAILABLE) {
            this.status = newStatus;
            return true;
        }
        return false; //cannot change status if it's not available
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void makeAvailableFromLocked() {
        if (this.status == TicketStatus.LOCKED) {
            this.status = AVAILABLE;
        }
    }
}