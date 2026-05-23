package domain.policy;

import java.time.LocalDate;

public class VisualDiscount extends DiscountElement {
    private double percentage;
    private LocalDate endDate;

    public VisualDiscount(double percentage, LocalDate endDate) {
        this.percentage = percentage;
        this.endDate = endDate;
    }
    public VisualDiscount(VisualDiscount visualDiscount) {
        this.percentage = visualDiscount.getPercentage();
        this.endDate = visualDiscount.getEndDate();
    }

    @Override
    public double apply(double originalPrice, int quantity, String couponCode) {
        if (LocalDate.now().isAfter(endDate))
            return originalPrice;
        return originalPrice * (1 - percentage / 100);
    }

    @Override
    public boolean isValid() {
        return percentage > 0 && percentage <= 100 && endDate != null;
    }

    @Override
    public String describe() {
        return percentage + "% discount until " + endDate;
    }

    public boolean discountExists(Discount newDiscount) {
        return equals(newDiscount);
    }

    @Override
    public Discount copy() {
        return new VisualDiscount(this);
    }

    public boolean equals(Object discount) {
        if (discount instanceof VisualDiscount) {
            VisualDiscount other = (VisualDiscount) discount;
            return this.percentage == other.percentage &&
                    this.endDate.equals(other.endDate);
        }
        return false;
    }
    public double getPercentage() { return percentage; }
    public LocalDate getEndDate() { return endDate; }
}
