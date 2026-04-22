package application;

import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.event.IOrderRepo;
import domain.policy.PurchasePolicy;

public class CompanyService {
    private IAuth auth;
    private ICompanyRepo companyRepo;
    private IOrderRepo orderRepo;

    public CompanyService(IAuth auth, ICompanyRepo companyRepo, IOrderRepo orderRepo) {
        this.auth = auth;
        this.companyRepo = companyRepo;
        this.orderRepo = orderRepo;
    }

    public Response<Boolean> updatePurchasePolicy(String token, int companyId, PurchasePolicy policy) {
        if (!auth.isLoggedIn(token))
            return Response.error("User is not logged in");
        int userId = auth.getUserId(token);
        Company company = companyRepo.findById(companyId);
        if (company == null)
            return Response.error("Company not found");
        String error = company.updatePurchasePolicy(userId, policy);
        if (error != null)
            return Response.error(error);
        companyRepo.store(company);
        return Response.ok(true);
    }
}
