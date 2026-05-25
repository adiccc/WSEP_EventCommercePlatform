package DTO;

public class PaymentDetailsDTO {
    private String cardNumber;
    private String expirationDate;
    private String cvv;
    private String cardHolderId;
    private int numberOfPayments;
    private String couponCode;

    public PaymentDetailsDTO(String cardNumber,
                             String expirationDate,
                             String cvv,
                             String cardHolderId,
                             int numberOfPayments,
                             String couponCode) {
        this.cardNumber = cardNumber;
        this.expirationDate = expirationDate;
        this.cvv = cvv;
        this.cardHolderId = cardHolderId;
        this.numberOfPayments = numberOfPayments;
        this.couponCode = normalizeCouponCode(couponCode);
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public String getCvv() {
        return cvv;
    }

    public String getCardHolderId() {
        return cardHolderId;
    }

    public int getNumberOfPayments() {
        return numberOfPayments;
    }

    public String getCouponCode() {
        return couponCode;
    }

    private String normalizeCouponCode(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return null;
        }

        return couponCode.trim();
    }
}