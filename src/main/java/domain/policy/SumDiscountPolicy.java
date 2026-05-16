package domain.policy;

import java.util.List;

public class SumDiscountPolicy extends DiscountPolicy {

    public SumDiscountPolicy() {
        super();
    }

    private SumDiscountPolicy(List<Discount> discounts) {
        super(discounts);
    }

    @Override
    public double apply(double originalPrice, int quantity, String couponCode) {
        double totalReduction = 0;
        for (Discount d : discounts)
            totalReduction += originalPrice - d.apply(originalPrice, quantity, couponCode);
        return Math.max(0, originalPrice - totalReduction);
    }

    @Override
    protected String policyName() {
        return "Sum discount policy";
    }

    @Override
    public DiscountPolicy copyPolicy() {
        return new SumDiscountPolicy(copyDiscounts());
    }
}
