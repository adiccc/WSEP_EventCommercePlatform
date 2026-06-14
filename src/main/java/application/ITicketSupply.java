package application;

import DTO.TicketSupplyRequestDTO;
import DTO.TicketSupplyResultDTO;

public interface ITicketSupply {
    TicketSupplyResultDTO issue(TicketSupplyRequestDTO request);
    boolean cancelTicket(String ticketCode);
    boolean handshake();

}