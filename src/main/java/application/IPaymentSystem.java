package application;

public interface IPaymentSystem {
    boolean refund(String paymentConfirmationId, double amount);}