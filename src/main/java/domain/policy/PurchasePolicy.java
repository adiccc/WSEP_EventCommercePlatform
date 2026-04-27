package domain.policy;

import domain.dto.UserDTO;
import java.util.ArrayList;
import java.util.List;

public class PurchasePolicy implements Purchase {
    private List<Purchase> rules;

    public PurchasePolicy() {
        this.rules = new ArrayList<>();
    }
    public PurchasePolicy(PurchasePolicy purchasePolicy) {
        this.rules=new ArrayList<>();
        for (Purchase purchase : purchasePolicy.getRules()) {
            if(purchase instanceof PurchasePolicy) {
                this.rules.add(new PurchasePolicy((PurchasePolicy) purchase));
            } if(purchase instanceof MinAgeRule) {
                this.rules.add(new MinAgeRule((MinAgeRule)purchase));
            } if(purchase instanceof MaxTicketsRule){
                this.rules.add(new MaxTicketsRule((MaxTicketsRule)purchase));
            }
        }
    }

    public void addRule(Purchase rule) {
        rules.add(rule);
    }

    public void removeRule(Purchase rule) {
        rules.remove(rule);
    }

    @Override
    public boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent) {
        for (Purchase rule : rules) {
            if (!rule.isSatisfied(user, quantity, ticketsBoughtForEvent)) return false;
        }
        return true;
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
        StringBuilder sb = new StringBuilder("Purchase policy: ");
        for (int i = 0; i < rules.size(); i++) {
            sb.append(rules.get(i).describe());
            if (i < rules.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    public List<Purchase> getRules() {
        return new ArrayList<>(rules);
    }
}
