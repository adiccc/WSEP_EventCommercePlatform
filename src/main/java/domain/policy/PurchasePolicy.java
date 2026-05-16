package domain.policy;

import DTO.PurchaseRuleDTO;
import domain.dto.UserDTO;

import java.util.ArrayList;
import java.util.List;

public abstract class PurchasePolicy implements Purchase {
    protected List<Purchase> rules;

    protected PurchasePolicy() {
        this.rules = new ArrayList<>();
    }

    protected PurchasePolicy(List<Purchase> rules) {
        this.rules = new ArrayList<>(rules);
    }

    @Override
    public abstract boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent);

    protected abstract String policyName();

    public abstract PurchasePolicyType getPolicyType();

    public abstract PurchasePolicy copyPolicy();

    @Override
    public Purchase copy() { return copyPolicy(); }

    protected List<Purchase> copyRules() {
        List<Purchase> copied = new ArrayList<>();
        for (Purchase p : rules) copied.add(p.copy());
        return copied;
    }

    public static Purchase dtoToPurchase(PurchaseRuleDTO ruleDTO) {
        if (ruleDTO == null) return null;
        switch (ruleDTO.getType()) {
            case MIN_AGE:     return new MinAgeRule(ruleDTO.getMinAge());
            case MAX_TICKETS: return new MaxTicketsRule(ruleDTO.getMaxTickets());
            case MIN_TICKETS: return new MinTicketsRule(ruleDTO.getMinTickets());
            case AND_POLICY: {
                AndPurchasePolicy policy = new AndPurchasePolicy();
                if (ruleDTO.getPurchases() != null)
                    for (PurchaseRuleDTO child : ruleDTO.getPurchases())
                        policy.addRule(dtoToPurchase(child));
                return policy;
            }
            case OR_POLICY: {
                OrPurchasePolicy policy = new OrPurchasePolicy();
                if (ruleDTO.getPurchases() != null)
                    for (PurchaseRuleDTO child : ruleDTO.getPurchases())
                        policy.addRule(dtoToPurchase(child));
                return policy;
            }
            default: return null;
        }
    }

    public void addRule(Purchase rule) {
        if (!rule.isValid())
            throw new IllegalArgumentException("Invalid rule data");
        if (ruleExists(rule))
            throw new RuntimeException("Rule already exists");
        rules.add(rule);
    }

    public void removeRule(Purchase rule) {
        for (Purchase r : rules) {
            if (rule.equals(r)) {
                rules.remove(r);
                return;
            }
        }
        throw new RuntimeException("Rule not found");
    }

    @Override
    public boolean ruleExists(Purchase newRule) {
        for (Purchase rule : rules) {
            if (rule.ruleExists(newRule)) return true;
        }
        return false;
    }

    @Override
    public boolean isValid() {
        for (Purchase rule : rules) {
            if (!rule.isValid()) return false;
        }
        return true;
    }

    @Override
    public String describe() {
        if (rules.isEmpty()) return "No purchase restrictions";
        if (rules.size() == 1) return rules.get(0).describe();
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < rules.size(); i++) {
            sb.append(rules.get(i).describe());
            if (i < rules.size() - 1) sb.append(" ").append(policyName()).append(" ");
        }
        sb.append(")");
        return sb.toString();
    }

    public List<Purchase> getRules() {
        return new ArrayList<>(rules);
    }
}
