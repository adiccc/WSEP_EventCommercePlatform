package domain.policy;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "visual_discounts")
@DiscriminatorValue("VISUAL")
public class VisualDiscount extends DiscountElement {

    @Column(name = "percentage", nullable = false)
    private double percentage;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    protected VisualDiscount() {}

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

    @Override
    public boolean discountExists(Discount newDiscount) {
        return equals(newDiscount);
    }

    @Override
    public Discount copy() {
        return new VisualDiscount(this);
    }

    @Override
    public boolean equals(Object discount) {
        if (discount instanceof VisualDiscount other)
            return this.percentage == other.percentage && this.endDate.equals(other.endDate);
        return false;
    }

    public double getPercentage() { return percentage; }
    public LocalDate getEndDate() { return endDate; }
}
