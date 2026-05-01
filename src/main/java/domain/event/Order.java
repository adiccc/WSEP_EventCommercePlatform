package domain.event;

import java.util.ArrayList;
import java.util.List;

public class Order {
    private OrderStatus status;
    private int orderId;
    private int userId;
    private Integer eventId;
    private List<Integer> tickets;
    private double totalSum;
    String paymentConfirmationId;


    public Order(int orderId, int userId, Integer eventId,
                 List<Integer> tickets, double totalSum,
                 String paymentConfirmationId) {
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = new ArrayList<>(tickets);
        this.status = OrderStatus.APPROVED;
        this.totalSum = totalSum;
        this.paymentConfirmationId = paymentConfirmationId;
    }
    // TODO: fix Order constructor to require totalSum and paymentConfirmationId
    public Order(int orderId, int userId, Integer eventId, List<Integer> tickets) {
        this(orderId, userId, eventId, tickets, 0.0,"TEMP_PAYMENT_CONFIRMATION_ID");
    }
    public int getOrderId() {
        return orderId;
    }
    public Order(Order order) {
        this.orderId = order.orderId;
        this.userId = order.userId;
        this.eventId = order.eventId;
        this.tickets=new ArrayList<>(order.tickets);
        this.status = order.status;
        this.totalSum = order.totalSum;
        this.paymentConfirmationId = order.paymentConfirmationId;
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

    public int getNumOfTickets() {
        return tickets.size();
    }

        public int getUserId() {
            return userId;
        }

        public Integer getEventId() {
            return eventId;
        }

        public List<Integer> getTickets() {
            return tickets;
        }
}