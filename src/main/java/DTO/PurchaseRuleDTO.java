package DTO;

import java.util.List;

public class PurchaseRuleDTO {

    public enum Type { AND_POLICY, OR_POLICY, MIN_AGE, MAX_TICKETS, MIN_TICKETS }

    private final Type type;
    private final int minAge;
    private final int maxTickets;
    private final int minTickets;
    private final List<PurchaseRuleDTO> purchases;

    public PurchaseRuleDTO(Type type, int value) {
        this.type       = type;
        this.purchases  = null;
        this.minAge     = (type == Type.MIN_AGE)     ? value : 0;
        this.maxTickets = (type == Type.MAX_TICKETS)  ? value : 0;
        this.minTickets = (type == Type.MIN_TICKETS)  ? value : 0;
    }

    public PurchaseRuleDTO(List<PurchaseRuleDTO> purchases, boolean isOr) {
        this.type       = isOr ? Type.OR_POLICY : Type.AND_POLICY;
        this.minAge     = 0;
        this.maxTickets = 0;
        this.minTickets = 0;
        this.purchases  = purchases;
    }

    public Type getType() { return type; }
    public int getMinAge() { return minAge; }
    public int getMaxTickets() { return maxTickets; }
    public int getMinTickets() { return minTickets; }
    public List<PurchaseRuleDTO> getPurchases() { return purchases; }
}
