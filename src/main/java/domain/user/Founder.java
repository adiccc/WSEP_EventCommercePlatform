package domain.user;

public class Founder extends Role {

    private int companyId;

    public Founder(int companyId) {
        this.companyId = companyId;
    }

    public int getCompanyId() {
        return companyId;
    }
}
