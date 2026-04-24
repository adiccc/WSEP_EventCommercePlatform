package domain.policy;

import java.util.ArrayList;
import java.util.List;

public class DiscountPolicy implements Discount {
    private List<Discount> discounts;

    public DiscountPolicy() {
        this.discounts = new ArrayList<>();
    }

    public void addDiscount(Discount discount) {
        discounts.add(discount);
    }
}
