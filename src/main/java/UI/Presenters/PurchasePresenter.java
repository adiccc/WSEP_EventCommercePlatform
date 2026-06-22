package UI.Presenters;

import DTO.EnterPurchaseDTO;
import application.ActiveOrderService;
import application.Response;
import DTO.ActiveOrderDTO;
import DTO.ActiveOrderSelectionDTO;
import DTO.SeatingTicketDTO;

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
            int eventId,
            String code
    ) {
        return activeOrderService.enterEventPurchase(
                token,
                companyId,
                eventId,
                code
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

    public Response<Boolean> isRequiredLotteryCode(String token, int companyId, int eventId) {
        return activeOrderService.isRequiredLotteryCode(token, companyId, eventId);
    }

    public Response<Boolean> validateLotteryCode(
            String token,
            int companyId,
            int eventId,
            String code
    ) {
        return activeOrderService.validateLotteryCode(token, companyId, eventId, code);
    }

    public Response<ActiveOrderDTO> editTicketSelection(
            String token,
            Map<String, List<SeatingTicketDTO>> seatingToRemove,
            Map<String, List<SeatingTicketDTO>> seatingToAdd,
            Map<String, Integer> standingDesired
    ) {
        return activeOrderService.editTicketSelection(
                token,
                seatingToRemove,
                seatingToAdd,
                standingDesired
        );
    }

    public Response<ActiveOrderSelectionDTO> getCurrentActiveOrderSelection(String token) {
        return activeOrderService.getCurrentActiveOrderSelection(token);
    }

    public Response<Long> getCheckoutRemainingSeconds(String token, int activeOrderId) {
        return activeOrderService.getCheckoutRemainingSeconds(token, activeOrderId);
    }
}