package domain.dto;

import domain.company.Company;
import domain.policy.PurchasePolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompanyDetailsDTO {
    private int companyId;
    private String companyName;
    private boolean isActive;
    private String email;
    private String phone;
    private String purchasePolicy; //describe function of Purchase policy class
    private String discountPolicy; //need to take also as String waiting for implementation
    List<EventDTO> futureEvents;
    
    public CompanyDetailsDTO(int companyId, String companyName, boolean isActive, String email, String phone, String purchasePolicy, String discountPolicy, List<EventDTO> futureEvents) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.isActive = isActive;
        this.email = email;
        this.phone = phone;
        this.purchasePolicy = purchasePolicy;
        this.discountPolicy = discountPolicy;
        this.futureEvents = futureEvents;
    }

    public CompanyDetailsDTO(Company c) {
        companyId = c.getCompanyId();
        companyName = c.getCompanyName();
        isActive = c.isActive();
        email = c.getContactInfo().getEmail();
        phone = c.getContactInfo().getPhone();
        purchasePolicy=c.getPurchasePolicy().describe();
        discountPolicy=c.getDiscountPolicy().describe();
        futureEvents=new ArrayList<>();
    }

    public int getCompanyId() {
        return companyId;
    }

    public List<EventDTO> getFutureEvents() {
        return futureEvents;
    }
}
