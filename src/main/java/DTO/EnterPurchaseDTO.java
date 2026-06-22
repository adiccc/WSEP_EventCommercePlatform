package DTO;

public class EnterPurchaseDTO {

    private final EventMapDTO eventMap;
    private final ActiveOrderDTO activeOrder;
    private final boolean existingOrder;
    private final boolean waitingInQueue;
    private final Integer queuePosition;
    private final String companyPurchasePolicy;
    private final String eventPurchasePolicy;

    public EnterPurchaseDTO(
            EventMapDTO eventMap,
            ActiveOrderDTO activeOrder,
            boolean existingOrder,
            boolean waitingInQueue,
            Integer queuePosition,
            String companyPurchasePolicy,
            String eventPurchasePolicy
    ) {
        this.eventMap = eventMap;
        this.activeOrder = activeOrder;
        this.existingOrder = existingOrder;
        this.waitingInQueue = waitingInQueue;
        this.queuePosition = queuePosition;
        this.companyPurchasePolicy = companyPurchasePolicy;
        this.eventPurchasePolicy = eventPurchasePolicy;
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

    public String getCompanyPurchasePolicy() {
        return companyPurchasePolicy;
    }

    public String getEventPurchasePolicy() {
        return eventPurchasePolicy;
    }
}