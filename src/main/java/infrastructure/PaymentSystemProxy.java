package infrastructure;

import DTO.PaymentDetailsDTO;
import application.IPaymentSystem;
import org.springframework.stereotype.Component;

@Component
public class PaymentSystemProxy implements IPaymentSystem {

    @Override
    public String pay(double amount, PaymentDetailsDTO paymentDetails) {
//        if ("38".equals(paymentDetails.getCardNumber())) {
//            return null;
//        }

        return "PAYMENT_CONFIRMATION_ID";
    }


    @Override
    public boolean refund(String paymentConfirmationId, double amount) {
        return true;
    }
}