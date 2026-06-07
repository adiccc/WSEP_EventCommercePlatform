package domain.event;

import DTO.ElementPositionDTO;
import DTO.PurchasedTicketDTO;
import domain.dataType.ElementPosition;
import domain.dataType.TicketStatus;
import domain.dto.ActiveOrderSeatDTO;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "zones")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "zone_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Integer id;
    @Column(nullable = false)
    private final String name;
    @Column(nullable = false)
    private final double price;
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "x", column = @Column(name = "position_x", nullable = false)),
            @AttributeOverride(name = "y", column = @Column(name = "position_y", nullable = false))
    })
    private final ElementPosition elementPosition;

    protected Zone() {
        // for JPA
        this.name = null;
        this.price = 0;
        this.elementPosition = null;
    }

    public Zone(String name, double price, ElementPosition elementPosition) {
        this.name = name;
        this.price = price;
        this.elementPosition = elementPosition;
    }
    public Zone(String name, double price, ElementPositionDTO elementPosition) {
        this.name = name;
        this.price = price;
        this.elementPosition = new ElementPosition(elementPosition.getX(),elementPosition.getY());
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public ElementPosition getElementPosition() {
        return new ElementPosition(elementPosition);
    }

    public double getPrice() {
        return price;
    }
    public abstract boolean containsTicketId(int ticketId);
    abstract void releaseTickets(List<Integer> ticketIds) ;
    public abstract void markTicketsAsSold(List<Integer> ticketIds);
    public abstract List<PurchasedTicketDTO> getPurchasedTicketDetails(List<Integer> ticketIds);
    public abstract TicketStatus getTicketStatus(int ticketId);
    public abstract boolean hasAvailableTickets();
    public abstract List<ActiveOrderSeatDTO> getActiveOrderSeats(List<Integer> ticketIds);
    public abstract List<Ticket> getTickets();
}
