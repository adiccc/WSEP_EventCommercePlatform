package domain.event;

import DTO.ElementPositionDTO;
import DTO.PurchasedTicketDTO;
import domain.dataType.ElementPosition;
import domain.dataType.TicketStatus;

import java.util.List;

public abstract class Zone {
    private final String name;
    private final double price;
    private final ElementPosition elementPosition;

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
}
