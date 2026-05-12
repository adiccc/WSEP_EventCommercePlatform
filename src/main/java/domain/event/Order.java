package domain.event;

import java.util.ArrayList;
import java.util.List;

public class Order {
    private OrderStatus status;
    private int orderId;
    private String userIdentifier; //for member will be an email, and for guest will be the token when purchased
    private Integer eventId;
    private List<Integer> tickets;
    private double totalSum;
    String paymentConfirmationId;


    public Order(int orderId, String userIdentifier, Integer eventId,
                 List<Integer> tickets, double totalSum,
                 String paymentConfirmationId) {
        this.orderId = orderId;
        this.userIdentifier = userIdentifier;
        this.eventId = eventId;
        this.tickets = new ArrayList<>(tickets);
        this.status = OrderStatus.APPROVED;
        this.totalSum = totalSum;
        this.paymentConfirmationId = paymentConfirmationId;
    }

    public int getOrderId() {
        return orderId;
    }
    public Order(Order order) {
        this.orderId = order.orderId;
        this.userIdentifier = order.userIdentifier;
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

    public String getUserIdentifier() {
        return userIdentifier;
    }

        public Integer getEventId() {
            return eventId;
        }

        public List<Integer> getTickets() {
            return tickets;
        }
}