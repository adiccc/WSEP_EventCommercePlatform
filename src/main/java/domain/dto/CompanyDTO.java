package domain.dto;

import domain.company.Company;

import java.util.Map;
import java.util.Set;

public class CompanyDTO {
    private int companyId;
    private String companyName;
    private boolean isActive;

    public CompanyDTO(int companyId, String companyName, boolean isActive) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.isActive = isActive;
    }
    public int getCompanyId() {
        return companyId;
    }
    public String getCompanyName() {
        return companyName;
    }
    public boolean isActive() {
        return isActive;
    }

    public String companyName() {
        return companyName;
    }
}

