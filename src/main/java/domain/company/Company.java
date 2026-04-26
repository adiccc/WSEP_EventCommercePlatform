package domain.company;

import domain.dataType.PermissionType;
import domain.policy.Discount;
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

   // private Map<String, ManagerAppointment> managersPermissionsMap;
    private Permissions companyPermission;

    public Company(int companyId, String companyName, ContactInfo contactInfo,
                   PurchasePolicy defaultPurchase, DiscountPolicy defaultDiscount,Permissions companyPermission) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.contactInfo = contactInfo;

        this.purchasePolicy = defaultPurchase;
        this.discountPolicy = defaultDiscount;
        this.isActive = true;
        //this.managersPermissionsMap = new HashMap<>();
        this.purchasePolicy = new PurchasePolicy();
        this.discountPolicy = new DiscountPolicy();
        this.companyPermission = companyPermission;
    }

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

    public void deactivate() { this.isActive = false; }

  //  public boolean isOwner(int userId) { return ownerIds.contains(userId); }

//    public boolean checkPermission(int userId, PermissionType permissionType) {
//        // TODO: to implement (just for the test before we have the real implementation)
//        if (userId > 1) {
//            return false;
//        }
//        return true;
//    }

    public int getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public boolean isActive() { return isActive; }
    public ContactInfo getContactInfo() { return contactInfo; }
    public PurchasePolicy getPurchasePolicy() { return purchasePolicy; }
    public DiscountPolicy getDiscountPolicy() { return discountPolicy; }
    public Permissions getCompanyPermission() { return companyPermission; }

    public void addDiscount(int userId, Discount policy) {
        if (!companyPermission.checkPermission(userId,PermissionType.MANAGE_POLICIES)) { //check inside the function if it's owner
            throw new SecurityException("User does not have permission to add discount policy");
        }
        discountPolicy.addDiscount(policy);
    }
    
    public void removeDiscount(int userId, Discount policy) {
        if (!companyPermission.checkPermission(userId,PermissionType.MANAGE_POLICIES)) {
            throw new SecurityException("User does not have permission to remove discount policy");
        }
        discountPolicy.removeDiscount(policy);
    }
}
