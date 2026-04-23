package domain.policy;

public class LimitedDiscount implements Discount {
    private double percentage;
    private int minQuantity;

    public LimitedDiscount(double percentage, int minQuantity) {
        this.percentage = percentage;
        this.minQuantity = minQuantity;
    }

    public void addDiscount(Discount discount) {
        throw new UnsupportedOperationException("Cannot add discount to a leaf");
    }

}