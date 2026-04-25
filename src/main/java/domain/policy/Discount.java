package domain.policy;

public interface Discount  {
    double apply(double originalPrice, int quantity, String couponCode);
    boolean isValid();
    String describe();
    void addDiscount(Discount discount);
    boolean discountExists(Discount newdiscount);
}