package domain.policy;

import java.util.List;

public class MaxDiscountPolicy extends DiscountPolicy {

    public MaxDiscountPolicy() {
        super();
    }

    private MaxDiscountPolicy(List<Discount> discounts) {
        super(discounts);
    }

    @Override
    public double apply(double originalPrice, int quantity, String couponCode) {
        double bestPrice = originalPrice;
        for (Discount d : discounts) {
            double result = d.apply(originalPrice, quantity, couponCode);
            if (result < bestPrice) bestPrice = result;
        }
        return bestPrice;
    }

    @Override
    protected String policyName() {
        return "Max discount policy";
    }

    @Override
    public DiscountPolicy copyPolicy() {
        return new MaxDiscountPolicy(copyDiscounts());
    }
}
