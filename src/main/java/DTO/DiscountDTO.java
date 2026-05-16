package DTO;

import java.time.LocalDate;
import java.util.List;

public class DiscountDTO {

    public enum Type { SUM_POLICY, MAX_POLICY, VISUAL, CODE_COUPON, MIN_QUANTITY, MAX_QUANTITY, DATE_RANGE }

    private final Type type;
    private final double percentage;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String code;
    private final int quantity;
    private final List<DiscountDTO> discounts;

    private DiscountDTO(Type type, double percentage, LocalDate startDate, LocalDate endDate,
                        String code, int quantity, List<DiscountDTO> discounts) {
        this.type       = type;
        this.percentage = percentage;
        this.startDate  = startDate;
        this.endDate    = endDate;
        this.code       = code;
        this.quantity   = quantity;
        this.discounts  = discounts;
    }

    // VisualDiscount
    public DiscountDTO(double percentage, LocalDate endDate) {
        this(Type.VISUAL, percentage, null, endDate, null, 0, null);
    }

    // CodeCoupun
    public DiscountDTO(String code, double percentage, LocalDate endDate) {
        this(Type.CODE_COUPON, percentage, null, endDate, code, 0, null);
    }

    // MinQuantityDiscount or MaxQuantityDiscount
    public DiscountDTO(Type type, double percentage, int quantity) {
        this(type, percentage, null, null, null, quantity, null);
    }

    // DateRangeDiscount
    public DiscountDTO(double percentage, LocalDate startDate, LocalDate endDate) {
        this(Type.DATE_RANGE, percentage, startDate, endDate, null, 0, null);
    }

    // SumDiscountPolicy
    public DiscountDTO(List<DiscountDTO> discounts) {
        this(Type.SUM_POLICY, 0, null, null, null, 0, discounts);
    }

    // MaxDiscountPolicy
    public DiscountDTO(List<DiscountDTO> discounts, boolean isMax) {
        this(Type.MAX_POLICY, 0, null, null, null, 0, discounts);
    }

    public Type getType() { return type; }
    public double getPercentage() { return percentage; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getCode() { return code; }
    public int getQuantity() { return quantity; }
    public List<DiscountDTO> getDiscounts() { return discounts; }
}
