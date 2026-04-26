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
}