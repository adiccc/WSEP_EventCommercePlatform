package domain.company;

import domain.activeOrder.ActiveOrder;
import domain.dataType.PermissionType;
import domain.dto.HierarchyDTO;
import domain.policy.Discount;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import java.util.Map;
import java.util.Set;

public class Company {
    private int companyId;
    private String companyName;
    private boolean isActive;
    private ContactInfo contactInfo;
    private PurchasePolicy purchasePolicy;
    private DiscountPolicy discountPolicy;
    private Permissions companyPermission;
    private long version;

    public Company(int companyId, String companyName, ContactInfo contactInfo,
                   PurchasePolicy defaultPurchase, DiscountPolicy defaultDiscount,
                   Permissions companyPermission) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.contactInfo = contactInfo;
        this.purchasePolicy = defaultPurchase;
        this.discountPolicy = defaultDiscount;
        this.companyPermission = companyPermission;
        this.isActive = true;
        this.version = 0;
    }

    /** Deep-copy constructor — used by the repo for defensive copying */
    public Company(Company company) {
        this.companyId = company.companyId;
        this.companyName = company.companyName;
        this.contactInfo = new ContactInfo(company.contactInfo);
        this.purchasePolicy = new PurchasePolicy(company.purchasePolicy);
        this.discountPolicy = new DiscountPolicy(company.discountPolicy);
        this.isActive = company.isActive;
        this.companyPermission = new Permissions(company.companyPermission);
        this.version = company.version;
    }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public void updatePurchasePolicy(int userId, PurchasePolicy newPolicy) {
        if (!isActive)
            throw new IllegalStateException("Company is not active");
        if (!companyPermission.isOwner(userId))
            throw new SecurityException("User does not have permission to update purchase policy");
        if (!newPolicy.isValid())
            throw new IllegalArgumentException("Invalid policy data");
        this.purchasePolicy = newPolicy;
    }

    public void updateDiscountPolicy(int userId, DiscountPolicy newPolicy) {
        if (!isActive)
            throw new IllegalStateException("Company is not active");
        if (!companyPermission.isOwner(userId))
            throw new SecurityException("User does not have permission to update discount policy");
        if (!newPolicy.isValid())
            throw new IllegalArgumentException("Invalid discount policy data");
        this.discountPolicy = newPolicy;
    }

    public void addDiscount(int userId, Discount discount) {
        if (!companyPermission.checkPermission(userId, PermissionType.MANAGE_POLICIES)) {
            throw new SecurityException("User does not have permission to add discount policy");
        }
        discountPolicy.addDiscount(discount);
    }

    public void removeDiscount(int userId, Discount discount) {
        if (!companyPermission.checkPermission(userId, PermissionType.MANAGE_POLICIES)) {
            throw new SecurityException("User does not have permission to remove discount policy");
        }
        discountPolicy.removeDiscount(discount);
    }

    public void deactivate() { this.isActive = false; }

    // --- Getters ---
    public int getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public boolean isActive() { return isActive; }
    public ContactInfo getContactInfo() { return contactInfo; }
    public PurchasePolicy getPurchasePolicy() { return purchasePolicy; }
    public DiscountPolicy getDiscountPolicy() { return discountPolicy; }
    public Permissions getCompanyPermission() { return companyPermission; }

    // --- Convenience delegates to Permissions ---
    public int getFounderId() { return companyPermission.getFounderId(); }
    public Set<Integer> getOwnerIds() { return companyPermission.getOwnerIds(); }
    public boolean isOwner(int userId) { return companyPermission.isOwner(userId); }
    public boolean checkPermission(int userId, PermissionType permission) {
        return companyPermission.checkPermission(userId, permission);
    }
    public Map<Integer, HierarchyDTO> getManagersPermissionsMap() {
        return companyPermission.getCompanyTree();
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Company other = (Company) obj;
        return companyId==other.companyId && version == other.getVersion();
    }
}
