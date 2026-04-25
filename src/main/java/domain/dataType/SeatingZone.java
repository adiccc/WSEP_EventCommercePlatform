package domain.dataType;

import DTO.SeatingZoneDTO;
import domain.event.SeatingTicket;

import java.util.ArrayList;
import java.util.List;

public class SeatingZone extends Zone {
    private List<SeatingTicket> tickets;

    public SeatingZone(String name, double price, int rows, int cols, ElementPosition elementPosition) {
        super(name, price, elementPosition);
        this.tickets = new ArrayList<>();
        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++) {
                tickets.add(new SeatingTicket(i,j));
            }
        }
    }
    public SeatingZone(SeatingZoneDTO seatingZoneDTO) {
        super(seatingZoneDTO.getName(), seatingZoneDTO.getPrice(), seatingZoneDTO.getPosition());
        int rows = seatingZoneDTO.getRows();
        int cols = seatingZoneDTO.getCols();
        this.tickets = new ArrayList<>();
        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++) {
                tickets.add(new SeatingTicket(i,j));
            }
        }
    }

}
