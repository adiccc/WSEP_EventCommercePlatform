package domain.policy;

import java.time.LocalDate;

public class CodeCoupun extends DiscountElement {
    private String code;
    private double percentage;
    private LocalDate endDate;

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
        return percentage + "% discount with coupon code, valid until " + endDate;
    }

    public boolean discountExists(Discount newdiscount) {
        return equals(newdiscount);
    }

    @Override
    public Discount copy() {
        return new CodeCoupun(this);
    }

    public boolean equals(Object discount) {
        if(discount instanceof CodeCoupun){
            if(code.equals(((CodeCoupun)discount).code))
                return true;
        }
        return false;
    }

    public String getCode() { return code; }
    public double getPercentage() { return percentage; }
    public LocalDate getEndDate() { return endDate; }
}
