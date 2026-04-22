package application;

import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.event.IOrderRepo;
import domain.policy.DiscountPolicy;
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
            int userId = auth.getUserId(token);
            Company company = companyRepo.findById(companyId);
            company.updatePurchasePolicy(userId, policy);
            companyRepo.store(company);
            logger.info("Purchase policy updated successfully for companyId: " + companyId);
            return Response.ok(true);
        } catch (SecurityException e) {
            logger.warning("updatePurchasePolicy unauthorized for companyId: " + companyId + ". " + e.getMessage());
            return Response.error(e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warning("updatePurchasePolicy bad arguments for companyId: " + companyId + ". " + e.getMessage());
            return Response.error(e.getMessage());
        } catch (IllegalStateException e) {
            logger.warning("updatePurchasePolicy invalid state for companyId: " + companyId + ". " + e.getMessage());
            return Response.error(e.getMessage());
        } catch (Exception e) {
            logger.severe("Unexpected error in updatePurchasePolicy for companyId: " + companyId + ". Error: " + e.getMessage());
            return Response.error("Unexpected error in updatePurchasePolicy for companyId: " + companyId);
        }
    }

    public Response<Boolean> updateDiscountPolicy(String token, int companyId, DiscountPolicy policy) {
        logger.info("Starting updateDiscountPolicy for companyId: " + companyId);
        try {
            int userId = auth.getUserId(token);
            Company company = companyRepo.findById(companyId);
            company.updateDiscountPolicy(userId, policy);
            companyRepo.store(company);
            logger.info("Discount policy updated successfully for companyId: " + companyId);
            return Response.ok(true);
        } catch (SecurityException e) {
            logger.warning("updateDiscountPolicy unauthorized for companyId: " + companyId + ". " + e.getMessage());
            return Response.error(e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warning("updateDiscountPolicy bad arguments for companyId: " + companyId + ". " + e.getMessage());
            return Response.error(e.getMessage());
        } catch (IllegalStateException e) {
            logger.warning("updateDiscountPolicy invalid state for companyId: " + companyId + ". " + e.getMessage());
            return Response.error(e.getMessage());
        } catch (Exception e) {
            logger.severe("Unexpected error in updateDiscountPolicy for companyId: " + companyId + ". Error: " + e.getMessage());
            return Response.error("Unexpected error in updateDiscountPolicy for companyId: " + companyId);
        }
    }
}
