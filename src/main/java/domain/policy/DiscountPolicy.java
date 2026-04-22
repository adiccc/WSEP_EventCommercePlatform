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

    public void removeDiscount(Discount discount) {
        discounts.remove(discount);
    }

    @Override
    public double apply(double originalPrice, int quantity, String couponCode) {
        double price = originalPrice;
        for (Discount discount : discounts) {
            price = discount.apply(price, quantity, couponCode);
        }
        return price;
    }

    @Override
    public boolean isValid() {
        for (Discount discount : discounts) {
            if (!discount.isValid()) return false;
        }
        return true;
    }

    @Override
    public String describe() {
        if (discounts.isEmpty()) return "No discounts";
        StringBuilder sb = new StringBuilder("Discount policy: ");
        for (int i = 0; i < discounts.size(); i++) {
            sb.append(discounts.get(i).describe());
            if (i < discounts.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    public List<Discount> getDiscounts() {
        return new ArrayList<>(discounts);
    }
}
