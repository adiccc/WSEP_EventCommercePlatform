package domain.user;

public class Founder extends Role {

    private String companyId;

    public Founder(String companyId) {
        this.companyId = companyId;
    }

    public String getCompanyId() {
        return companyId;
    }
}
