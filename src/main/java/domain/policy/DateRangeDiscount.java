package domain.policy;

import java.time.LocalDateTime;

public class DateRangeDiscount extends DiscountElement {
    private final double percentage;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;

    public DateRangeDiscount(double percentage, LocalDateTime startDate, LocalDateTime endDate) {
        if (percentage <= 0 || percentage > 100)
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        if (startDate == null || endDate == null)
            throw new IllegalArgumentException("Dates cannot be null");
        if (endDate.isBefore(startDate))
            throw new IllegalArgumentException("End date must not be before start date");
        this.percentage = percentage;
        this.startDate  = startDate;
        this.endDate    = endDate;
    }

    public DateRangeDiscount(DateRangeDiscount d) {
        this.percentage = d.percentage;
        this.startDate  = d.startDate;
        this.endDate    = d.endDate;
    }

    @Override
    public double apply(double originalPrice, int quantity, String couponCode) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startDate) || now.isAfter(endDate)) return originalPrice;
        return originalPrice * (1 - percentage / 100);
    }

    @Override
    public boolean isValid() {
        return percentage > 0 && percentage <= 100
                && startDate != null && endDate != null
                && !endDate.isBefore(startDate);
    }

    @Override
    public String describe() {
        return percentage + "% discount between " + startDate + " and " + endDate;
    }

    @Override
    public boolean discountExists(Discount newDiscount) { return equals(newDiscount); }

    @Override
    public Discount copy() { return new DateRangeDiscount(this); }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DateRangeDiscount other)
            return Double.compare(percentage, other.percentage) == 0
                    && startDate.equals(other.startDate)
                    && endDate.equals(other.endDate);
        return false;
    }

    public double getPercentage() { return percentage; }
    public LocalDateTime getStartDate() { return startDate; }
    public LocalDateTime getEndDate() { return endDate; }
}
