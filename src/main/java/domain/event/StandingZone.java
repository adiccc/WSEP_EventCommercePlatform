package domain.event;

import DTO.PurchasedTicketDTO;
import DTO.StandingZoneDTO;
import domain.dataType.ElementPosition;
import domain.dataType.TicketStatus;
import domain.dto.ActiveOrderSeatDTO;
import jakarta.persistence.*;

import java.util.*;
@Entity
@DiscriminatorValue("STANDING")
public class StandingZone extends Zone {
    @Column(name = "capacity")
    private int capacity;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private List<StandingTicket> tickets = new ArrayList<>();
    @Transient
    private Queue<StandingTicket> availableTickets = new LinkedList<>();
    @Transient
    private List<StandingTicket> occupiedTickets = new LinkedList<>();
    @Column(name = "available")
    private int available;

    protected StandingZone() {
        // for JPA
        capacity = 0;
    }


    public StandingZone(String name, double price, int capacity, ElementPosition elementPosition) {
        super(name,price,elementPosition);
        this.capacity = capacity;
        for(int i=0;i<capacity;i++){
            StandingTicket ticket = new StandingTicket();
            this.availableTickets.add(ticket);
            this.tickets.add(ticket);
        }
        available=capacity;
    }
    public StandingZone(StandingZone zone){
        super(zone.getName(),zone.getPrice(),zone.getElementPosition());
        setId(zone.getId());
        this.capacity = zone.capacity;
        zone.ensureTransientTicketCollections();
        for (StandingTicket st : zone.availableTickets) {
            StandingTicket copiedTicket = new StandingTicket(st);
            this.availableTickets.add(copiedTicket);
            this.tickets.add(copiedTicket);
        }
        for(StandingTicket st : zone.occupiedTickets) {
            StandingTicket copiedTicket = new StandingTicket(st);
            this.occupiedTickets.add(copiedTicket);
            this.tickets.add(copiedTicket);
        }
        this.available = zone.available;
    }


    public StandingZone(StandingZoneDTO standingZoneDTO) {
        super(standingZoneDTO.getName(),standingZoneDTO.getPrice(),standingZoneDTO.getPosition());
        this.capacity = standingZoneDTO.getCapacty();
        for(int i=0;i<capacity;i++){
            StandingTicket ticket = new StandingTicket();
            this.availableTickets.add(ticket);
            this.tickets.add(ticket);
        }
        available=capacity;
    }

    @PostLoad
    private void rebuildTransientTicketCollections() {
        this.availableTickets = new LinkedList<>();
        this.occupiedTickets = new LinkedList<>();

        if (this.tickets == null) {
            this.tickets = new ArrayList<>();
            this.available = 0;
            return;
        }

        for (StandingTicket ticket : tickets) {
            if (ticket.getStatus() == TicketStatus.AVAILABLE) {
                availableTickets.add(ticket);
            } else {
                occupiedTickets.add(ticket);
            }
        }

        this.available = availableTickets.size();
    }

    private void ensureTransientTicketCollections() {
        if (tickets == null
                || availableTickets == null
                || occupiedTickets == null
                || availableTickets.size() + occupiedTickets.size() != tickets.size()) {
            rebuildTransientTicketCollections();
        }
    }

    public List<Ticket> getTickets(){
        ensureTransientTicketCollections();
        ArrayList<Ticket> tickets = new ArrayList<>();
        tickets.addAll(this.availableTickets);
        tickets.addAll(this.occupiedTickets);
        return tickets;
    }
    public int getCapacity() {
        return capacity;
    }

    public int getAvailable() {
        ensureTransientTicketCollections();
        return available;
    }

    public List<Integer> bookTickets(int quantity) {
        ensureTransientTicketCollections();
        if (quantity > available) {
            throw new IllegalArgumentException("Not enough tickets available in this zone.");
        }

        List<Integer> bookedTicketIds = new ArrayList<>();

        for (int i = 0; i < quantity; i++) {
            StandingTicket ticket = availableTickets.poll();

            if (ticket == null) {
                throw new IllegalStateException("No more tickets available.");
            }
            int ticketId = ticket.getTicketId();
            ticket.setStatusFromAvailable(TicketStatus.LOCKED);
            occupiedTickets.add(ticket); //add the ticket to the occupied list
            bookedTicketIds.add(ticketId);
        }

        available -= quantity;
        return bookedTicketIds;
    }

