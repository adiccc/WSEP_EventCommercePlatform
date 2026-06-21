package domain.event;

import DTO.PurchasedTicketDTO;
import DTO.SeatingZoneDTO;
import domain.dataType.ElementPosition;
import domain.dataType.TicketStatus;
import domain.dto.ActiveOrderSeatDTO;
import domain.dto.SeatingTicketDTO;
import jakarta.persistence.*;


import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
@Entity
@DiscriminatorValue("SEATING")
public class SeatingZone extends Zone {
    //for JPA, we need to store tickets in a list, but we will maintain a transient map for efficient access
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id")
    private List<SeatingTicket> tickets = new ArrayList<>();
    @Transient
    private Map<String, SeatingTicket> ticketMap = new HashMap<>();
    @Column(name = "rows")
    private int rows;
    @Column(name = "cols")
    private int cols;

    protected SeatingZone() {
        // for JPA
    }

    public SeatingZone(String name, double price, int rows, int cols, ElementPosition elementPosition) {
        super(name, price, elementPosition);
        this.ticketMap = new HashMap<>();
        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++) {
                    String seatKey = i + "-" + j;
                    SeatingTicket ticket = new SeatingTicket(i, j);
                    ticketMap.put(seatKey, ticket);
                    tickets.add(ticket);
            }
        }
        this.rows = rows;
        this.cols = cols;
    }
    public SeatingZone(SeatingZone seatingZone) {
        super(seatingZone.getName(), seatingZone.getPrice(), seatingZone.getElementPosition());
        setId(seatingZone.getId());
        this.ticketMap = new HashMap<>();
        this.tickets = new ArrayList<>();
        seatingZone.ensureTicketMap();
        for(Map.Entry<String, SeatingTicket> entry : seatingZone.ticketMap.entrySet()) {
            SeatingTicket copiedTicket = new SeatingTicket(entry.getValue());
            this.ticketMap.put(entry.getKey(), copiedTicket);
            this.tickets.add(copiedTicket);
        }
            this.rows = seatingZone.rows;
            this.cols = seatingZone.cols;
    }
    public SeatingZone(SeatingZoneDTO seatingZoneDTO) {
        super(seatingZoneDTO.getName(), seatingZoneDTO.getPrice(), seatingZoneDTO.getPosition());
        int rows = seatingZoneDTO.getRows();
        int cols = seatingZoneDTO.getCols();
        this.ticketMap = new HashMap<>();
        this.tickets = new ArrayList<>();
        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++) {
                SeatingTicket ticket = new SeatingTicket(i, j);
                ticketMap.put(i + "-" + j, ticket);
                tickets.add(ticket);
            }
        }
        this.rows = rows;
        this.cols = cols;
    }

    @PostLoad
    private void rebuildTicketMap() {
        this.ticketMap = new HashMap<>();

        if (this.tickets == null) {
            this.tickets = new ArrayList<>();
            return;
        }

        for (SeatingTicket ticket : tickets) {
            String seatKey = ticket.getRow() + "-" + ticket.getCol();
            ticketMap.put(seatKey, ticket);
        }
    }

    private void ensureTicketMap() {
        if (ticketMap == null || ticketMap.size() != tickets.size()) {
            rebuildTicketMap();
        }
    }


    public List<Ticket> getTickets(){
        ensureTicketMap();
        return new ArrayList<>(ticketMap.values());
    }

    public Collection<Integer> bookTickets(List<SeatingTicketDTO> seats) {
        ensureTicketMap();
        List<Integer> bookedTicketIds = new ArrayList<>();
        for (SeatingTicketDTO seat : seats) {
            for (SeatingTicket next : ticketMap.values()) {
                if (next.getRow() == seat.getRow() && next.getCol() == seat.getCol()) {
                    if (next.getStatus() == TicketStatus.AVAILABLE) {
                        next.setStatusFromAvailable(TicketStatus.LOCKED);
                        bookedTicketIds.add(next.getTicketId());
                    } else {
                        throw new IllegalArgumentException("Seat " + seat + " is not available.");
                    }
                }
            }
        }
        return bookedTicketIds;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }
    @Override
    public boolean containsTicketId(int ticketId) {
        ensureTicketMap();
        for (SeatingTicket ticket : ticketMap.values()) {
            if (ticket.getTicketId() == ticketId) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void releaseTickets(List<Integer> ticketIds) {
        ensureTicketMap();
        for (Integer ticketId : ticketIds) {
            for (SeatingTicket ticket : ticketMap.values()) {
                if (ticket.getTicketId() == ticketId && ticket.getStatus() == TicketStatus.LOCKED) {
                    ticket.makeAvailableFromLocked();
                }
            }
        }
    }

    public Map<String, SeatingTicket> getTicketMap() {
        ensureTicketMap();
        return ticketMap;
    }


    public Collection<Integer> findSeatingTicketIds(List<SeatingTicketDTO> seats) {
        ensureTicketMap();
        List<Integer> seatingTicketIds = new ArrayList<>();
        for (SeatingTicketDTO seat : seats) {
            for (SeatingTicket next : ticketMap.values()) {
                if (next.getRow() == seat.getRow() && next.getCol() == seat.getCol()) {
                    seatingTicketIds.add(next.getTicketId());
                }
            }
        }
        if (seatingTicketIds.size() != seats.size()) {
            throw new IllegalArgumentException("Some seats were not found in the zone.");
        }
        return seatingTicketIds;
    }

    @Override
    public void markTicketsAsSold(List<Integer> ticketIds) {
        ensureTicketMap();
        for (Integer ticketId : ticketIds) {
            for (SeatingTicket ticket : ticketMap.values()) {
                if (ticket.getTicketId() == ticketId) {
                    if (ticket.getStatus() == TicketStatus.LOCKED) {
                        ticket.setStatusFromLocked(TicketStatus.SOLD);
                    }
                }
            }
        }
    }

    @Override
    public List<PurchasedTicketDTO> getPurchasedTicketDetails(List<Integer> ticketIds) {
        ensureTicketMap();
        List<PurchasedTicketDTO> result = new ArrayList<>();

        for (Integer ticketId : ticketIds) {
            for (SeatingTicket ticket : ticketMap.values()) {
                if (ticket.getTicketId() == ticketId) {
                    result.add(new PurchasedTicketDTO(
                            ticket.getTicketId(),
                            getName(),
                            "SEATING",
                            ticket.getRow(),
                            ticket.getCol(),
                            getPrice()
                    ));
                    break;
                }
            }
        }

        return result;
    }

    @Override
    public TicketStatus getTicketStatus(int ticketId) {
        ensureTicketMap();
        for (SeatingTicket ticket : ticketMap.values()) {
            if (ticket.getTicketId() == ticketId) {
                return ticket.getStatus();
            }
        }

        throw new IllegalArgumentException("Ticket does not exist in seating zone: " + ticketId);}

    public boolean hasAvailableTickets() {
        ensureTicketMap();
        for (SeatingTicket ticket : ticketMap.values()) {
            if (ticket.getStatus() == TicketStatus.AVAILABLE) {
                return true;
            }
        }
        return false;

    }

    @Override
    public List<ActiveOrderSeatDTO> getActiveOrderSeats(List<Integer> ticketIds) {
        ensureTicketMap();
        List<ActiveOrderSeatDTO> result = new ArrayList<>();

        for (Integer ticketId : ticketIds) {
            for (SeatingTicket ticket : ticketMap.values()) {
                if (ticket.getTicketId() == ticketId) {
                    result.add(new ActiveOrderSeatDTO(
                            ticket.getTicketId(),
                            getName(),
                            ticket.getRow(),
                            ticket.getCol()
                    ));
                    break;
                }
            }
        }

        return result;
    }
}
