package domain.policy;

public abstract class PurchaseRule extends PurchaseNode {

    protected PurchaseRule() {}

    @Override
    public void addRule(Purchase rule) {
        throw new UnsupportedOperationException("Cannot add rule to a leaf rule");
    }

    @Override
    public boolean ruleExists(Purchase rule) {
        return equals(rule);
    }

    @Override
    public abstract Purchase copy();
}
