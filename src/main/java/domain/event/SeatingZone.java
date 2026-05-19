package domain.event;

import DTO.SeatingZoneDTO;
import domain.dataType.ElementPosition;
import domain.dataType.TicketStatus;
import domain.dto.SeatingTicketDTO;


import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SeatingZone extends Zone {
    private Map<String, SeatingTicket> ticketMap;
    private AtomicInteger ticketIdGenerator;
    private int rows;
    private int cols;

    public SeatingZone(String name, double price, int rows, int cols, ElementPosition elementPosition, AtomicInteger ticketIdGenerator) {
        super(name, price, elementPosition);
        this.ticketMap = new HashMap<>();
        this.ticketIdGenerator = ticketIdGenerator;
        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++) {
                    String seatKey = i + "-" + j;
                    ticketMap.put(seatKey, new SeatingTicket(ticketIdGenerator.getAndIncrement(), i, j));
            }
        }
        this.rows = rows;
        this.cols = cols;
    }
    public SeatingZone(SeatingZone seatingZone,AtomicInteger ticketIdGenerator) {
        super(seatingZone.getName(), seatingZone.getPrice(), seatingZone.getElementPosition());
        this.ticketMap = new HashMap<>();
        this.ticketIdGenerator = ticketIdGenerator;
        for(Map.Entry<String, SeatingTicket> entry : seatingZone.ticketMap.entrySet()) {
            this.ticketMap.put(entry.getKey(), new SeatingTicket(entry.getValue()));
        }
            this.rows = seatingZone.rows;
            this.cols = seatingZone.cols;
        assert this.ticketIdGenerator != null;
        this.ticketIdGenerator.set(seatingZone.ticketIdGenerator.get());
    }
    public SeatingZone(SeatingZoneDTO seatingZoneDTO, AtomicInteger generator) {
        super(seatingZoneDTO.getName(), seatingZoneDTO.getPrice(), seatingZoneDTO.getPosition());
        int rows = seatingZoneDTO.getRows();
        int cols = seatingZoneDTO.getCols();
        this.ticketIdGenerator = generator;
        this.ticketMap = new HashMap<>();
        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++) {
                ticketMap.put(i + "-" + j, new SeatingTicket(ticketIdGenerator.getAndIncrement(), i, j));
            }
        }
        this.rows = rows;
        this.cols = cols;
    }


    public Collection<Integer> bookTickets(List<SeatingTicketDTO> seats) {
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
        for (SeatingTicket ticket : ticketMap.values()) {
            if (ticket.getTicketId() == ticketId) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void releaseTickets(List<Integer> ticketIds) {
        for (Integer ticketId : ticketIds) {
            for (SeatingTicket ticket : ticketMap.values()) {
                if (ticket.getTicketId() == ticketId) {
                    if (ticket.getStatus() == TicketStatus.LOCKED) {
                        ticket.makeAvailableFromLocked();
                    }
                }
            }
        }
    }
    public Map<String, SeatingTicket> getTicketMap() {
        return ticketMap;
    }


    public AtomicInteger getTicketIdGenerator() {
        return ticketIdGenerator;
    }

    public Collection<Integer> findSeatingTicketIds(List<SeatingTicketDTO> seats) {
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
    public boolean hasAvailableTickets() {
        for (SeatingTicket ticket : ticketMap.values()) {
            if (ticket.getStatus() == TicketStatus.AVAILABLE) {
                return true;
            }
        }
        return false;
    }
}
