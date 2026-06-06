package domain.policy;

import domain.dto.UserDTO;
import jakarta.persistence.*;

@Entity
@Table(name = "min_age_rules")
@DiscriminatorValue("MIN_AGE")
public class MinAgeRule extends PurchaseRule {

    @Column(name = "min_age", nullable = false)
    private int minAge;

    protected MinAgeRule() {}

    public MinAgeRule(int minAge) {
        this.minAge = minAge;
    }

    public MinAgeRule(MinAgeRule rule) {
        this.minAge = rule.minAge;
    }

    @Override
    public boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent) {
        if (user == null) return true;
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
