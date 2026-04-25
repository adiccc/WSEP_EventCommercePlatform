package domain.policy;

public abstract class DiscountElement implements Discount {
    @Override
    public void addDiscount(Discount discount) {
        throw new UnsupportedOperationException("Cannot add discount to a leaf discount");
    }
}
