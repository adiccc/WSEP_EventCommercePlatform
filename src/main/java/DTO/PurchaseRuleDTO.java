package DTO;

public class PurchaseRuleDTO {

    public enum Type { MIN_AGE, MAX_TICKETS }

    private final Type type;
    private final int minAge;
    private final int maxTickets;

    // MinAgeRule
    public PurchaseRuleDTO(Type type, int value) {
        this.type = type;
        if (type == Type.MIN_AGE) {
            this.minAge = value;
            this.maxTickets = 0;
        } else {
            this.maxTickets = value;
            this.minAge = 0;
        }
    }

    public Type getType() { return type; }
    public int getMinAge() { return minAge; }
    public int getMaxTickets() { return maxTickets; }
}
