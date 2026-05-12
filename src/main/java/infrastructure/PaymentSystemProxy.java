package infrastructure;

import DTO.PaymentDetailsDTO;
import application.IPaymentSystem;

public class PaymentSystemProxy implements IPaymentSystem {

    @Override
    public String pay(double amount, PaymentDetailsDTO paymentDetails) {
        return "PAYMENT_CONFIRMATION_ID";
    }

    @Override
    public boolean refund(String paymentConfirmationId, double amount) {
        return true;
    }
}