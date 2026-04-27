package infrastructure;

import application.IPaymentSystem;

public class PaymentSystemProxy implements IPaymentSystem {

    @Override
    public boolean refund(String paymentConfirmationId, double amount) {
        // TODO: replace mock behavior with real external payment
        return true;
    }
}