    public boolean TicketIsBooked(int ticketId) {
        ensureTransientTicketCollections();
        for (StandingTicket ticket : occupiedTickets) {
            if (ticket.getTicketId() == ticketId&& ticket.getStatus() == TicketStatus.LOCKED) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsTicketId(int ticketId) {
        ensureTransientTicketCollections();
        for (StandingTicket ticket : availableTickets) {
            if (ticket.getTicketId() == ticketId) {
                return true;
            }
        }

        for (StandingTicket ticket : occupiedTickets) {
            if (ticket.getTicketId() == ticketId) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void releaseTickets(List<Integer> ticketIds) {
        ensureTransientTicketCollections();
        List<StandingTicket> ticketsToRemove = new ArrayList<>();

        for (Integer ticketId : ticketIds) {
            for (StandingTicket ticket : occupiedTickets) {
                if (ticket.getTicketId() == ticketId && ticket.getStatus() == TicketStatus.LOCKED) {
                    ticket.makeAvailableFromLocked();
                    availableTickets.add(ticket);
                    available++;
                    ticketsToRemove.add(ticket);
                    break;
                }
            }
        }

        occupiedTickets.removeAll(ticketsToRemove);
    }

    public List<StandingTicket> getOccupiedTickets() {
        ensureTransientTicketCollections();
        return occupiedTickets;
    }

    public int countTickets(List<Integer> currentTickets) {
        ensureTransientTicketCollections();
        int count = 0;
        for (Integer ticketId : currentTickets) {
            for (StandingTicket ticket : occupiedTickets) {
                if (ticket.getTicketId() == ticketId) {
                    count++;
                }
            }
        }
        return count;
    }

    public List<Integer> pickStandingFromZone(List<Integer> newTickets, int numToPick) {
        ensureTransientTicketCollections();
        List<Integer> pickedTickets = new ArrayList<>();
        for (Integer ticketId : newTickets) {
            if (pickedTickets.size() >= numToPick) break;
            for (StandingTicket ticket : occupiedTickets) {
                if (ticket.getTicketId() == ticketId) {
                    pickedTickets.add(ticketId);
                    break;
                }
            }
        }
        if (pickedTickets.size() < numToPick) {
            throw new IllegalArgumentException(
                    "Not enough tickets to pick: requested " + numToPick
                            + ", user holds " + pickedTickets.size() + " in this zone");
        }
        return pickedTickets;
    }

    @Override
    public void markTicketsAsSold(List<Integer> ticketIds) {
        ensureTransientTicketCollections();
        for (Integer ticketId : ticketIds) {
            for (StandingTicket ticket : occupiedTickets) {
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
        ensureTransientTicketCollections();
        List<PurchasedTicketDTO> result = new ArrayList<>();

        for (Integer ticketId : ticketIds) {
            for (StandingTicket ticket : occupiedTickets) {
                if (ticket.getTicketId() == ticketId) {
                    result.add(new PurchasedTicketDTO(
                            ticket.getTicketId(),
                            getName(),
                            "STANDING",
                            null,
                            null,
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
        ensureTransientTicketCollections();
        for (Ticket ticket : availableTickets) {
            if (ticket.getTicketId() == ticketId) {
                return ticket.getStatus();
            }
        }

        for (Ticket ticket : occupiedTickets) {
            if (ticket.getTicketId() == ticketId) {
                return ticket.getStatus();
            }
        }

        throw new IllegalArgumentException("Ticket does not exist in standing zone: " + ticketId);}

    public boolean hasAvailableTickets() {
        ensureTransientTicketCollections();
        return available > 0;

    }

    @Override
    public List<ActiveOrderSeatDTO> getActiveOrderSeats(List<Integer> ticketIds) {
        return Collections.emptyList();
    }

    public int countActiveOrderTickets(List<Integer> ticketIds) {
        int count = 0;

        for (Integer ticketId : ticketIds) {
            if (containsTicketId(ticketId)) {
                count++;
            }
        }

        return count;
    }
}
