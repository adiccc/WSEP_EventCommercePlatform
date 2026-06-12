package DTO;

public class PaymentDetailsDTO {
    private String cardNumber;
    private String expirationDate;
    private String cvv;
    private String cardHolderId;
    private String cardHolderName;
    private int numberOfPayments;
    private String couponCode;

    public PaymentDetailsDTO(String cardNumber,
                             String expirationDate,
                             String cvv,
                             String cardHolderId,
                             String cardHolderName,
                             int numberOfPayments,
                             String couponCode) {
        this.cardNumber = cardNumber;
        this.expirationDate = expirationDate;
        this.cvv = cvv;
        this.cardHolderId = cardHolderId;
        this.cardHolderName = cardHolderName;
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
    public String getCardHolderName() {
        return cardHolderName;
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
    public String getMonth() {
        return (expirationDate != null && expirationDate.contains("/")) ? expirationDate.split("/")[0].trim() : "";
    }

    public String getYear() {
        if (expirationDate != null && expirationDate.contains("/")) {
            String year = expirationDate.split("/")[1].trim();
            return year.length() == 2 ? "20" + year : year;
        }
        return "";
    }
}