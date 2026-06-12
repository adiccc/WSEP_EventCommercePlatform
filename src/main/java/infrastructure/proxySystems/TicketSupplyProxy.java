package infrastructure.proxySystems;

import DTO.TicketSupplyRequestDTO;
import DTO.TicketSupplyResultDTO;
import application.ITicketSupply;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
@Component
@Profile("test")
public class TicketSupplyProxy implements ITicketSupply {

    @Override
    public TicketSupplyResultDTO issue(TicketSupplyRequestDTO request) {
//        if (request.getTickets().size() == 3) {
//            return new TicketSupplyResultDTO(false, List.of());
//        }

        List<String> issuedCodes = new ArrayList<>();
        int count = request.getPurchasedTickets().isEmpty() ?
                request.getTickets().size() : request.getPurchasedTickets().size();

        for (int i = 0; i < request.getTickets().size(); i++) {
            issuedCodes.add("ISSUED-CODE-" + (i + 1));
        }

        return new TicketSupplyResultDTO(true, issuedCodes);
    }
    @Override
    public boolean cancelTicket(String ticketCode) {
        return true;
    }

    @Override
    public boolean handshake() {
        return true;
    }
}