package domain.event;

import DTO.SeatingZoneDTO;
import domain.dataType.ElementPosition;
import domain.dataType.TicketStatus;
import domain.dto.SeatingTicketDTO;


import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SeatingZone extends Zone {
    private Map<String, SeatingTicket> ticketMap;
    private AtomicInteger ticketIdGenerator = new AtomicInteger(1);
    private int rows;
    private int cols;

    public SeatingZone(String name, double price, int rows, int cols, ElementPosition elementPosition) {
        super(name, price, elementPosition);
        this.ticketMap = new HashMap<>();
        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++) {
                    String seatKey = i + "-" + j;
                    ticketMap.put(seatKey, new SeatingTicket(ticketIdGenerator.getAndIncrement(), i, j));
            }
        }
        this.rows = rows;
        this.cols = cols;
    }
    public SeatingZone(SeatingZone seatingZone) {
        super(seatingZone.getName(), seatingZone.getPrice(), seatingZone.getElementPosition());
        this.ticketMap = new HashMap<>();
        for(Map.Entry<String, SeatingTicket> entry : seatingZone.ticketMap.entrySet()) {
            this.ticketMap.put(entry.getKey(), new SeatingTicket(entry.getValue()));
        }
            this.rows = seatingZone.rows;
            this.cols = seatingZone.cols;
    }
    public SeatingZone(SeatingZoneDTO seatingZoneDTO) {
        super(seatingZoneDTO.getName(), seatingZoneDTO.getPrice(), seatingZoneDTO.getPosition());
        int rows = seatingZoneDTO.getRows();
        int cols = seatingZoneDTO.getCols();
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
        for(SeatingTicketDTO seat : seats) {
            for (SeatingTicket next : ticketMap.values()) {
                if (next.getRow() == seat.getRow() && next.getCol() == seat.getCol()) {
                    if (next.getStatus() == TicketStatus.AVAILABLE) {
                        next.setStatus(TicketStatus.LOCKED);
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

}
