package DTO;

import domain.dto.ActiveOrderDTO;
import domain.dto.EventMapDTO;

public class EnterPurchaseDTO {

    private final EventMapDTO eventMap;
    private final ActiveOrderDTO activeOrder;
    private final boolean existingOrder;
    private final boolean waitingInQueue;
    private final Integer queuePosition;

     public EnterPurchaseDTO(EventMapDTO eventMap, ActiveOrderDTO activeOrder, boolean existingOrder, boolean waitingInQueue, Integer queuePosition) {
        this.eventMap = eventMap;
        this.activeOrder = activeOrder;
        this.existingOrder = existingOrder;
        this.waitingInQueue = waitingInQueue;
        this.queuePosition = queuePosition;
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

    public boolean isWaitingInQueue() {
        return waitingInQueue;
    }

    public Integer getQueuePosition() {
        return queuePosition;
    }
}