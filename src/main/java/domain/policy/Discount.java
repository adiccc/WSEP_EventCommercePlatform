package domain.policy;

import jakarta.persistence.*;

@Entity
@Table(name = "discount_nodes")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class Discount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private int version;

    protected Discount() {}

    public Long getId() { return id; }

    public abstract double apply(double originalPrice, int quantity, String couponCode);
    public abstract boolean isValid();
    public abstract String describe();
    public abstract void addDiscount(Discount discount);
    public abstract boolean discountExists(Discount newdiscount);
    public abstract Discount copy();
}