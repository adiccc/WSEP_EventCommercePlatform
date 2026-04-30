package DTO;

import java.util.ArrayList;
import java.util.List;

public class TicketSupplyRequestDTO {
    private final List<Integer> tickets;

    public TicketSupplyRequestDTO(List<Integer> tickets) {
        this.tickets = tickets == null ? new ArrayList<>() : new ArrayList<>(tickets);
    }

    public List<Integer> getTickets() {
        return new ArrayList<>(tickets);
    }
}