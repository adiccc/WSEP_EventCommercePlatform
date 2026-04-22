package domain.policy;

import domain.dto.UserDTO;

public interface Purcase {
    boolean isSatisfied(UserDTO user, int quantity, int eventId);
    boolean isValid();
    String describe();
}
