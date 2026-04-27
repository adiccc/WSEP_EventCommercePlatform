package application;

import java.util.*;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Set;
import domain.company.Company;
import domain.company.ContactInfo;
import domain.company.ICompanyRepo;
import domain.company.ManagerAppointment;
import domain.dataType.PermissionType;
import domain.dto.CompanyDTO;
import domain.dto.RolesPermissionsTreeDTO;
import domain.event.IOrderRepo;
import DTO.DiscountDTO;
import DTO.PurchaseRuleDTO;
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
                // 1. Validate token (covers "user not logged in")
                if (!auth.isLoggedIn(token).getValue()) {
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
        });
    }

    public Response<Boolean> addRuleToCompany(String token, int companyId, PurchaseRuleDTO ruleDTO) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("addRuleToCompany called for companyId: " + companyId);
            try {
                if (!auth.isLoggedIn(token).getValue()) {
                    logger.warning("addRuleToCompany failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }

                int userId = auth.getUserId(token).getValue();

                Company company = companyRepo.findById(companyId);
                if (company == null) {
                    logger.warning("addRuleToCompany failed: company not found, id: " + companyId);
                    return Response.error("Company not found");
                }

                if (ruleDTO == null) {
                    logger.warning("addRuleToCompany failed: null rule DTO");
                    return Response.error("Invalid rule data");
                }

                company.addRule(userId, ruleDTO);
                companyRepo.store(company);

                logger.info("addRuleToCompany succeeded for companyId: " + companyId);
                return Response.ok(true);

            } catch (SecurityException e) {
                logger.warning("addRuleToCompany unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warning("addRuleToCompany invalid data: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalStateException e) {
                logger.warning("addRuleToCompany invalid state: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (Exception e) {
                logger.severe("Unexpected error in addRuleToCompany: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> removeRuleFromCompany(String token, int companyId, PurchaseRuleDTO ruleDTO) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("removeRuleFromCompany called for companyId: " + companyId);
            try {
                if (!auth.isLoggedIn(token).getValue()) {
                    logger.warning("removeRuleFromCompany failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }

                int userId = auth.getUserId(token).getValue();

                Company company = companyRepo.findById(companyId);
                if (company == null) {
                    logger.warning("removeRuleFromCompany failed: company not found, id: " + companyId);
                    return Response.error("Company not found");
                }

                if (ruleDTO == null) {
                    logger.warning("removeRuleFromCompany failed: null rule DTO");
                    return Response.error("Invalid rule data");
                }

                company.removeRule(userId, ruleDTO);
                companyRepo.store(company);

                logger.info("removeRuleFromCompany succeeded for companyId: " + companyId);
                return Response.ok(true);

            } catch (SecurityException e) {
                logger.warning("removeRuleFromCompany unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warning("removeRuleFromCompany invalid data: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalStateException e) {
                logger.warning("removeRuleFromCompany invalid state: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (Exception e) {
                logger.severe("Unexpected error in removeRuleFromCompany: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> addDiscountToCompany(String token, int companyId, DiscountDTO discountDTO) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("addDiscountToCompany called for companyId: " + companyId);

            try {
                // 1. Validate token
                if (!auth.isLoggedIn(token).getValue()) {
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

                // 4. DTO must be present
                if (discountDTO == null) {
                    logger.warning("addDiscountToCompany failed: null discount DTO");
                    return Response.error("Invalid discount data");
                }

                // 5. Apply (permissions + duplicates checked inside Company)
                company.addDiscount(userId, discountDTO);
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

    public Response<Boolean> removeDiscountFromCompany(String token, int companyId, DiscountDTO discountDTO) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("removeDiscountFromCompany called for companyId: " + companyId);

            try {
                // 1. Validate token
                if (!auth.isLoggedIn(token).getValue()) {
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

                // 4. DTO must be present
                if (discountDTO == null) {
                    logger.warning("removeDiscountFromCompany failed: null discount DTO");
                    return Response.error("Invalid discount data");
                }

                // 5. Apply (permissions + existence checked inside Company)
                company.removeDiscount(userId, discountDTO);
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
                    //TODO when check permission is implemented change just for a call to that function
                    boolean isUserPermitted = company.isActive() || (isMember && (company.isOwner(userId) || company.checkPermission(userId, PermissionType.VIEW_CLOSED_COMPANIES)));
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
            if (!companyRepo.existsById(companyId)) {
                logger.warning("deactivateCompany failed: company not found, id: " + companyId);
                return new Response<>(false, "Company not found");
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
            } catch (Exception e) {
                logger.severe("Unexpected error in deactivateCompany: " + e.getMessage());
                return new Response<>(false, "Unexpected error: " + e.getMessage());
            }
        });
    }
}
