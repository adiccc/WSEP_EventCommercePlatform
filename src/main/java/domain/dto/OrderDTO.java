package domain.dto;

import domain.event.Order;
import domain.event.OrderStatus;

import java.util.ArrayList;
import java.util.List;

public class OrderDTO {
    private OrderStatus status;
    private int orderId;
    private String userIdentifier;
    private Integer eventId;
    private List<Integer> tickets;
    private double totalSum;
    String paymentConfirmationId;

    public OrderDTO(int orderId, String userIdentifier, Integer eventId, List<Integer> tickets,double totalSum, String paymentConfirmationId) {
        this.orderId = orderId;
        this.userIdentifier = userIdentifier;
        this.eventId = eventId;
        this.tickets = tickets;
        this.status = OrderStatus.APPROVED;
        this.totalSum = totalSum;
        this.paymentConfirmationId = paymentConfirmationId;
    }

    public OrderDTO(Order order){
        this.orderId = order.getOrderId();
        this.userIdentifier = order.getUserIdentifier();
        this.eventId = order.getEventId();
        this.tickets = new ArrayList<>(order.getTickets());
        this.status = order.getStatus();
        this.totalSum = order.getTotalSum();
        this.paymentConfirmationId = order.getPaymentConfirmationId();

    }
    public String getUserIdentifier() {
        return userIdentifier;
    }

    public int getOrderId() {
        return orderId;
    }
}
