package domain.company;

import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import java.util.HashSet;
import java.util.Set;

public class Company {
    private int companyId;
    private String name;
    private boolean active;
    private Set<Integer> ownerIds;
    private PurchasePolicy purchasePolicy;
    private DiscountPolicy discountPolicy;

    public Company(int companyId, String name, int founderId) {
        this.companyId = companyId;
        this.name = name;
        this.active = true;
        this.ownerIds = new HashSet<>();
        this.ownerIds.add(founderId);
        this.purchasePolicy = new PurchasePolicy();
        this.discountPolicy = new DiscountPolicy();
    }

    public void updatePurchasePolicy(int userId, PurchasePolicy newPolicy) {
        if (!active)
            throw new IllegalStateException("Company is not active");
        if (!ownerIds.contains(userId))
            throw new SecurityException("User does not have permission to update purchase policy");
        if (!newPolicy.isValid())
            throw new IllegalArgumentException("Invalid policy data");
        this.purchasePolicy = newPolicy;
    }

    public void updateDiscountPolicy(int userId, DiscountPolicy newPolicy) {
        if (!active)
            throw new IllegalStateException("Company is not active");
        if (!ownerIds.contains(userId))
            throw new SecurityException("User does not have permission to update discount policy");
        if (!newPolicy.isValid())
            throw new IllegalArgumentException("Invalid discount policy data");
        this.discountPolicy = newPolicy;
    }

    public void deactivate() { this.active = false; }

    public boolean isOwner(int userId) { return ownerIds.contains(userId); }
    public boolean isActive() { return active; }
    public int getCompanyId() { return companyId; }
    public String getName() { return name; }
    public PurchasePolicy getPurchasePolicy() { return purchasePolicy; }
    public DiscountPolicy getDiscountPolicy() { return discountPolicy; }
}
