package application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import domain.company.Company;
import domain.company.ContactInfo;
import domain.company.ICompanyRepo;
import domain.event.IOrderRepo;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import domain.user.Founder;
import domain.user.IUserRepo;
import domain.user.User;

public class CompanyService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyService.class);

    private final TokenService tokenService;
    private final IAuth auth;
    private final ICompanyRepo companyRepo;
    private final IUserRepo userRepo;
    private final IOrderRepo orderRepo;

    public CompanyService(TokenService tokenService, IAuth auth, ICompanyRepo companyRepo,
                          IUserRepo userRepo, IOrderRepo orderRepo) {
        this.tokenService = tokenService;
        this.auth = auth;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
    }

    public Response<Company> createProductionCompany(String sessionToken, int companyId, String companyName,
                                                     String email, String phone, String bankAccount) {
        try {
            logger.info("Attempting to create company: {} for user: {}", companyName, sessionToken);

            User user = userRepo.findById(sessionToken);
            if (user == null) {
                return new Response<>(null, "User not found.");
            }
            if (!user.isConnected()) {
                return new Response<>(null, "User must be logged in to create a company.");
            }

            if (email == null || !email.contains("@") || phone == null || bankAccount == null) {
                return new Response<>(null, "Invalid contact or bank account information.");
            }

            synchronized (companyRepo) {
                if (companyRepo.existsById(companyId)) {
                    return new Response<>(null, "Company ID already exists in the system.");
                }
                if (companyRepo.existsByName(companyName)) {
                    return new Response<>(null, "Company name is already taken.");
                }

                ContactInfo contactInfo = new ContactInfo(email, phone, bankAccount);
                PurchasePolicy defaultPurchase = new PurchasePolicy();
                DiscountPolicy defaultDiscount = new DiscountPolicy();

                int userId = auth.getUserId(sessionToken);
                Company newCompany = new Company(companyId, companyName, userId,
                        contactInfo, defaultPurchase, defaultDiscount);

                Founder founderRole = new Founder(companyId);
                user.addRole(founderRole);

                companyRepo.save(newCompany);
                userRepo.save(user);

                logger.info("Company {} created successfully", companyName);
                return new Response<>(newCompany, "Production company created successfully.");
            }

        } catch (Exception e) {
            logger.error("Failed to create company {}. Error: {}", companyName, e.getMessage());
            return new Response<>(null, "System error occurred: " + e.getMessage());
        }
    }

    public Response<Boolean> updatePurchasePolicy(String token, int companyId, PurchasePolicy policy) {
        logger.info("Starting updatePurchasePolicy for companyId: {}", companyId);
        try {
            if (!tokenService.validateToken(token)) {
                logger.warn("updatePurchasePolicy failed: invalid or expired token");
                return Response.error("Invalid or expired token");
            }
            int userId = auth.getUserId(token);
            Company company = companyRepo.findById(companyId);
            if (company == null) {
                logger.warn("updatePurchasePolicy failed: company not found, id: {}", companyId);
                return Response.error("Company not found");
            }
            String error = company.updatePurchasePolicy(userId, policy);
            if (error != null) {
                logger.warn("updatePurchasePolicy failed for companyId: {}. Reason: {}", companyId, error);
                return Response.error(error);
            }
            companyRepo.store(company);
            logger.info("Purchase policy updated successfully for companyId: {}", companyId);
            return Response.ok(true);
        } catch (Exception e) {
            logger.error("Unexpected error in updatePurchasePolicy for companyId: {}. Error: {}", companyId, e.getMessage());
            return Response.error("Unexpected error in updatePurchasePolicy for companyId: " + companyId);
        }
    }
}
