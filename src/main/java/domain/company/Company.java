package domain.company;

import domain.dataType.PermissionType;
import domain.policy.Discount;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Company {
    private int companyId;
    private String companyName;
    private boolean isActive;

    private ContactInfo contactInfo;
    private PurchasePolicy purchasePolicy;
    private DiscountPolicy discountPolicy;

    private int founderId;
    // key = ownerId, value = appointedBy (the owner who appointed them); founder has -1 (no appointer)
    private Map<Integer, Integer> ownerAppointedBy;
    private Map<Integer, ManagerAppointment> managersPermissionsMap;

    public Company(int companyId, String companyName, int founderId, ContactInfo contactInfo,
                   PurchasePolicy defaultPurchase, DiscountPolicy defaultDiscount) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.founderId = founderId;
        this.contactInfo = contactInfo;

        this.purchasePolicy = defaultPurchase;
        this.discountPolicy = defaultDiscount;
        this.isActive = true;
        this.ownerAppointedBy = new HashMap<>();
        this.ownerAppointedBy.put(founderId, -1);  // founder has no appointer
        this.managersPermissionsMap = new HashMap<>();
        this.purchasePolicy = new PurchasePolicy();
        this.discountPolicy = new DiscountPolicy();
    }

    public void updatePurchasePolicy(int userId, PurchasePolicy newPolicy) {
        if (!isActive)
            throw new IllegalStateException("Company is not active");
        if (!isOwner(userId))
            throw new SecurityException("User does not have permission to update purchase policy");
        if (!newPolicy.isValid())
            throw new IllegalArgumentException("Invalid policy data");
        this.purchasePolicy = newPolicy;
    }

    public void updateDiscountPolicy(int userId, DiscountPolicy newPolicy) {
        if (!isActive)
            throw new IllegalStateException("Company is not active");
        if (!isOwner(userId))
            throw new SecurityException("User does not have permission to update discount policy");
        if (!newPolicy.isValid())
            throw new IllegalArgumentException("Invalid discount policy data");
        this.discountPolicy = newPolicy;
    }

    public void deactivate() { this.isActive = false; }

    public boolean isOwner(int userId) { return ownerAppointedBy.containsKey(userId); }

    public boolean checkPermission(int userId, PermissionType permissionType) {
        // TODO: to implement (just for the test before we have the real implementation)
        if (userId > 1) {
            return false;
        }
        return true;
    }

    public int getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public boolean isActive() { return isActive; }
    public int getFounderId() { return founderId; }
    public ContactInfo getContactInfo() { return contactInfo; }
    public PurchasePolicy getPurchasePolicy() { return purchasePolicy; }
    public DiscountPolicy getDiscountPolicy() { return discountPolicy; }
    public Set<Integer> getOwnerIds() { return ownerAppointedBy.keySet(); }
    public int getOwnerAppointedBy(int ownerId) { return ownerAppointedBy.getOrDefault(ownerId, -1); }
    public void addOwner(int newOwnerId, int appointedBy) { ownerAppointedBy.put(newOwnerId, appointedBy); }
    public Map<Integer, ManagerAppointment> getManagersPermissionsMap() { return managersPermissionsMap; }

    /**
     * Admin use – force-remove an owner without checking who the appointer is.
     * Used when a system admin removes a user from the platform entirely.
     * Their appointees cascade to the founder exactly as in removeOwner.
     */
    public void forceRemoveOwner(int ownerToRemoveId) {
        if (!ownerAppointedBy.containsKey(ownerToRemoveId))
            throw new IllegalArgumentException("User is not an owner of this company");
        if (ownerToRemoveId == founderId)
            throw new IllegalArgumentException("Cannot force-remove the founder — deactivate the company instead");
        ownerAppointedBy.remove(ownerToRemoveId);
        cascadeToFounder(ownerToRemoveId);
    }

    public boolean isFounder(int userId) { return userId == founderId; }

    /**
     * Use case 9 – Remove owner appointment.
     * Only the appointer of the target owner may remove them.
     * All owners and managers appointed by the removed owner are re-assigned to the founder.
     */
    public void removeOwner(int requesterId, int ownerToRemoveId) {
        if (!ownerAppointedBy.containsKey(ownerToRemoveId))
            throw new IllegalArgumentException("User is not an owner of this company");
        if (ownerToRemoveId == founderId)
            throw new IllegalArgumentException("Cannot remove the founder");
        if (ownerAppointedBy.get(ownerToRemoveId) != requesterId)
            throw new SecurityException("Only the appointer can remove this owner");

        ownerAppointedBy.remove(ownerToRemoveId);
        cascadeToFounder(ownerToRemoveId);
    }

    /**
     * Use case 10 – Waive ownership (voluntary).
     * The owner themselves gives up their role.
     * Their appointees cascade to the founder exactly as in removeOwner.
     */
    public void waiveOwnership(int ownerId) {
        if (ownerId == founderId)
            throw new IllegalArgumentException("Founder cannot waive ownership");
        if (!ownerAppointedBy.containsKey(ownerId))
            throw new IllegalArgumentException("User is not an owner of this company");

        ownerAppointedBy.remove(ownerId);
        cascadeToFounder(ownerId);
    }

    /**
     * Use case 12 – Remove manager appointment.
     * Only the appointer of the target manager may remove them.
     */
    public void removeManager(int requesterId, int managerToRemoveId) {
        ManagerAppointment appt = managersPermissionsMap.get(managerToRemoveId);
        if (appt == null)
            throw new IllegalArgumentException("User is not a manager of this company");
        if (appt.getAppointedBy() != requesterId)
            throw new SecurityException("Only the appointer can remove this manager");

        managersPermissionsMap.remove(managerToRemoveId);
    }

    /**
     * Re-assigns all owners and managers whose appointer was removedOwnerId to the founder.
     */
    private void cascadeToFounder(int removedOwnerId) {
        ownerAppointedBy.replaceAll((ownerId, appointerId) ->
                appointerId == removedOwnerId ? founderId : appointerId);
        managersPermissionsMap.values().stream()
                .filter(m -> m.getAppointedBy() == removedOwnerId)
                .forEach(m -> m.setAppointedBy(founderId));
    }

    public void addDiscount(int userId, Discount policy) {
        if (!checkPermission(userId,PermissionType.MANAGE_POLICIES)&&!isOwner(userId)) {
            throw new SecurityException("User does not have permission to add discount policy");
        }
        discountPolicy.addDiscount(policy);
    }
    
    public void removeDiscount(int userId, Discount policy) {
        if (!checkPermission(userId,PermissionType.MANAGE_POLICIES)&&!isOwner(userId)) {
            throw new SecurityException("User does not have permission to remove discount policy");
        }
        discountPolicy.removeDiscount(policy);
    }
}
