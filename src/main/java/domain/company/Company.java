package domain.company;

import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Company {
    private String companyId;
    private String companyName;
    private boolean isActive;

    private ContactInfo contactInfo;
    private PurchasePolicy purchasePolicy;
    private DiscountPolicy discountPolicy;

    private String founderId;
    private List<String> ownerIds;
    private Map<String, ManagerAppointment> managersPermissionsMap;

    public Company(String companyId, String companyName, String founderId, ContactInfo contactInfo,
                   PurchasePolicy defaultPurchase, DiscountPolicy defaultDiscount) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.founderId = founderId;
        this.contactInfo = contactInfo;

        this.purchasePolicy = defaultPurchase;
        this.discountPolicy = defaultDiscount;
        this.isActive = true;
        this.ownerIds = new ArrayList<>();
        this.ownerIds.add(founderId);
        this.managersPermissionsMap = new HashMap<>();
    }

    public String getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public boolean isActive() { return isActive; }
    public String getFounderId() { return founderId; }
}