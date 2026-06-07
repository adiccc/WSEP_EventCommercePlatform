package domain.policy;

import domain.dto.UserDTO;
import jakarta.persistence.*;

@Entity
@Table(name = "max_tickets_rules")
@DiscriminatorValue("MAX_TICKETS")
public class MaxTicketsRule extends PurchaseRule {

    @Column(name = "max_tickets", nullable = false)
    private int maxTickets;

    protected MaxTicketsRule() {}

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
    public Purchase copy() { return new MaxTicketsRule(this); }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MaxTicketsRule other) return this.maxTickets == other.maxTickets;
        return false;
    }
}
