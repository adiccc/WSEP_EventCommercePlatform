package application;

import java.util.*;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

import domain.company.*;
import domain.dataType.PermissionType;
import domain.dto.CompanyDTO;
import domain.dto.HierarchyDTO;
import domain.dto.RolesPermissionsTreeDTO;
import domain.event.IOrderRepo;
import domain.policy.Discount;
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

    public CompanyService( IAuth auth, ICompanyRepo companyRepo,
                          IUserRepo userRepo) {
        this.auth = auth;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
    }

    public Response<Company> createProductionCompany(String sessionToken, int companyId, String companyName,
                                                     String email, String phone, String bankAccount) {
        return RetryHelper.executeWithRetry(() ->{
        try {
                logger.info("Attempting to create company: " + companyName + " for user: " + sessionToken);
                int userId = auth.getUserId(sessionToken).getValue();
                Member user = userRepo.findById(userId);
                if (user == null) {
                    return new Response<>(null, "User not found.");
                }
//            if (!user.isConnected()) {
//                return new Response<>(null, "User must be logged in to create a company.");
//            }
                if (!auth.isLoggedIn(sessionToken).getValue()) {
                    return new Response<>(null, "User must be logged in to create a company.");
                }

                if (email == null || !email.contains("@") || phone == null || bankAccount == null) {
                    return new Response<>(null, "Invalid contact or bank account information.");
                }

                synchronized (companyRepo) {
                    try {
                        companyRepo.findById(companyId);
                        // no exception → company already exists
                        return new Response<>(null, "Company ID already exists in the system.");
                    } catch (NoSuchElementException ignored) {
                        // expected: company does not exist yet, continue
                    }
                    if (companyRepo.existsByName(companyName)) {
                        return new Response<>(null, "Company name is already taken.");
                    }

                    ContactInfo contactInfo = new ContactInfo(email, phone, bankAccount);
                    PurchasePolicy defaultPurchase = new PurchasePolicy();
                    DiscountPolicy defaultDiscount = new DiscountPolicy();
                    Permissions companyPermission = new Permissions(userId);
                    Company newCompany = new Company(companyId, companyName,
                            contactInfo, defaultPurchase, defaultDiscount, companyPermission);

                    Founder founderRole = new Founder(companyId);
                    user.addRole(founderRole);

                    companyRepo.store(newCompany);
                    userRepo.store(user);

                    logger.info("Company " + companyName + " created successfully");
                    return new Response<>(newCompany, "Production company created successfully.");
                }

            } catch(Exception e){
                logger.severe("Failed to create company " + companyName + ". Error: " + e.getMessage());
                return new Response<>(null, "System error occurred: " + e.getMessage());
            }
        });
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
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("viewRolesAndPermissionsTree called for companyId: " + companyId);
            try {
                // 1. Validate token
                if (auth.isLoggedIn(token).isError()) {
                    logger.warning("viewRolesAndPermissionsTree failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }

                int userId = auth.getUserId(token).getValue();

                // 2. Company must exist
                Company company = companyRepo.findById(companyId);

                // 3. Requesting user must be an owner
                if (!company.getCompanyPermission().isOwner(userId)) {
                    logger.warning("viewRolesAndPermissionsTree failed: user " + userId + " is not an owner of company " + companyId);
                    return Response.error("User does not have permission to view roles and permissions");
                }

                // 4. Build the roles tree
                Map<Integer, Set<PermissionType>> managersPermissions = new HashMap<>();
                for (Map.Entry<Integer, HierarchyDTO> entry : company.getCompanyPermission().getCompanyTree().entrySet()) {
                    managersPermissions.put(entry.getKey(), entry.getValue().getAllPermissions());
                }
                RolesPermissionsTreeDTO tree = new RolesPermissionsTreeDTO(
                        company.getCompanyPermission().getFounderId(),
                        company.getCompanyPermission().getOwnerIds(),
                        managersPermissions
                );
                logger.info("viewRolesAndPermissionsTree succeeded for companyId: " + companyId);
                return Response.ok(tree);

            } catch (NoSuchElementException e) {
                logger.warning("viewRolesAndPermissionsTree failed: company not found, id: " + companyId);
                return Response.error("Company not found");
            } catch (Exception e) {
                logger.severe("Unexpected error in viewRolesAndPermissionsTree for companyId: " + companyId + ". Error: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> updatePurchasePolicy(String token, int companyId, PurchasePolicy policy) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("Starting updatePurchasePolicy for companyId: " + companyId);
            try {
                int userId = auth.getUserId(token).getValue();
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
        });
    }

    public Response<Boolean> addDiscountToCompany(String token, int companyId, Discount discount) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("addDiscountToCompany called for companyId: " + companyId);

            try {
                // 1. Validate token
                if (auth.isLoggedIn(token).isError()) {
                    logger.warning("addDiscountToCompany failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }

                int userId = auth.getUserId(token).getValue();

                // 2. Company must exist
                Company company = companyRepo.findById(companyId);
                if (company == null) {
                    logger.warning("addDiscountToCompany failed: company not found, id: " + companyId);
                    return Response.error("Company not found");
                }

                // 3. Company must be active
                if (!company.isActive()) {
                    logger.warning("addDiscountToCompany failed: company is not active, id: " + companyId);
                    return Response.error("Company is not active");
                }

                // 4. Discount must be valid
                if (discount == null || !discount.isValid()) {
                    logger.warning("addDiscountToCompany failed: invalid discount");
                    return Response.error("Invalid discount data");
                }

                // 5. Apply (permissions + duplicates checked inside Company)
                company.addDiscount(userId, discount);
                companyRepo.store(company);

                logger.info("addDiscountToCompany succeeded for companyId: " + companyId);
                return Response.ok(true);

            } catch (SecurityException e) {
                logger.warning("addDiscountToCompany unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());

            } catch (IllegalArgumentException e) {
                logger.warning("addDiscountToCompany invalid data: " + e.getMessage());
                return Response.error(e.getMessage());

            } catch (IllegalStateException e) {
                logger.warning("addDiscountToCompany invalid state: " + e.getMessage());
                return Response.error(e.getMessage());

            } catch (Exception e) {
                logger.severe("Unexpected error in addDiscountToCompany: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> removeDiscountFromCompany(String token, int companyId, Discount discount) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("removeDiscountFromCompany called for companyId: " + companyId);

            try {
                // 1. Validate token
                if (auth.isLoggedIn(token).isError()) {
                    logger.warning("removeDiscountFromCompany failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }

                int userId = auth.getUserId(token).getValue();

                // 2. Company must exist
                Company company = companyRepo.findById(companyId);
                if (company == null) {
                    logger.warning("removeDiscountFromCompany failed: company not found, id: " + companyId);
                    return Response.error("Company not found");
                }

                // 3. Company must be active
                if (!company.isActive()) {
                    logger.warning("removeDiscountFromCompany failed: company is not active, id: " + companyId);
                    return Response.error("Company is not active");
                }

                // 4. Discount must be valid
                if (discount == null || !discount.isValid()) {
                    logger.warning("removeDiscountFromCompany failed: invalid discount");
                    return Response.error("Invalid discount data");
                }

                // 5. Apply (permissions + existence checked inside Company)
                company.removeDiscount(userId, discount);
                companyRepo.store(company);

                logger.info("removeDiscountFromCompany succeeded for companyId: " + companyId);
                return Response.ok(true);

            } catch (SecurityException e) {
                logger.warning("removeDiscountFromCompany unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());

            } catch (IllegalArgumentException e) {
                logger.warning("removeDiscountFromCompany invalid data: " + e.getMessage());
                return Response.error(e.getMessage());

            } catch (IllegalStateException e) {
                logger.warning("removeDiscountFromCompany invalid state: " + e.getMessage());
                return Response.error(e.getMessage());

            } catch (Exception e) {
                logger.severe("Unexpected error in removeDiscountFromCompany: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<List<CompanyDTO>> getAvailableCompanies(String token) { //TODO taking care of guest part, additional input : String guestUuid
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("getAvailableCompanies called");
            try {
                List<Company> allCompanies = companyRepo.getAll();
                if (allCompanies == null || allCompanies.isEmpty()) {
                    logger.warning("No companies in the system");
                    return new Response<>(null, "No companies in the system");
                }
                List<CompanyDTO> filteredCompanies = new ArrayList<CompanyDTO>();
                if (token != null && !token.isEmpty()) {
                    if (!auth.isLoggedIn(token).getValue()) {
                        return new Response<>(null, "Invalid or expired token");
                    }
                }
                //it's a member or a guest
                int userId = auth.getUserId(token).getValue(); //for guest returns -1
                boolean isMember = userId != -1;
                for (Company company : allCompanies) {
                    // isUserPermitted means or the company is active
                    // if the company isn't active only members who are owners and have the right permitting can access
                    boolean isUserPermitted = company.isActive() || (isMember && (company.getCompanyPermission().isOwner(userId) || company.getCompanyPermission().checkPermission(userId, PermissionType.VIEW_CLOSED_COMPANIES)));
                    if (isUserPermitted) {
                        filteredCompanies.add(new CompanyDTO(
                                company.getCompanyId(),
                                company.getCompanyName(),
                                company.isActive()));
                    }
                }
                if (filteredCompanies.isEmpty()) {
                    logger.warning("No companies in the system");
                    return new Response<>(null, "No companies in the system");
                }
                logger.info("Successfully retrieved available companies");
                return new Response<>(filteredCompanies, "Companies retrieved successfully");
            } catch (SecurityException e) {
                logger.warning("getAvailableCompanies unauthorized: " + e.getMessage());
                return new Response<>(null, "Invalid or expired token");
            }
        });
    }

    public Response<Boolean> deactivateCompany(String ownerToken, int companyId) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("deactivateCompany called");
            if (!auth.isLoggedIn(ownerToken).getValue()) {
                logger.warning("deactivateCompany failed: invalid or expired token");
                return new Response<>(false, "Invalid or expired token, deactivate failed");
            }
            try {
                Company company = companyRepo.findById(companyId);
                if (company.isActive()) {
                    company.deactivate();
                    companyRepo.store(company);
                    logger.info("deactivateCompany succeeded for companyId: " + companyId);
                    return new Response<>(true, "Company deactivated successfully");
                } else {
                    logger.warning("deactivateCompany failed: company is already deactivated, id: " + companyId);
                    return new Response<>(false, "Company is already deactivated");
                }
            } catch (NoSuchElementException e) {
                logger.warning("deactivateCompany failed: company not found, id: " + companyId);
                return new Response<>(false, "Company not found");
            } catch (Exception e) {
                logger.severe("Unexpected error in deactivateCompany: " + e.getMessage());
                return new Response<>(false, "Unexpected error: " + e.getMessage());
            }
        });
    }
}
