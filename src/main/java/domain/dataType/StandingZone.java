package domain.dataType;

import DTO.StandingZoneDTO;
import domain.event.StandingTicket;

import java.util.ArrayList;
import java.util.List;

public class StandingZone extends Zone {
    private int capacity;
    private List<StandingTicket> tickets;

    public StandingZone(String name, double price, int capacity, ElementPosition elementPosition) {
        super(name,price,elementPosition);
        this.capacity = capacity;
        this.tickets = new ArrayList<>();
        for(int i=0;i<capacity;i++){
            this.tickets.add(new StandingTicket());
        }
    }
    public StandingZone(StandingZone zone){
        super(zone.getName(),zone.getPrice(),zone.getElementPosition());
        this.capacity = zone.capacity;
        this.tickets=new ArrayList<>();
        for(StandingTicket st:tickets){
            this.tickets.add(new StandingTicket(st));
        }
    }
    public StandingZone(StandingZoneDTO standingZoneDTO) {
        super(standingZoneDTO.getName(),standingZoneDTO.getPrice(),standingZoneDTO.getPosition());
        this.capacity = standingZoneDTO.getCapacty();
        this.tickets = new ArrayList<StandingTicket>();
        for(int i=0;i<capacity;i++){
            this.tickets.add(new StandingTicket());
        }
    }

    public int getCapacity() {
        return capacity;
    }

     public List<StandingTicket> getTickets() {
        return tickets;
    }
}
