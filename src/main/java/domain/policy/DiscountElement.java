package domain.policy;

public abstract class DiscountElement extends Discount {

    protected DiscountElement() {}

    @Override
    public void addDiscount(Discount discount) {
        throw new UnsupportedOperationException("Cannot add discount to a leaf discount");
    }
}
