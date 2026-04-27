package domain.event;

import java.util.List;

public class Order {
    private int orderId;
    private int userId;
    private String eventId;
    private List<Integer> tickets;
    private OrderStatus status;
    private double totalSum;
    private String paymentConfirmationId;

    public Order(int orderId, int userId, String eventId, List<Integer> tickets,double totalSum, String paymentConfirmationId) {
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = tickets;
        this.status = OrderStatus.APPROVED;
        this.totalSum = totalSum;
        this.paymentConfirmationId = paymentConfirmationId;
    }
    // TODO: fix Order constructor to require totalSum and paymentConfirmationId
    public Order(int orderId, int userId, String eventId, List<Integer> tickets) {
        this(orderId, userId, eventId, tickets, 0.0,"TEMP_PAYMENT_CONFIRMATION_ID");
    }
    public int getOrderId() {
        return orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public boolean canBeRefunded() {
        return status == OrderStatus.REFUND_REQUIRED;
    }

    public void markRefunded() {
        this.status = OrderStatus.REFUNDED;
    }

    public void markRefundRequired() {
        this.status = OrderStatus.REFUND_REQUIRED;
    }
    public double getTotalSum() {
        return totalSum;
    }
    public String getPaymentConfirmationId() {
        return paymentConfirmationId;
    }
}