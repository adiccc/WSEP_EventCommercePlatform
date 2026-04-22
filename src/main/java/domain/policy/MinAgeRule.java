package domain.policy;

import domain.dto.UserDTO;

public class MinAgeRule extends PurchaseRule {
    private int minAge;

    public MinAgeRule(int minAge) {
        this.minAge = minAge;
    }

    @Override
    public boolean isSatisfied(UserDTO user, int quantity, int eventId) {
        return user.getAge() >= minAge;
    }

    @Override
    public boolean isValid() {
        return minAge >= 0;
    }

    @Override
    public String describe() {
        return "Minimum age: " + minAge;
    }

    public int getMinAge() { return minAge; }
}
