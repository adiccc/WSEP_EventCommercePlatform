package domain.policy;

import jakarta.persistence.*;

@Entity
@Table(name = "discount_nodes")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class DiscountNode implements Discount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private int version;

    protected DiscountNode() {}

    public Long getId() { return id; }
}
