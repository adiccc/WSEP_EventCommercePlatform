package infrastructure;

import DTO.TicketSupplyRequestDTO;
import DTO.TicketSupplyResultDTO;
import application.ITicketSupply;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TicketSupplyProxy implements ITicketSupply {

    @Override
    public TicketSupplyResultDTO issue(TicketSupplyRequestDTO request) {
//        if (request.getTickets().size() == 3) {
//            return new TicketSupplyResultDTO(false, List.of());
//        }

        List<String> issuedCodes = new ArrayList<>();

        for (int i = 0; i < request.getTickets().size(); i++) {
            issuedCodes.add("ISSUED-CODE-" + (i + 1));
        }

        return new TicketSupplyResultDTO(true, issuedCodes);
    }
}