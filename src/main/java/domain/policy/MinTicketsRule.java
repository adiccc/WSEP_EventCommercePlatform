package domain.policy;

import DTO.UserDTO;
import jakarta.persistence.*;

@Entity
@Table(name = "min_tickets_rules")
@DiscriminatorValue("MIN_TICKETS")
public class MinTicketsRule extends PurchaseRule {

    @Column(name = "min_tickets", nullable = false)
    private int minTickets;

    protected MinTicketsRule() {}

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
