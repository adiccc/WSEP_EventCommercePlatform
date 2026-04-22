package domain.policy;

import domain.dto.UserDTO;
import java.util.ArrayList;
import java.util.List;

public class PurchasePolicy implements Purcase {
    private List<Purcase> rules;

    public PurchasePolicy() {
        this.rules = new ArrayList<>();
    }

    public void addRule(Purcase rule) {
        rules.add(rule);
    }

    public void removeRule(Purcase rule) {
        rules.remove(rule);
    }

    @Override
    public boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent) {
        for (Purcase rule : rules) {
            if (!rule.isSatisfied(user, quantity, ticketsBoughtForEvent)) return false;
        }
        return true;
    }

    @Override
    public boolean isValid() {
        for (Purcase rule : rules) {
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

    public List<Purcase> getRules() {
        return new ArrayList<>(rules);
    }
}
