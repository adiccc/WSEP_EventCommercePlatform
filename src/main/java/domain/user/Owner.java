package domain.user;

public class Owner extends Role {
    private int companyId;

    public Owner(int companyId) {
        this.companyId = companyId;
    }

    public int getCompanyId() {
        return companyId;
    }
}