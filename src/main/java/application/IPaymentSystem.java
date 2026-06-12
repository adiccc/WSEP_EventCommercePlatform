package application;

import DTO.PaymentDetailsDTO;

public interface IPaymentSystem {
    String pay(double amount, PaymentDetailsDTO paymentDetails);
    boolean refund(String paymentConfirmationId, double amount);
    boolean handshake();
}

