package domain.policy;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "code_coupons")
@DiscriminatorValue("CODE_COUPON")
public class CodeCoupun extends DiscountElement {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "percentage", nullable = false)
    private double percentage;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    protected CodeCoupun() {}

    public CodeCoupun(String code, double percentage, LocalDate endDate) {
        this.code = code;
        this.percentage = percentage;
        this.endDate = endDate;
    }

    public CodeCoupun(CodeCoupun codeCoupun) {
        this.code = codeCoupun.code;
        this.percentage = codeCoupun.percentage;
        this.endDate = codeCoupun.endDate;
    }

    @Override
    public double apply(double originalPrice, int quantity, String couponCode) {
        if (code.equals(couponCode) && !LocalDate.now().isAfter(endDate))
            return originalPrice * (1 - percentage / 100);
        return originalPrice;
    }

    @Override
    public boolean isValid() {
        return code != null && !code.isEmpty()
                && percentage > 0 && percentage <= 100
                && endDate != null;
    }

    @Override
    public String describe() {
        return percentage + "% discount with coupon code: "+ code +", valid until " + endDate;
    }

    @Override
    public boolean discountExists(Discount newdiscount) {
        return equals(newdiscount);
    }

    @Override
    public Discount copy() {
        return new CodeCoupun(this);
    }

    @Override
    public boolean equals(Object discount) {
        if (discount instanceof CodeCoupun)
            return code.equals(((CodeCoupun) discount).code);
        return false;
    }

    public String getCode() { return code; }
    public double getPercentage() { return percentage; }
    public LocalDate getEndDate() { return endDate; }
}
