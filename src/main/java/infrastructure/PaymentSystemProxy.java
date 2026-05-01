package infrastructure;

import DTO.PaymentDetailsDTO;
import application.IPaymentSystem;

public class PaymentSystemProxy implements IPaymentSystem {

    @Override
    public String pay(double amount, PaymentDetailsDTO paymentDetails) {
        // TODO: replace mock behavior with real external payment
        return "PAYMENT_CONFIRMATION_ID";
    }

    @Override
    public boolean refund(String paymentConfirmationId, double amount) {
        // TODO: replace mock behavior with real external payment
        return true;
    }
}