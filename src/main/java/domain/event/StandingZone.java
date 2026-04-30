package domain.event;

import DTO.StandingZoneDTO;
import domain.dataType.ElementPosition;
import domain.dataType.TicketStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class StandingZone extends Zone {
    private final int capacity;
    private final Queue<StandingTicket> availableTickets = new LinkedList<>(); //todo think about synchronization
    private AtomicInteger ticketIdGenerator;
    private final List<StandingTicket> occupiedTickets = new LinkedList<>();
    private int available; // todo AtomicInteger


    public StandingZone(String name, double price, int capacity, ElementPosition elementPosition, AtomicInteger ticketIdGenerator) {
        super(name,price,elementPosition);
        this.capacity = capacity;
        this.ticketIdGenerator = ticketIdGenerator;
        for(int i=0;i<capacity;i++){
            this.availableTickets.add(new StandingTicket(ticketIdGenerator.getAndIncrement()));
        }
        available=capacity;
    }
    public StandingZone(StandingZone zone){
        super(zone.getName(),zone.getPrice(),zone.getElementPosition());
        this.capacity = zone.capacity;
        this.ticketIdGenerator = new AtomicInteger(zone.ticketIdGenerator.get());
        for (StandingTicket st : zone.availableTickets) {
            this.availableTickets.add(new StandingTicket(st));
        }
        for(StandingTicket st : zone.occupiedTickets) {
            this.occupiedTickets.add(new StandingTicket(st));
        }
        this.available = zone.available;
    }

    public StandingZone(StandingZoneDTO standingZoneDTO, AtomicInteger generator) {
        super(standingZoneDTO.getName(),standingZoneDTO.getPrice(),standingZoneDTO.getPosition());
        this.capacity = standingZoneDTO.getCapacty();
        this.ticketIdGenerator = generator;
        for(int i=0;i<capacity;i++){
            this.availableTickets.add(new StandingTicket(ticketIdGenerator.getAndIncrement()));
        }
        available=capacity;
    }
    public int getCapacity() {
        return capacity;
    }

    public int getAvaliable() {
        return available;
    }

    public List<Integer> bookTickets(int quantity) {
        if (quantity > available) {
            throw new IllegalArgumentException("Not enough tickets available in this zone.");
        }
        List<Integer> bookedTicketIds = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            StandingTicket ticket = availableTickets.poll(); //get a ticket from the available queue
            if (ticket == null) {
                throw new IllegalStateException("No more tickets available.");
            }
            int ticketId = ticket.getTicketId();
            ticket.setStatus(TicketStatus.LOCKED);
            bookedTicketIds.add(ticketId);
        }
        available -= quantity; //decrease the capacity by the number of booked tickets
        return bookedTicketIds;
    }

    public boolean TicketIsBooked(int ticketId) {
        for (StandingTicket ticket : occupiedTickets) {
            if (ticket.getTicketId() == ticketId&& ticket.getStatus() == TicketStatus.LOCKED) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void releaseTickets(List<Integer> ticketIds) {
        for (Integer ticketId : ticketIds) {
            for (StandingTicket ticket : occupiedTickets) {
                if (ticket.getTicketId() == ticketId) {
                    if (ticket.getStatus() == TicketStatus.LOCKED) {
                        ticket.setStatus(TicketStatus.AVAILABLE);
                        availableTickets.add(ticket); //add the released ticket back to the available queue
                        available++; //increase the capacity by one
                    }
                }
            }
        }
        for (Integer ticketId : ticketIds) {
            occupiedTickets.removeIf(ticket -> ticket.getTicketId() == ticketId);
        }
    }
}
