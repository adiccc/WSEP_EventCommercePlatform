package domain.event;

import domain.dataType.TicketStatus;
import jakarta.persistence.*;

import static domain.dataType.TicketStatus.AVAILABLE;

@MappedSuperclass
public abstract class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ticket_seq")
    @SequenceGenerator(name = "ticket_seq", sequenceName = "global_ticket_sequence", allocationSize = 1)
    @Column(name = "ticket_id", nullable = false)
    private Integer ticketId;
    @Version
    private long version;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

//    public Ticket(int ticketId) {
//        this.ticketId = ticketId;
//        this.status = AVAILABLE;
//    }
    public Ticket() {
        status=AVAILABLE;
    }

    public Ticket(int ticketId, TicketStatus status) {
        this.ticketId = ticketId;
        this.status = status;
    }

    public void setId(Integer ticketId) {
        this.ticketId=ticketId;
    }

    public Integer getId() {
        return ticketId;
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

    public void setStatusFromLocked(TicketStatus newStatus) {
        if (this.status == TicketStatus.LOCKED) {
            this.status = newStatus;
        }
    }

    public long getVersion() {
        return version;
    }

    protected void setVersion(long version) {
        this.version = version;
    }

}