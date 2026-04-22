package infrastructure;

import domain.company.ICompanyRepo;
import domain.dataType.PermissionType;

public class CompanyRepoIml implements ICompanyRepo {

    // TODO: to implement :)
    @Override
    public boolean checkPremissions(int companyId, int userId, PermissionType permissionType) {
        return true;
    }
}
