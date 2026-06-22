package DTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveOrderSelectionDTO {
    private final List<ActiveOrderSeatDTO> seats;
    private final Map<String, Integer> standingTicketsByZone;

    public ActiveOrderSelectionDTO(
            List<ActiveOrderSeatDTO> seats,
            Map<String, Integer> standingTicketsByZone
    ) {
        this.seats = seats;
        this.standingTicketsByZone = standingTicketsByZone == null
                ? new HashMap<>()
                : new HashMap<>(standingTicketsByZone);
    }

    public List<ActiveOrderSeatDTO> getSeats() {
        return seats;
    }

    public Map<String, Integer> getStandingTicketsByZone() {
        return standingTicketsByZone;
    }
}