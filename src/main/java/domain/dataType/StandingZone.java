package domain.dataType;

import DTO.StandingZoneDTO;
import domain.event.StandingTicket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class StandingZone extends Zone {
    private final int capacity;
    private final ConcurrentLinkedQueue<StandingTicket> availableTickets = new ConcurrentLinkedQueue<>();
    private AtomicInteger ticketIdGenerator=new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, List<StandingTicket>> occupiedTickets = new ConcurrentHashMap<>();//userid and tickets
    private int avaliable;


    public StandingZone(String name, double price, int capacity, ElementPosition elementPosition) {
        super(name,price,elementPosition);
        this.capacity = capacity;
        for(int i=0;i<capacity;i++){
            this.availableTickets.add(new StandingTicket(ticketIdGenerator.getAndIncrement()));
        }
        avaliable=capacity;
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
        for(int i=0;i<capacity;i++){
            this.availableTickets.add(new StandingTicket(ticketIdGenerator.getAndIncrement()));
        }
        avaliable=capacity;
    }
    public int getCapacity() {
        return capacity;
    }

    public List<Integer> bookTickets(int userid,int quantity) {
        if (quantity > avaliable) {
            throw new IllegalArgumentException("Not enough tickets available in this zone.");
        }
        List<Integer> bookedTicketIds = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            StandingTicket ticket = availableTickets.poll(); //get a ticket from the available queue
            if (ticket == null) {
                throw new IllegalStateException("No more tickets available.");
            }
            int ticketId = ticket.getTicketId();
            occupiedTickets.computeIfAbsent(userid, k -> new ArrayList<>()).add(ticket);
            bookedTicketIds.add(ticketId);
        }
        avaliable -= quantity; //decrease the capacity by the number of booked tickets
        return bookedTicketIds;
    }
}
