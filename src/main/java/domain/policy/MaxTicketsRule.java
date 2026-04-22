package domain.policy;

import domain.dto.UserDTO;

public class MaxTicketsRule extends PurchaseRule {
    private int maxTickets;

    public MaxTicketsRule(int maxTickets) {
        this.maxTickets = maxTickets;
    }

    @Override
    public boolean isSatisfied(UserDTO user, int quantity, int eventId) {
        return user.getTicketsBoughtForEvent(eventId) + quantity <= maxTickets;
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
}
