package domain.policy;

import domain.dto.UserDTO;

public class MinTicketsRule extends PurchaseRule {
    private int minTickets;

    public MinTicketsRule(int minTickets) {
        this.minTickets = minTickets;
    }

    public MinTicketsRule(MinTicketsRule rule) {
        this.minTickets = rule.minTickets;
    }

    @Override
    public boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent) {
        return quantity >= minTickets;
    }

    @Override
    public boolean isValid() {
        return minTickets > 0;
    }

    @Override
    public String describe() {
        return "Min " + minTickets + " tickets per purchase";
    }

    public int getMinTickets() { return minTickets; }

    @Override
    public Purchase copy() { return new MinTicketsRule(this); }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MinTicketsRule other) return this.minTickets == other.minTickets;
        return false;
    }
}
