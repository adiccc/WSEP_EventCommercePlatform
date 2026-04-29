package domain.dataType;

import DTO.SeatingZoneDTO;
import domain.event.SeatingTicket;

import java.util.ArrayList;
import java.util.List;

public class SeatingZone extends Zone {
    private List<SeatingTicket> tickets;
    private int rows;
    private int cols;

    public SeatingZone(String name, double price, int rows, int cols, ElementPosition elementPosition) {
        super(name, price, elementPosition);
        this.rows = rows;
        this.cols = cols;
        this.tickets = new ArrayList<>();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                tickets.add(new SeatingTicket(i, j));
            }
        }
    }

    public SeatingZone(SeatingZone seatingZone) {
        super(seatingZone.getName(), seatingZone.getPrice(), seatingZone.getElementPosition());
        this.rows = seatingZone.getRows();
        this.cols = seatingZone.getCols();
        this.tickets = new ArrayList<>();

        for (SeatingTicket st : seatingZone.tickets) {
            this.tickets.add(new SeatingTicket(st));
        }
    }

    public SeatingZone(SeatingZoneDTO seatingZoneDTO) {
        super(seatingZoneDTO.getName(), seatingZoneDTO.getPrice(), seatingZoneDTO.getPosition());
        this.rows = seatingZoneDTO.getRows();
        this.cols = seatingZoneDTO.getCols();
        this.tickets = new ArrayList<>();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                tickets.add(new SeatingTicket(i, j));
            }
        }
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }
}
