package domain.dto;

import domain.event.Order;
import domain.event.OrderStatus;

import java.util.ArrayList;
import java.util.List;

public class OrderDTO {
    private OrderStatus status;
    private int orderId;
    private int userId;
    private String eventId;
    private List<Integer> tickets;
    private double totalSum;
    String paymentConfirmationId;

    public OrderDTO(int orderId, int userId, String eventId, List<Integer> tickets,double totalSum, String paymentConfirmationId) {
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.tickets = tickets;
        this.status = OrderStatus.APPROVED;
        this.totalSum = totalSum;
        this.paymentConfirmationId = paymentConfirmationId;
    }

    public OrderDTO(Order order){
        this.orderId = order.getOrderId();
        this.userId = order.getUserId();
        this.eventId = order.getEventId();
        this.tickets = new ArrayList<>(order.getTickets());
        this.status = order.getStatus();
        this.totalSum = order.getTotalSum();
        this.paymentConfirmationId = order.getPaymentConfirmationId();

    }

}
