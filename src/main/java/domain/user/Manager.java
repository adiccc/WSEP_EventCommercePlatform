package domain.user;

public class Manager extends Role {
    private int companyId;

    public Manager(int companyId) { this.companyId = companyId; }

    public int getCompanyId() { return companyId; }
}