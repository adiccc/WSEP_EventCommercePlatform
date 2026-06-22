package domain.policy;

import DTO.UserDTO;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "or_purchase_policies")
@DiscriminatorValue("OR_POLICY")
public class OrPurchasePolicy extends PurchasePolicy {

    public OrPurchasePolicy() { super(); }

    private OrPurchasePolicy(List<Purchase> rules) { super(rules); }

    @Override
    public boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent) {
        if (rules.isEmpty()) return true;
        for (Purchase rule : rules)
            if (rule.isSatisfied(user, quantity, ticketsBoughtForEvent)) return true;
        return false;
    }

    @Override
    public PurchasePolicyType getPolicyType() { return PurchasePolicyType.OR; }

    @Override
    protected String policyName() { return "OR"; }

    @Override
    public PurchasePolicy copyPolicy() { return new OrPurchasePolicy(copyRules()); }
}
