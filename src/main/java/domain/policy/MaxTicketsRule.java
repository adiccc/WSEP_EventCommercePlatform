package domain.policy;

import domain.dto.UserDTO;

public class MaxTicketsRule extends PurchaseRule {
    private int maxTickets;

    public MaxTicketsRule(int maxTickets) {
        this.maxTickets = maxTickets;
    }

    public MaxTicketsRule(MaxTicketsRule rule) {
        this.maxTickets = rule.maxTickets;
    }
    @Override
    public boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent) {
        return ticketsBoughtForEvent + quantity <= maxTickets;
    }

    @Override
    public boolean isValid() {
        return maxTickets > 0;
    }

    @Override
    public String describe() {
        return "Max " + maxTickets + " tickets per buyer per event";
    }

    public int getMaxTickets() { return maxTickets; }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MaxTicketsRule other) return this.maxTickets == other.maxTickets;
        return false;
    }
}
