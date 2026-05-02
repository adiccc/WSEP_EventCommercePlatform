package domain.company;

import DTO.DiscountDTO;
import DTO.PurchaseRuleDTO;
import domain.dataType.PermissionType;
import domain.policy.*;
import domain.activeOrder.ActiveOrder;
import domain.dataType.PermissionType;
import domain.dto.HierarchyDTO;

import java.util.HashMap;
import java.util.HashSet;
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

    public void deactivate() { this.isActive = false; }

    // --- Getters ---
    public int getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public boolean isActive() { return isActive; }
    public ContactInfo getContactInfo() { return contactInfo; }
    public PurchasePolicy getPurchasePolicy() { return purchasePolicy; }
    public DiscountPolicy getDiscountPolicy() { return discountPolicy; }
    public Permissions getCompanyPermission() { return companyPermission; }

    public void addDiscount(int userId, DiscountDTO discount) {
        if (!companyPermission.checkPermission(userId, PermissionType.MANAGE_POLICIES)) {
            throw new SecurityException("User does not have permission to add discount policy");
        }
        discountPolicy.addDiscount(DiscountPolicy.dtoToDiscount(discount));
    }

    public void removeDiscount(int userId, DiscountDTO discount) {
        if (!companyPermission.checkPermission(userId, PermissionType.MANAGE_POLICIES)) {
            throw new SecurityException("User does not have permission to remove discount policy");
        }
        discountPolicy.removeDiscount(DiscountPolicy.dtoToDiscount(discount));
    }

    public void addRule(int userId, PurchaseRuleDTO dto) {
        if (!checkPermission(userId, PermissionType.MANAGE_POLICIES) && !isOwner(userId))
            throw new SecurityException("User does not have permission to add purchase rule");
        if (!isActive)
            throw new IllegalStateException("Company is not active");
        purchasePolicy.addRule(PurchasePolicy.dtoToPurchase(dto));
    }

    public void removeRule(int userId, PurchaseRuleDTO dto) {
        if (!checkPermission(userId, PermissionType.MANAGE_POLICIES) && !isOwner(userId))
            throw new SecurityException("User does not have permission to remove purchase rule");
        if (!isActive)
            throw new IllegalStateException("Company is not active");
        purchasePolicy.removeRule(PurchasePolicy.dtoToPurchase(dto));
    }

    public int getFounderId() { return companyPermission.getFounderId(); }
    public Set<Integer> getOwnerIds() { return companyPermission.getOwnerIds(); }
    public boolean isOwner(int userId) { return companyPermission.isOwner(userId); }
    public boolean checkPermission(int userId, PermissionType permission) {
        return companyPermission.checkPermission(userId, permission);
    }
    public Map<Integer, HierarchyDTO> getManagersPermissionsMap() {
        return companyPermission.getCompanyTree();
    }

    public void updateManagerPermissions(int ownerId, int managerId, Set<PermissionType> newPermissions) {
        companyPermission.updateManagerPermissions(ownerId, managerId, newPermissions);
    }

    // ── Owner appointment ──────────────────────────────────────────────────

    public void requestAppointOwner(int appointerId, int appointeeId) {
        if (!companyPermission.isOwner(appointerId))
            throw new SecurityException("User does not have the required owner permissions");
        if (companyPermission.isOwner(appointeeId))
            throw new IllegalStateException("Subscriber is already appointed as owner in this company");
        if (companyPermission.isPendingOwner(appointeeId))
            throw new IllegalStateException("Subscriber already has a pending owner appointment");
        companyPermission.addOwner(appointeeId);
    }

    public boolean isPendingOwner(int userId) {
        return companyPermission.isPendingOwner(userId);
    }

    public void respondOwnerAppointment(int userId, boolean accept) {
        companyPermission.OwnerAppointeeRespond(userId, accept);
    }

    // ── Manager appointment ────────────────────────────────────────────────

    public void requestAppointManager(int appointerId, int appointeeId, Set<PermissionType> permissions) {
        if (!companyPermission.isOwner(appointerId))
            throw new SecurityException("User does not have the required owner permissions");
        if (companyPermission.isManager(appointeeId))
            throw new IllegalStateException("Subscriber is already appointed as manager in this company");
        if (companyPermission.isPendingManager(appointeeId))
            throw new IllegalStateException("Subscriber already has a pending manager appointment");
        if (permissions == null || permissions.isEmpty())
            throw new IllegalArgumentException("At least one permission must be selected for the representative");
        companyPermission.addPendingManager(appointeeId, appointerId, permissions);
    }

    public boolean isPendingManager(int userId) {
        return companyPermission.isPendingManager(userId);
    }

    public void respondManagerAppointment(int userId, boolean accept) {
        companyPermission.respondManagerAppointment(userId, accept);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Company other = (Company) obj;
        return companyId==other.companyId && version == other.getVersion();
    }
}
