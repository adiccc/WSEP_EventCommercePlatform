package domain.policy;

import jakarta.persistence.*;

@Entity
@Table(name = "max_quantity_discounts")
@DiscriminatorValue("MAX_QUANTITY")
public class MaxQuantityDiscount extends DiscountElement {

    @Column(name = "percentage", nullable = false)
    private double percentage;

    @Column(name = "max_quantity", nullable = false)
    private int maxQuantity;

    protected MaxQuantityDiscount() {}

    public MaxQuantityDiscount(double percentage, int maxQuantity) {
        if (percentage <= 0 || percentage > 100)
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        if (maxQuantity <= 0)
            throw new IllegalArgumentException("Max quantity must be positive");
        this.percentage  = percentage;
        this.maxQuantity = maxQuantity;
    }

    public MaxQuantityDiscount(MaxQuantityDiscount d) {
        this.percentage  = d.percentage;
        this.maxQuantity = d.maxQuantity;
    }

    @Override
    public double apply(double originalPrice, int quantity, String couponCode) {
        if (quantity > maxQuantity) return originalPrice;
        return originalPrice * (1 - percentage / 100);
    }

    @Override
    public boolean isValid() {
        return percentage > 0 && percentage <= 100 && maxQuantity > 0;
    }

    @Override
    public String describe() {
        return percentage + "% discount when buying at most " + maxQuantity + " tickets";
    }

    @Override
    public boolean discountExists(Discount newDiscount) { return equals(newDiscount); }

    @Override
    public Discount copy() { return new MaxQuantityDiscount(this); }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MaxQuantityDiscount other)
            return Double.compare(percentage, other.percentage) == 0 && maxQuantity == other.maxQuantity;
        return false;
    }

    public double getPercentage() { return percentage; }
    public int getMaxQuantity() { return maxQuantity; }
}
