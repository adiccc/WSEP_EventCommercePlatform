package domain.policy;

import domain.dto.UserDTO;

public interface Purchase {
    boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent);
    boolean isValid();
    String describe();
    void addRule(Purchase rule);
    boolean ruleExists(Purchase rule);
}
