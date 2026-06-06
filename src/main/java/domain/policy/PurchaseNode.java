package domain.policy;

import jakarta.persistence.*;

@Entity
@Table(name = "purchase_nodes")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class PurchaseNode implements Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private int version;

    protected PurchaseNode() {}

    public Long getId() { return id; }
}
