package domain.policy;

import domain.dto.UserDTO;
import jakarta.persistence.*;

@Entity
@Table(name = "purchase_nodes")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private int version;

    protected Purchase() {}

    public Long getId() { return id; }

    public abstract boolean isSatisfied(UserDTO user, int quantity, int ticketsBoughtForEvent);
    public abstract boolean isValid();
    public abstract String describe();
    public abstract void addRule(Purchase rule);
    public abstract boolean ruleExists(Purchase rule);
    public abstract Purchase copy();
}
