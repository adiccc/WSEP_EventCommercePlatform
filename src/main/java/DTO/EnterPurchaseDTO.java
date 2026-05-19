package DTO;

import domain.dto.ActiveOrderDTO;
import domain.dto.EventMapDTO;

public class EnterPurchaseDTO {

    private final EventMapDTO eventMap;
    private final ActiveOrderDTO activeOrder;
    private final boolean existingOrder;

    public EnterPurchaseDTO(EventMapDTO eventMap, ActiveOrderDTO activeOrder, boolean existingOrder) {
        this.eventMap = eventMap;
        this.activeOrder = activeOrder;
        this.existingOrder = existingOrder;
    }

    public EventMapDTO getEventMap() {
        return eventMap;
    }

    public ActiveOrderDTO getActiveOrder() {
        return activeOrder;
    }

    public boolean isExistingOrder() {
        return existingOrder;
    }
}