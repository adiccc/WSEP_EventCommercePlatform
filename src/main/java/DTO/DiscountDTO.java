package DTO;

import java.time.LocalDate;

public class DiscountDTO {

    public enum Type { VISUAL, CODE_COUPON, LIMITED }

    private final Type type;
    private final double percentage;
    private final LocalDate endDate;
    private final String code;
    private final int minQuantity;

    // VisualDiscount
    public DiscountDTO(double percentage, LocalDate endDate) {
        this.type = Type.VISUAL;
        this.percentage = percentage;
        this.endDate = endDate;
        this.code = null;
        this.minQuantity = 0;
    }

    // CodeCoupun
    public DiscountDTO(String code, double percentage, LocalDate endDate) {
        this.type = Type.CODE_COUPON;
        this.percentage = percentage;
        this.endDate = endDate;
        this.code = code;
        this.minQuantity = 0;
    }

    // LimitedDiscount
    public DiscountDTO(double percentage, int minQuantity) {
        this.type = Type.LIMITED;
        this.percentage = percentage;
        this.endDate = null;
        this.code = null;
        this.minQuantity = minQuantity;
    }

    public Type getType() { return type; }
    public double getPercentage() { return percentage; }
    public LocalDate getEndDate() { return endDate; }
    public String getCode() { return code; }
    public int getMinQuantity() { return minQuantity; }
}
