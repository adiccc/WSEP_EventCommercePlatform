package application;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import domain.company.Company;
import domain.company.ContactInfo;
import domain.company.ICompanyRepo;
import domain.company.ManagerAppointment;
import domain.dataType.PermissionType;
import domain.dto.RolesPermissionsTreeDTO;
import domain.event.IOrderRepo;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import domain.user.Founder;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.user.User;

public class CompanyService {

    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final IAuth auth;
    private final ICompanyRepo companyRepo;
    private final IUserRepo userRepo;
    private final IOrderRepo orderRepo;

    public CompanyService(IAuth auth, ICompanyRepo companyRepo,
                          IUserRepo userRepo, IOrderRepo orderRepo) {
        this.auth = auth;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
    }

    public Response<Company> createProductionCompany(String sessionToken, int companyId, String companyName,
                                                     String email, String phone, String bankAccount) {
        try {
            logger.info("Attempting to create company: " + companyName + " for user: " + sessionToken);
            int userId = auth.getUserId(sessionToken).getValue();
            Member user = userRepo.findById(userId);
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
                Company newCompany = new Company(companyId, companyName, userId,
                        contactInfo, defaultPurchase, defaultDiscount);

                Founder founderRole = new Founder(companyId);
                user.addRole(founderRole);

                companyRepo.store(newCompany);
                userRepo.store(user);

                logger.info("Company " + companyName + " created successfully");
                return new Response<>(newCompany, "Production company created successfully.");
            }

        } catch (Exception e) {
            logger.severe("Failed to create company " + companyName + ". Error: " + e.getMessage());
            return new Response<>(null, "System error occurred: " + e.getMessage());
        }
    }

    /**
     * II.4.15 – View roles and permissions tree
     *
     * Returns the full roles hierarchy for a company:
     *   Founder → Owners → Managers (each with their permissions set).
     *
     * Only an owner of the company may call this.
     */
    public Response<RolesPermissionsTreeDTO> viewRolesAndPermissionsTree(String token, int companyId) {
        logger.info("viewRolesAndPermissionsTree called for companyId: " + companyId);
        try {
            // 1. Validate token (covers "user not logged in")
            if (!auth.isLoggedIn(token).isError()) {
                logger.warning("viewRolesAndPermissionsTree failed: invalid or expired token");
                return Response.error("Invalid or expired token");
            }

            int userId = auth.getUserId(token).getValue();

            // 2. Company must exist
            Company company = companyRepo.findById(companyId);
            if (company == null) {
                logger.warning("viewRolesAndPermissionsTree failed: company not found, id: " + companyId);
                return Response.error("Company not found");
            }

            // 3. Requesting user must be an owner
            if (!company.isOwner(userId)) {
                logger.warning("viewRolesAndPermissionsTree failed: user " + userId + " is not an owner of company " + companyId);
                return Response.error("User does not have permission to view roles and permissions");
            }

            // 4. Build the roles tree
            Map<String, Set<PermissionType>> managersPermissions = new HashMap<>();
            for (Map.Entry<String, ManagerAppointment> entry : company.getManagersPermissionsMap().entrySet()) {
                managersPermissions.put(entry.getKey(), entry.getValue().getPermissions());
            }

            RolesPermissionsTreeDTO tree = new RolesPermissionsTreeDTO(
                    company.getFounderId(),
                    company.getOwnerIds(),
                    managersPermissions
            );

            logger.info("viewRolesAndPermissionsTree succeeded for companyId: " + companyId);
            return Response.ok(tree);

        } catch (Exception e) {
            logger.severe("Unexpected error in viewRolesAndPermissionsTree for companyId: " + companyId + ". Error: " + e.getMessage());
            return Response.error("Unexpected error: " + e.getMessage());
        }
    }

    public Response<Boolean> updatePurchasePolicy(String token, int companyId, PurchasePolicy policy) {
        logger.info("Starting updatePurchasePolicy for companyId: " + companyId);
        try {
            int userId = auth.getUserId(token).getValue();
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
            return Response.error("Unexpected error in updatePurchasePolicy for companyId: " + companyId);
        }
    }
}
