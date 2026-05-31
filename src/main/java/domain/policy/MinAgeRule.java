package domain.policy;

import domain.dto.UserDTO;

public class MinAgeRule extends PurchaseRule {
    private int minAge;

    public MinAgeRule(int minAge) {
        this.minAge = minAge;
    }
    public MinAgeRule(MinAgeRule rule) {
        this.minAge = rule.minAge;
    }

    @Override
    public boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent) {
        //check in the ui
        if (user == null) {
            return true;
        }
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

    @Override
    public Purchase copy() { return new MinAgeRule(this); }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MinAgeRule other) return this.minAge == other.minAge;
        return false;
    }
}
