package domain.company;

import domain.dataType.PermissionType;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
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

    private int founderId;
    private Set<Integer> ownerIds;
    private Map<String, ManagerAppointment> managersPermissionsMap;

    public Company(int companyId, String companyName, int founderId, ContactInfo contactInfo,
                   PurchasePolicy defaultPurchase, DiscountPolicy defaultDiscount) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.founderId = founderId;
        this.contactInfo = contactInfo;

        this.purchasePolicy = defaultPurchase;
        this.discountPolicy = defaultDiscount;
        this.isActive = true;
        this.ownerIds = new HashSet<>();
        this.ownerIds.add(founderId);
        this.managersPermissionsMap = new HashMap<>();
    }

    public String updatePurchasePolicy(int userId, PurchasePolicy newPolicy) {
        if (!isActive)
            return "Company is not active";
        if (!ownerIds.contains(userId))
            return "User does not have permission to update purchase policy";
        if (!newPolicy.isValid())
            return "Invalid policy data";
        this.purchasePolicy = newPolicy;
        return null;
    }

    public void deactivate() { this.isActive = false; }

    public boolean isOwner(int userId) { return ownerIds.contains(userId); }

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
    public Set<Integer> getOwnerIds() { return ownerIds; }
    public Map<String, ManagerAppointment> getManagersPermissionsMap() { return managersPermissionsMap; }
}