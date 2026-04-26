package domain.event;

import DTO.SeatingZoneDTO;
import domain.dataType.ElementPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SeatingZone extends Zone {
    private List<SeatingTicket> tickets;
    private AtomicInteger ticketIdGenerator = new AtomicInteger(1);

    public SeatingZone(String name, double price, int rows, int cols, ElementPosition elementPosition) {
        super(name, price, elementPosition);
        this.tickets = new ArrayList<>();
        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++) {
                tickets.add(new SeatingTicket(ticketIdGenerator.getAndIncrement(),i,j));
            }
        }
    }
    public SeatingZone(SeatingZone seatingZone) {
        super(seatingZone.getName(), seatingZone.getPrice(), seatingZone.getElementPosition());
        this.tickets=new ArrayList<>();
        for(SeatingTicket st:tickets){
            this.tickets.add(new SeatingTicket(st));
        }
    }
    public SeatingZone(SeatingZoneDTO seatingZoneDTO) {
        super(seatingZoneDTO.getName(), seatingZoneDTO.getPrice(), seatingZoneDTO.getPosition());
        int rows = seatingZoneDTO.getRows();
        int cols = seatingZoneDTO.getCols();
        this.tickets = new ArrayList<>();
        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++) {
                tickets.add(new SeatingTicket(ticketIdGenerator.getAndIncrement(),i,j));
            }
        }
    }

}
