package UI.Presenters;

import DTO.EnterPurchaseDTO;
import application.ActiveOrderService;
import application.Response;
import domain.dto.EventMapDTO;
import domain.dto.SeatingTicketDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PurchasePresenter {

    private final ActiveOrderService activeOrderService;

    public PurchasePresenter(ActiveOrderService activeOrderService) {
        this.activeOrderService = activeOrderService;
    }

    public Response<EnterPurchaseDTO> enterPurchase(
            String token,
            int companyId,
            int eventId
    ) {
        return activeOrderService.enterEventPurchase(
                token,
                companyId,
                eventId
        );
    }

    public Response<Integer> selectTickets(
            String token,
            Integer eventId,
            Map<String, List<SeatingTicketDTO>> seatingZones,
            Map<String, Integer> standingZones
    ) {
        return activeOrderService.userSelectTickets(
                token,
                eventId,
                seatingZones,
                standingZones
        );
    }

    public Map<String, List<SeatingTicketDTO>> createEmptySeatSelection() {
        return new HashMap<>();
    }

    public Map<String, Integer> createEmptyStandingSelection() {
        return new HashMap<>();
    }
}