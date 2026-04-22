package domain.company;

import domain.dataType.PermissionType;
import domain.policy.PurchasePolicy;
import java.util.HashSet;
import java.util.Set;

public class Company {
    private int companyId;
    private String name;
    private boolean active;
    private Set<Integer> ownerIds;
    private PurchasePolicy purchasePolicy;

    public Company(int companyId, String name, int founderId) {
        this.companyId = companyId;
        this.name = name;
        this.active = true;
        this.ownerIds = new HashSet<>();
        this.ownerIds.add(founderId);
        this.purchasePolicy = new PurchasePolicy();
    }

    public String updatePurchasePolicy(int userId, PurchasePolicy newPolicy) {
        if (!active)
            return "Company is not active";
        if (!ownerIds.contains(userId))
            return "User does not have permission to update purchase policy";
        if (!newPolicy.isValid())
            return "Invalid policy data";
        this.purchasePolicy = newPolicy;
        return null;
    }

    public void deactivate() { this.active = false; }

    public boolean isOwner(int userId) { return ownerIds.contains(userId); }
    public boolean isActive() { return active; }
    public int getCompanyId() { return companyId; }
    public String getName() { return name; }
    public PurchasePolicy getPurchasePolicy() { return purchasePolicy; }

    public boolean checkPermission(int userId, PermissionType permissionType) {
        // TODO: to implement
        return true;
    }
}
