package domain.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveOrderSelectionDTO {
    private final List<domain.dto.ActiveOrderSeatDTO> seats;
    private final Map<String, Integer> standingTicketsByZone;

    public ActiveOrderSelectionDTO(
            List<domain.dto.ActiveOrderSeatDTO> seats,
            Map<String, Integer> standingTicketsByZone
    ) {
        this.seats = seats;
        this.standingTicketsByZone = standingTicketsByZone == null
                ? new HashMap<>()
                : new HashMap<>(standingTicketsByZone);
    }

    public List<domain.dto.ActiveOrderSeatDTO> getSeats() {
        return seats;
    }

    public Map<String, Integer> getStandingTicketsByZone() {
        return standingTicketsByZone;
    }
}