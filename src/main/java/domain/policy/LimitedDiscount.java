package domain.policy;

public class LimitedDiscount extends DiscountElement {
    private double percentage;
    private int minQuantity;

    public LimitedDiscount(double percentage, int minQuantity) {
        this.percentage = percentage;
        this.minQuantity = minQuantity;
    }
    public LimitedDiscount(LimitedDiscount discount) {
        this.percentage = discount.percentage;
        this.minQuantity = discount.minQuantity;
    }

    @Override
    public double apply(double originalPrice, int quantity, String couponCode) {
        if (quantity < minQuantity + 1) return originalPrice;
        double pricePerTicket = originalPrice / quantity;
        int groups = quantity / (minQuantity + 1);
        double savings = groups * pricePerTicket * (percentage / 100);
        return originalPrice - savings;
    }

    @Override
    public boolean isValid() {
        return percentage > 0 && percentage <= 100 && minQuantity > 0;
    }

    @Override
    public String describe() {
        return "Buy " + minQuantity + " get the next " + percentage + "% off";
    }

    public boolean discountExists(Discount newdiscount) {
        return equals(newdiscount);
    }

    public boolean equals(Object discount) {
        if (discount instanceof LimitedDiscount) {
            LimitedDiscount other = (LimitedDiscount) discount;
            return other.percentage == percentage && other.minQuantity == minQuantity;
        }
        return false;
    }

    public double getPercentage() { return percentage; }
    public int getMinQuantity() { return minQuantity; }
}