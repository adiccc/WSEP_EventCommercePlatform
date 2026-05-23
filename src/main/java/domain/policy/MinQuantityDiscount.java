package domain.policy;

public class MinQuantityDiscount extends DiscountElement {
    private final double percentage;
    private final int minQuantity;

    public MinQuantityDiscount(double percentage, int minQuantity) {
        if (percentage <= 0 || percentage > 100)
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        if (minQuantity <= 0)
            throw new IllegalArgumentException("Min quantity must be positive");
        this.percentage = percentage;
        this.minQuantity = minQuantity;
    }

    public MinQuantityDiscount(MinQuantityDiscount d) {
        this.percentage  = d.percentage;
        this.minQuantity = d.minQuantity;
    }

    @Override
    public double apply(double originalPrice, int quantity, String couponCode) {
        if (quantity < minQuantity) return originalPrice;
        return originalPrice * (1 - percentage / 100);
    }

    @Override
    public boolean isValid() {
        return percentage > 0 && percentage <= 100 && minQuantity > 0;
    }

    @Override
    public String describe() {
        return percentage + "% discount when buying at least " + minQuantity + " tickets";
    }

    @Override
    public boolean discountExists(Discount newDiscount) { return equals(newDiscount); }

    @Override
    public Discount copy() { return new MinQuantityDiscount(this); }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MinQuantityDiscount other)
            return Double.compare(percentage, other.percentage) == 0 && minQuantity == other.minQuantity;
        return false;
    }

    public double getPercentage() { return percentage; }
    public int getMinQuantity() { return minQuantity; }
}
