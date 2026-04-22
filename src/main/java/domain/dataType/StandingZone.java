package domain.dataType;

import DTO.StandingZoneDTO;
import domain.event.StandingTicket;

import java.util.List;

public class StandingZone extends Zone {
    private int capacity;
    private List<StandingTicket> tickets;

    public StandingZone(String name, double price, int capacity, ElementPosition elementPosition) {
        super(name,price,elementPosition);
        this.capacity = capacity;
        for(int i=0;i<capacity;i++){
            this.tickets.add(new StandingTicket());
        }
    }
    public StandingZone(StandingZoneDTO standingZoneDTO) {
        super(standingZoneDTO.getName(),standingZoneDTO.getPrice(),standingZoneDTO.getPosition());
        this.capacity = standingZoneDTO.getCapacty();
        for(int i=0;i<capacity;i++){
            this.tickets.add(new StandingTicket());
        }
    }
}
