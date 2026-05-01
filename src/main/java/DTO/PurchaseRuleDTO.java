package DTO;

import domain.policy.Purchase;

import java.util.List;

public class PurchaseRuleDTO {

    public enum Type { PurchasePolicy, MIN_AGE, MAX_TICKETS }

    private final Type type;
    private final int minAge;
    private final int maxTickets;
    private final List<Purchase> purchases;

    // MinAgeRule or MaxTicketsRule
    public PurchaseRuleDTO(Type type, int value) {
        this.type = type;
        this.purchases = null;
        if (type == Type.MIN_AGE) {
            this.minAge = value;
            this.maxTickets = 0;
        } else {
            this.maxTickets = value;
            this.minAge = 0;
        }
    }

    // PurchasePolicy (composite)
    public PurchaseRuleDTO(List<Purchase> purchases) {
        this.type = Type.PurchasePolicy;
        this.minAge = 0;
        this.maxTickets = 0;
        this.purchases = purchases;
    }

    public Type getType() { return type; }
    public int getMinAge() { return minAge; }
    public int getMaxTickets() { return maxTickets; }
    public List<Purchase> getPurchases() { return purchases; }
}
