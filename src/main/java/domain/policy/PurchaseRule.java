package domain.policy;

public abstract class PurchaseRule implements Purchase {
    @Override
    public void addRule(Purchase rule) {
        throw new UnsupportedOperationException("Cannot add rule to a leaf rule");
    }
}
