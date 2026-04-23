package domain.policy;

public abstract class PurchaseRule implements Purcase {
    @Override
    public void addRule(Purcase rule) {
        throw new UnsupportedOperationException("Cannot add rule to a leaf rule");
    }
}
