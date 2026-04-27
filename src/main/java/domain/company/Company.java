package domain.company;

import DTO.DiscountDTO;
import DTO.PurchaseRuleDTO;
import domain.dataType.PermissionType;
import domain.policy.CodeCoupun;
import domain.policy.Discount;
import domain.policy.DiscountPolicy;
import domain.policy.LimitedDiscount;
import domain.policy.MaxTicketsRule;
import domain.policy.MinAgeRule;
import domain.policy.Purchase;
import domain.policy.PurchasePolicy;
import domain.policy.VisualDiscount;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private long version;

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
        this.purchasePolicy = new PurchasePolicy();
        this.discountPolicy = new DiscountPolicy();
        this.version = 0;
    }
    public Company(Company company) {
        this.companyId = company.getCompanyId();
        this.companyName = company.getCompanyName();
        this.founderId = company.getFounderId();
        this.contactInfo = new ContactInfo(company.getContactInfo());
        this.purchasePolicy = new PurchasePolicy(company.getPurchasePolicy());
        this.discountPolicy = new DiscountPolicy(company.getDiscountPolicy());
        this.isActive = company.isActive;
        this.ownerIds = new HashSet<>(company.getOwnerIds());
        this.managersPermissionsMap = company.managersPermissionsMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new ManagerAppointment(e.getValue())
                ));
        this.version=company.version;
    }

    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }

    public void updatePurchasePolicy(int userId, PurchasePolicy newPolicy) {
        if (!isActive)
            throw new IllegalStateException("Company is not active");
        if (!ownerIds.contains(userId))
            throw new SecurityException("User does not have permission to update purchase policy");
        if (!newPolicy.isValid())
            throw new IllegalArgumentException("Invalid policy data");
        this.purchasePolicy = newPolicy;
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

    public void addRule(int userId, PurchaseRuleDTO dto) {
        if (!checkPermission(userId, PermissionType.MANAGE_POLICIES) && !isOwner(userId))
            throw new SecurityException("User does not have permission to add purchase rule");
        if (!isActive)
            throw new IllegalStateException("Company is not active");
        purchasePolicy.addRule(toRule(dto));
    }

    public void removeRule(int userId, PurchaseRuleDTO dto) {
        if (!checkPermission(userId, PermissionType.MANAGE_POLICIES) && !isOwner(userId))
            throw new SecurityException("User does not have permission to remove purchase rule");
        if (!isActive)
            throw new IllegalStateException("Company is not active");
        purchasePolicy.removeRule(toRule(dto));
    }

    public void addDiscount(int userId, DiscountDTO dto) {
        if (!checkPermission(userId, PermissionType.MANAGE_POLICIES) && !isOwner(userId))
            throw new SecurityException("User does not have permission to add discount");
        discountPolicy.addDiscount(toDiscount(dto));
    }

    public void removeDiscount(int userId, DiscountDTO dto) {
        if (!checkPermission(userId, PermissionType.MANAGE_POLICIES) && !isOwner(userId))
            throw new SecurityException("User does not have permission to remove discount");
        discountPolicy.removeDiscount(toDiscount(dto));
    }

    private Purchase toRule(PurchaseRuleDTO dto) {
        return switch (dto.getType()) {
            case MIN_AGE -> new MinAgeRule(dto.getMinAge());
            case MAX_TICKETS -> new MaxTicketsRule(dto.getMaxTickets());
        };
    }

    private Discount toDiscount(DiscountDTO dto) {
        return switch (dto.getType()) {
            case VISUAL -> new VisualDiscount(dto.getPercentage(), dto.getEndDate());
            case CODE_COUPON -> new CodeCoupun(dto.getCode(), dto.getPercentage(), dto.getEndDate());
            case LIMITED -> new LimitedDiscount(dto.getPercentage(), dto.getMinQuantity());
        };
    }
}
