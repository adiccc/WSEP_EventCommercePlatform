package domain.policy;

import domain.dto.UserDTO;

import java.util.List;

public class AndPurchasePolicy extends PurchasePolicy {
    public AndPurchasePolicy() { super(); }
    private AndPurchasePolicy(List<Purchase> rules) { super(rules); }

    @Override
    public boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent) {
        for (Purchase rule : rules) {
            if (!rule.isSatisfied(user, quantity, ticketsBoughtForEvent)) return false;
        }
        return true;
    }

    @Override
    public PurchasePolicyType getPolicyType() { return PurchasePolicyType.AND; }

    @Override
    protected String policyName() { return "AND"; }

    @Override
    public PurchasePolicy copyPolicy() { return new AndPurchasePolicy(copyRules()); }
}
