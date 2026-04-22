package application;

import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.event.IOrderRepo;
import domain.policy.PurchasePolicy;

import java.util.logging.Logger;

public class CompanyService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final TokenService tokenService;
    private final IAuth auth;
    private final ICompanyRepo companyRepo;
    private final IOrderRepo orderRepo;

    public CompanyService(TokenService tokenService, IAuth auth, ICompanyRepo companyRepo, IOrderRepo orderRepo) {
        this.tokenService = tokenService;
        this.auth = auth;
        this.companyRepo = companyRepo;
        this.orderRepo = orderRepo;
    }

    public Response<Boolean> updatePurchasePolicy(String token, int companyId, PurchasePolicy policy) {
        logger.info("Starting updatePurchasePolicy for companyId: " + companyId);
        try {
            if (!tokenService.validateToken(token)) {
                logger.warning("updatePurchasePolicy failed: invalid or expired token");
                return Response.error("Invalid or expired token");
            }
            int userId = auth.getUserId(token);
            Company company = companyRepo.findById(companyId);
            if (company == null) {
                logger.warning("updatePurchasePolicy failed: company not found, id: " + companyId);
                return Response.error("Company not found");
            }
            String error = company.updatePurchasePolicy(userId, policy);
            if (error != null) {
                logger.warning("updatePurchasePolicy failed for companyId: " + companyId + ". Reason: " + error);
                return Response.error(error);
            }
            companyRepo.store(company);
            logger.info("Purchase policy updated successfully for companyId: " + companyId);
            return Response.ok(true);
        } catch (Exception e) {
            logger.severe("Unexpected error in updatePurchasePolicy for companyId: " + companyId + ". Error: " + e.getMessage());
            throw e;
        }
    }
}
