package domain.policy;

import DTO.UserDTO;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "and_purchase_policies")
@DiscriminatorValue("AND_POLICY")
public class AndPurchasePolicy extends PurchasePolicy {

    public AndPurchasePolicy() { super(); }

    private AndPurchasePolicy(List<Purchase> rules) { super(rules); }

    @Override
    public boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent) {
        for (Purchase rule : rules)
            if (!rule.isSatisfied(user, quantity, ticketsBoughtForEvent)) return false;
        return true;
    }

    @Override
    public PurchasePolicyType getPolicyType() { return PurchasePolicyType.AND; }

    @Override
    protected String policyName() { return "AND"; }

    @Override
    public PurchasePolicy copyPolicy() { return new AndPurchasePolicy(copyRules()); }
}
