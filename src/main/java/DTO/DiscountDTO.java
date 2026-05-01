package DTO;

import domain.policy.Discount;

import java.time.LocalDate;
import java.util.List;

public class DiscountDTO {

    public enum Type { Policy, VISUAL, CODE_COUPON, LIMITED }

    private final Type type;
    private final double percentage;
    private final LocalDate endDate;
    private final String code;
    private final int minQuantity;
    private final List<Discount> discounts;

    // VisualDiscount
    public DiscountDTO(double percentage, LocalDate endDate) {
        this.type = Type.VISUAL;
        this.percentage = percentage;
        this.endDate = endDate;
        this.code = null;
        this.minQuantity = 0;
        this.discounts = null;
    }

    // CodeCoupun
    public DiscountDTO(String code, double percentage, LocalDate endDate) {
        this.type = Type.CODE_COUPON;
        this.percentage = percentage;
        this.endDate = endDate;
        this.code = code;
        this.minQuantity = 0;
        this.discounts = null;
    }

    // LimitedDiscount
    public DiscountDTO(double percentage, int minQuantity) {
        this.type = Type.LIMITED;
        this.percentage = percentage;
        this.endDate = null;
        this.code = null;
        this.minQuantity = minQuantity;
        this.discounts = null;
    }

    // DiscountPolicy (composite)
    public DiscountDTO(List<Discount> discounts) {
        this.type = Type.Policy;
        this.percentage = 0;
        this.endDate = null;
        this.code = null;
        this.minQuantity = 0;
        this.discounts = discounts;
    }

    public Type getType() { return type; }
    public double getPercentage() { return percentage; }
    public LocalDate getEndDate() { return endDate; }
    public String getCode() { return code; }
    public int getMinQuantity() { return minQuantity; }
    public List<Discount> getDiscounts() { return discounts; }
}
