package DTO;

import java.util.List;

public class CheckoutSuccessDTO {
    private final int orderId;
    private final List<String> ticketCodes;

    public CheckoutSuccessDTO(int orderId, List<String> ticketCodes) {
        this.orderId = orderId;
        this.ticketCodes = ticketCodes;
    }

    public int getOrderId() {
        return orderId;
    }

    public List<String> getTicketCodes() {
        return ticketCodes;
    }
}