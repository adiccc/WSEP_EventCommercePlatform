package domain.dto;

import domain.company.Company;

import java.util.List;

public class CompanyDetailsDTO {
    private int companyId;
    private String companyName;
    private boolean isActive;
    private String email;
    private String phone;
    private String purchasePolicy; //describe function of Purchase policy class
    private String discountPolicy; //need to take also as String waiting for implementation
    private int founderId;
    List<EventDTO> futureEvents;
    
    public CompanyDetailsDTO() {}
}
