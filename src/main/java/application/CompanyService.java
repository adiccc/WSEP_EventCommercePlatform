package application;

import java.util.*;
import java.util.logging.Logger;

import DTO.*;
import Exception.OptimisticLockingFailureException;
import domain.Suspension.ISuspensionRepo;
import domain.company.*;
import domain.dataType.PermissionType;
import domain.dto.CompanyDTO;
import domain.dto.CompanyDetailsDTO;
import domain.dto.HierarchyDTO;
import domain.dto.RolesPermissionsTreeDTO;
import domain.policy.*;
import domain.user.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CompanyService {

    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final IAuth auth;
    private final ISuspensionRepo suspensionRepo;
    private final ICompanyRepo companyRepo;
    private final IUserRepo userRepo;
    private final INotifier notifier;
    private TransactionTemplate transactionTemplate;


    @Autowired
    public CompanyService( IAuth auth, ICompanyRepo companyRepo,
                          IUserRepo userRepo,ISuspensionRepo suspensionRepo,INotifier notifier, TransactionTemplate transactionTemplate) {
        this.auth = auth;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.suspensionRepo = suspensionRepo;
        this.notifier = notifier;
        this.transactionTemplate = transactionTemplate;
    }

    public Response<Company> createProductionCompany(String sessionToken, int companyId, String companyName,
                                                     String email, String phone, String bankAccount) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                try {
                    logger.info("Attempting to create company: " + companyName + " for user: " + sessionToken);
                    String role = getValidatedRole(sessionToken);
                    if (role == null) {
                        return new Response<>(null, "Invalid token");
                    }
                    int userId = getUserIdFromToken(sessionToken);
                    if (userId == -1) {
                        return new Response<>(null, "User must be logged in to create a company, or session expired.");
                    }
                    Member user = userRepo.findById(userId);
                    if (user == null) {
                        return new Response<>(null, "User not found.");
                    }
                    if (suspensionRepo.haveActiveSuspension(getUserIdFromToken(sessionToken))) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
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
                        PurchasePolicy defaultPurchase = new AndPurchasePolicy();
                        DiscountPolicy defaultDiscount = new SumDiscountPolicy();
                        Permissions companyPermission = new Permissions(userId);
                        Company newCompany = new Company(companyId, companyName,
                                contactInfo, defaultPurchase, defaultDiscount, companyPermission);

                        user.changeState(new Founder());

                        companyRepo.store(newCompany);
                        userRepo.store(user);

                        logger.info("Company " + companyName + " created successfully");
                        return Response.ok(newCompany);
                    }

                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Failed to create company " + companyName + ". Error: " + e.getMessage());
                    return new Response<>(null, "System error occurred: " + e.getMessage());
                }
            })
        );
    }

    public Response<CompanyDetailsDTO> getProductionCompany(String sessionToken, int companyId) {
        return RetryHelper.executeWithRetry(()-> {
            logger.info("getProductionCompany called for companyId: " + companyId);
            try {
                // 1. Validate token — guests have userId == -1, which is fine for read-only access
                String role = getValidatedRole(sessionToken);
                if (role == null) {
                    logger.warning("getProductionCompany failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                // 2. Company must exist
                Company company = companyRepo.findById(companyId);
                // 3. For members, sync their role state within the company
                if ("MEMBER".equals(role)) {
                    int userId = getUserIdFromToken(sessionToken);
                    if (userId != -1) {
                        Member member = userRepo.findById(userId);
                        member.changeState(company.getCompanyPermission().getUserState(userId));
                        userRepo.store(member);
                    }
                }
                logger.info("getProductionCompany successful");
                return Response.ok(new CompanyDetailsDTO(company));
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Failed to get company " + companyId + ". Error: " + e.getMessage());
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
                String role = getValidatedRole(token);
                if (role == null) {
                    return new Response<>(null, "Invalid token");
                }
                // 1. Validate token
                int userId = getUserIdFromToken(token);
                if (userId == -1) {
                    logger.warning("viewRolesAndPermissionsTree failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
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
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in viewRolesAndPermissionsTree for companyId: " + companyId + ". Error: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    /**
     * Returns the calling user's role within the given company:
     * "FOUNDER", "OWNER", "MANAGER", or "MEMBER".
     */
    public Response<String> getUserRoleInCompany(String token, int companyId) {
        return RetryHelper.executeWithRetry(() -> {
            try {
                String role = getValidatedRole(token);
                if (role == null) return Response.error("Invalid or expired token");
                int userId = getUserIdFromToken(token);
                if (userId == -1) return Response.error("Invalid or expired token");

                Company company = companyRepo.findById(companyId);
                return Response.ok(company.getUserRoleName(userId));
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("getUserRoleInCompany failed: " + e.getMessage());
                return Response.error("Could not determine role: " + e.getMessage());
            }
        });
    }

    /**
     * Returns the set of PermissionTypes granted to the calling user as a manager in the company.
     * Returns an empty set if the user is not a manager.
     */
    public Response<Set<PermissionType>> getMyPermissions(String token, int companyId) {
        return RetryHelper.executeWithRetry(() -> {
            try {
                String role = getValidatedRole(token);
                if (role == null) return Response.error("Invalid or expired token");
                int userId = getUserIdFromToken(token);
                if (userId == -1) return Response.error("Invalid or expired token");

                Company company = companyRepo.findById(companyId);
                return Response.ok(company.getManagerPermissions(userId));
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("getMyPermissions failed: " + e.getMessage());
                return Response.error("Could not retrieve permissions: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> addRuleToCompany(String token, int companyId, PurchaseRuleDTO ruleDTO) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("addRuleToCompany called for companyId: " + companyId);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return new Response<>(false, "Invalid token");
                    }
                    int userId = getUserIdFromToken(token);
                    if (userId == -1) {
                        logger.warning("addRuleToCompany failed: invalid or expired token");
                        return Response.error("Invalid or expired token");
                    }
                    if (suspensionRepo.haveActiveSuspension(userId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }

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
                    status.setRollbackOnly();
                    logger.warning("addRuleToCompany unauthorized: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalArgumentException e) {
                    status.setRollbackOnly();
                    logger.warning("addRuleToCompany invalid data: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalStateException e) {
                    status.setRollbackOnly();
                    logger.warning("addRuleToCompany invalid state: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in addRuleToCompany: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }

    public Response<Boolean> removeRuleFromCompany(String token, int companyId, PurchaseRuleDTO ruleDTO) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("removeRuleFromCompany called for companyId: " + companyId);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return new Response<>(false, "Invalid token");
                    }
                    int userId = getUserIdFromToken(token);
                    if (userId == -1) {
                        logger.warning("removeRuleFromCompany failed: invalid or expired token");
                        return Response.error("Invalid or expired token");
                    }
                    if (suspensionRepo.haveActiveSuspension(userId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }
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
                    status.setRollbackOnly();
                    logger.warning("removeRuleFromCompany unauthorized: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalArgumentException e) {
                    status.setRollbackOnly();
                    logger.warning("removeRuleFromCompany invalid data: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalStateException e) {
                    status.setRollbackOnly();
                    logger.warning("removeRuleFromCompany invalid state: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in removeRuleFromCompany: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }

    public Response<Boolean> addDiscountToCompany(String token, int companyId, DiscountDTO discountDTO) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("addDiscountToCompany called for companyId: " + companyId);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return new Response<>(false, "Invalid token");
                    }
                    int userId = getUserIdFromToken(token);
                    if (userId == -1) {
                        logger.warning("addDiscountToCompany failed: invalid or expired token");
                        return Response.error("Invalid or expired token");
                    }
                    if (suspensionRepo.haveActiveSuspension(userId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }

                    Company company = companyRepo.findById(companyId);
                    if (company == null) {
                        logger.warning("addDiscountToCompany failed: company not found, id: " + companyId);
                        return Response.error("Company not found");
                    }

                    if (!company.isActive()) {
                        logger.warning("addDiscountToCompany failed: company is not active, id: " + companyId);
                        return Response.error("Company is not active");
                    }

                    if (discountDTO == null) {
                        logger.warning("addDiscountToCompany failed: null discount DTO");
                        return Response.error("Invalid discount data");
                    }

                    company.addDiscount(userId, discountDTO);
                    companyRepo.store(company);

                    logger.info("addDiscountToCompany succeeded for companyId: " + companyId);
                    return Response.ok(true);

                } catch (SecurityException e) {
                    status.setRollbackOnly();
                    logger.warning("addDiscountToCompany unauthorized: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalArgumentException e) {
                    status.setRollbackOnly();
                    logger.warning("addDiscountToCompany invalid data: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalStateException e) {
                    status.setRollbackOnly();
                    logger.warning("addDiscountToCompany invalid state: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in addDiscountToCompany: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }

    public Response<Boolean> removeDiscountFromCompany(String token, int companyId, DiscountDTO discountDTO) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("removeDiscountFromCompany called for companyId: " + companyId);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return new Response<>(false, "Invalid token");
                    }
                    int userId = getUserIdFromToken(token);
                    if (userId == -1) {
                        logger.warning("removeDiscountFromCompany failed: invalid or expired token");
                        return Response.error("Invalid or expired token");
                    }
                    if (suspensionRepo.haveActiveSuspension(userId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }

                    Company company = companyRepo.findById(companyId);
                    if (company == null) {
                        logger.warning("removeDiscountFromCompany failed: company not found, id: " + companyId);
                        return Response.error("Company not found");
                    }

                    if (!company.isActive()) {
                        logger.warning("removeDiscountFromCompany failed: company is not active, id: " + companyId);
                        return Response.error("Company is not active");
                    }

                    if (discountDTO == null) {
                        logger.warning("removeDiscountFromCompany failed: null discount DTO");
                        return Response.error("Invalid discount data");
                    }

                    company.removeDiscount(userId, discountDTO);
                    companyRepo.store(company);

                    logger.info("removeDiscountFromCompany succeeded for companyId: " + companyId);
                    return Response.ok(true);

                } catch (SecurityException e) {
                    status.setRollbackOnly();
                    logger.warning("removeDiscountFromCompany unauthorized: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalArgumentException e) {
                    status.setRollbackOnly();
                    logger.warning("removeDiscountFromCompany invalid data: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalStateException e) {
                    status.setRollbackOnly();
                    logger.warning("removeDiscountFromCompany invalid state: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in removeDiscountFromCompany: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }

    public Response<Void> changeDiscountPolicyType(String token, int companyId, DiscountPolicyType policyType) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("changeDiscountPolicyType called for companyId: " + companyId);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return new Response<>(null, "Invalid token");
                    }
                    int userId = getUserIdFromToken(token);
                    if (userId == -1)
                        return Response.error("Invalid or expired token");
                    if (suspensionRepo.haveActiveSuspension(userId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }
                    Company company = companyRepo.findById(companyId);
                    if (!company.isActive())
                        return Response.error("Company is not active");

                    company.changeDiscountPolicyType(userId, policyType);
                    companyRepo.store(company);

                    logger.info("changeDiscountPolicyType succeeded for companyId: " + companyId);
                    return Response.ok(null);

                } catch (SecurityException e) {
                    status.setRollbackOnly();
                    logger.warning("changeDiscountPolicyType unauthorized: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in changeDiscountPolicyType: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }

    public Response<Void> changePurchasePolicyType(String token, int companyId, PurchasePolicyType policyType) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("changePurchasePolicyType called for companyId: " + companyId);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return new Response<>(null, "Invalid token");
                    }
                    int userId = getUserIdFromToken(token);
                    if (userId == -1)
                        return Response.error("Invalid or expired token");
                    if (suspensionRepo.haveActiveSuspension(userId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }
                    Company company = companyRepo.findById(companyId);
                    if (!company.isActive())
                        return Response.error("Company is not active");

                    company.changePurchasePolicyType(userId, policyType);
                    companyRepo.store(company);

                    logger.info("changePurchasePolicyType succeeded for companyId: " + companyId);
                    return Response.ok(null);

                } catch (SecurityException e) {
                    status.setRollbackOnly();
                    logger.warning("changePurchasePolicyType unauthorized: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in changePurchasePolicyType: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }

    public Response<List<CompanyDTO>> getAvailableCompanies(String token) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("getAvailableCompanies called");
            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    logger.warning("getAvailableCompanies failed: invalid or expired token");
                    return new Response<>(null,"Invalid or expired token");
                }
                List<Company> allCompanies = companyRepo.getAll();
                if (allCompanies == null || allCompanies.isEmpty()) {
                    logger.warning("No companies in the system");
                    return new Response<>(null, "No companies in the system");
                }
                List<CompanyDTO> filteredCompanies = new ArrayList<CompanyDTO>();
                //it's a member or a guest
                int userId = getUserIdFromToken(token);
                boolean isMember = "MEMBER".equals(role);
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
                return new Response<>(null, "System error occurred");
            }   catch (OptimisticLockingFailureException e) {
                throw e;
            }
        });
    }
    public Response<Boolean> updateManagerPermissions(String token, int companyId, int managerId,
                                                       Set<PermissionType> newPermissions) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("updateManagerPermissions called for companyId: " + companyId + ", managerId: " + managerId);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return new Response<>(false, "Invalid token");
                    }
                    int userId = getUserIdFromToken(token);
                    if (userId == -1) {
                        logger.warning("updateManagerPermissions failed: invalid or expired token");
                        return Response.error("Invalid or expired token");
                    }
                    if (suspensionRepo.haveActiveSuspension(userId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }
                    if (newPermissions == null) {
                        logger.warning("updateManagerPermissions failed: null permissions list");
                        return Response.error("Permissions list cannot be null");
                    }
                    Company company = companyRepo.findById(companyId);
                    if (!company.isOwner(userId)) {
                        logger.warning("updateManagerPermissions failed: user " + userId + " is not an owner of company " + companyId);
                        return Response.error("User does not have the required owner permissions");
                    }
                    Member managerMember = userRepo.findById(managerId);
                    if (managerMember == null) {
                        logger.warning("updateManagerPermissions failed: manager " + managerId + " not found");
                        return Response.error("Manager not found");
                    }
                    company.updateManagerPermissions(userId, managerId, newPermissions);
                    companyRepo.store(company);

                    NotifyPayload payload = new NotifyPayload("Your manager permissions have been updated in company " + company.getCompanyName(),null, companyId);
                    NotifyDTO notifyDTO = new NotifyDTO( NotifyType.GENERAL_POPUP,payload);
                    sendOrSaveNotification(managerMember.getIdentifier(), notifyDTO);
                    logger.info("updateManagerPermissions sent notification successfully");

                    logger.info("updateManagerPermissions succeeded for managerId: " + managerId);
                    return Response.ok(true);
                } catch (NoSuchElementException e) {
                    status.setRollbackOnly();
                    logger.warning("updateManagerPermissions failed: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (SecurityException e) {
                    status.setRollbackOnly();
                    logger.warning("updateManagerPermissions unauthorized: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalArgumentException e) {
                    status.setRollbackOnly();
                    logger.warning("updateManagerPermissions invalid argument: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in updateManagerPermissions: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }
    public Response<Boolean> requestAppointOwner(String token, int companyId, int appointeeId) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("requestAppointOwner called for companyId: " + companyId + ", appointeeId: " + appointeeId);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return new Response<>(false, "Invalid token");
                    }
                    int appointerId = getUserIdFromToken(token);
                    if (appointerId == -1) {
                        logger.warning("requestAppointOwner failed: invalid or expired token");
                        return Response.error("Invalid or expired token");
                    }
                    if (suspensionRepo.haveActiveSuspension(appointerId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }
                    Company company;
                    try {
                        company = companyRepo.findById(companyId);
                    } catch (NoSuchElementException e) {
                        logger.warning("requestAppointOwner failed: company not found, id: " + companyId);
                        return Response.error("Company not found");
                    }
                    Member appointee = null;
                    try {
                        appointee = userRepo.findById(appointeeId);
                    } catch (NoSuchElementException ignored) {}
                    if (appointee == null) {
                        logger.warning("requestAppointOwner failed: appointee " + appointeeId + " not found");
                        return Response.error("Only a registered subscriber can be appointed");
                    }
                    company.requestAppointOwner(appointerId, appointeeId);
                    companyRepo.store(company);
                    NotifyPayload payload = new NotifyPayload("You have been invited to be a owner at company " + company.getCompanyName(), null,companyId);
                    NotifyDTO notifyDTO = new NotifyDTO( NotifyType.ROLE_APPOINTMENT_REQUEST,payload);
                    sendOrSaveNotification(appointee.getIdentifier(), notifyDTO);
                    logger.info("requestAppointOwner sent notification successfully");

                    logger.info("requestAppointOwner succeeded: pending appointment created for " + appointeeId);
                    return Response.ok(true);
                } catch (SecurityException e) {
                    status.setRollbackOnly();
                    logger.warning("requestAppointOwner unauthorized: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalStateException e) {
                    status.setRollbackOnly();
                    logger.warning("requestAppointOwner invalid state: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in requestAppointOwner: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }
    public Response<Boolean> respondToOwnerAppointment(String token, int companyId, boolean accept) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("respondToOwnerAppointment called for companyId: " + companyId + ", accept: " + accept);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return new Response<>(false, "Invalid token");
                    }
                    int userId = getUserIdFromToken(token);
                    if (userId == -1) {
                        logger.warning("respondToOwnerAppointment failed: invalid or expired token");
                        return Response.error("Invalid or expired token");
                    }
                    if (suspensionRepo.haveActiveSuspension(userId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }
                    Company company;
                    try {
                        company = companyRepo.findById(companyId);
                    } catch (NoSuchElementException e) {
                        logger.warning("respondToOwnerAppointment failed: company not found, id: " + companyId);
                        return Response.error("Company not found");
                    }
                    if (!company.isPendingOwner(userId)) {
                        logger.warning("respondToOwnerAppointment failed: no pending appointment for user " + userId);
                        return Response.error("No pending owner appointment found for this user");
                    }
                    company.respondOwnerAppointment(userId, accept);
                    if (accept) {
                        Member member = userRepo.findById(userId);
                        member.changeState(new Owner());
                        userRepo.store(member);
                    } else {
                        logger.info("respondToOwnerAppointment: user " + userId + " rejected appointment for company " + companyId);
                    }
                    companyRepo.store(company);
                    if(accept){
                        NotifyPayload payload = new NotifyPayload("You are now officially a Owner of company " + company.getCompanyName(), null, companyId);
                        NotifyDTO notifyDTO = new NotifyDTO( NotifyType.GENERAL_POPUP,payload);
                        sendOrSaveNotification(userRepo.getUserEmail(userId), notifyDTO);
                        logger.info("respondToOwnerAppointment: user " + userId + " accepted and became owner of company " + companyId);
                    }
                    return Response.ok(accept);
                } catch (NoSuchElementException e) {
                    status.setRollbackOnly();
                    logger.warning("respondToOwnerAppointment failed: company not found, id: " + companyId);
                    return Response.error("Company not found");
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in respondToOwnerAppointment: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }
    public Response<Boolean> requestAppointManager(String token, int companyId, int appointeeId,
                                                    Set<PermissionType> permissions) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("requestAppointManager called for companyId: " + companyId + ", appointeeId: " + appointeeId);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return Response.error("Invalid token");
                    }
                    int appointerId = getUserIdFromToken(token);

                    if (appointerId == -1) {
                        logger.warning("requestAppointManager failed: invalid or expired token");
                        return Response.error("Invalid or expired token");
                    }
                    if (suspensionRepo.haveActiveSuspension(appointerId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }
                    Company company;
                    try {
                        company = companyRepo.findById(companyId);
                    } catch (NoSuchElementException e) {
                        logger.warning("requestAppointManager failed: company not found, id: " + companyId);
                        return Response.error("Company not found");
                    }
                    Member member = null;
                    try {
                        member = userRepo.findById(appointeeId);
                    } catch (NoSuchElementException e) {
                        logger.warning("requestAppointManager failed: appointee not found, id: " + appointeeId);
                        return Response.error("Only a registered subscriber can be appointed");
                    }
                    company.requestAppointManager(appointerId, appointeeId, permissions);
                    companyRepo.store(company);
                    NotifyPayload payload = new NotifyPayload("You have been invited to be a manager at company " + company.getCompanyName(), null,companyId);
                    NotifyDTO notifyDTO = new NotifyDTO( NotifyType.ROLE_APPOINTMENT_REQUEST,payload);
                    sendOrSaveNotification(member.getIdentifier(), notifyDTO);
                    logger.info("requestAppointManager sent notification successfully");

                    logger.info("requestAppointManager succeeded for appointeeId: " + appointeeId);
                    return Response.ok(true);
                } catch (SecurityException e) {
                    status.setRollbackOnly();
                    logger.warning("requestAppointManager unauthorized: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalStateException e) {
                    status.setRollbackOnly();
                    logger.warning("requestAppointManager invalid state: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalArgumentException e) {
                    status.setRollbackOnly();
                    logger.warning("requestAppointManager invalid argument: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in requestAppointManager: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }
    public Response<Boolean> respondToManagerAppointment(String token, int companyId, boolean accept) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("respondToManagerAppointment called for companyId: " + companyId + ", accept: " + accept);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return new Response<>(false, "Invalid token");
                    }
                    int userId = getUserIdFromToken(token);
                    if (userId == -1) {
                        logger.warning("respondToManagerAppointment failed: invalid or expired token");
                        return Response.error("Invalid or expired token");
                    }
                    if (suspensionRepo.haveActiveSuspension(userId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }
                    Company company;
                    try {
                        company = companyRepo.findById(companyId);
                    } catch (NoSuchElementException e) {
                        logger.warning("respondToManagerAppointment failed: company not found, id: " + companyId);
                        return Response.error("Company not found");
                    }
                    if (!company.isPendingManager(userId)) {
                        logger.warning("respondToManagerAppointment failed: no pending appointment for user " + userId);
                        return Response.error("No pending manager appointment found for this user");
                    }
                    company.respondManagerAppointment(userId, accept);
                    if (accept) {
                        Member member = userRepo.findById(userId);
                        member.changeState(new Manager());
                        userRepo.store(member);
                    }
                    companyRepo.store(company);
                    if(accept){
                        NotifyPayload payload = new NotifyPayload("You are now officially a Manager of company " + company.getCompanyName(), null, companyId);
                        NotifyDTO notifyDTO = new NotifyDTO( NotifyType.GENERAL_POPUP,payload);
                        sendOrSaveNotification(userRepo.getUserEmail(userId), notifyDTO);
                        logger.info("respondToManagerAppointment succeeded for userId: " + userId + ", accepted: " + accept);
                    }
                    return Response.ok(accept);
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in respondToManagerAppointment: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }
    public Response<Boolean> removeManagerAppointment(String token, int companyId, int managerId) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("removeManagerAppointment called for companyId: " + companyId + ", managerId: " + managerId);
                try {
                    String role = getValidatedRole(token);
                    if (role == null) {
                        return new Response<>(false, "Invalid token");
                    }
                    int actingOwnerId = getUserIdFromToken(token);

                    if (actingOwnerId == -1) {
                        logger.warning("removeManagerAppointment failed: invalid or expired token");
                        return Response.error("Invalid or expired token");
                    }
                    if (suspensionRepo.haveActiveSuspension(actingOwnerId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(null, "user does not have write access caused by suspension.");
                    }
                    Company company;
                    try {
                        company = companyRepo.findById(companyId);
                    } catch (NoSuchElementException e) {
                        logger.warning("removeManagerAppointment failed: company not found, id: " + companyId);
                        return Response.error("Company not found");
                    }
                    company.removeManagerAppointment(actingOwnerId, managerId);
                    Member managerMember;
                    try {
                        managerMember = userRepo.findById(managerId);
                    } catch (NoSuchElementException e) {
                        logger.warning("removeManagerAppointment failed: manager user not found, id: " + managerId);
                        return Response.error("Manager user not found");
                    }
                    managerMember.changeState(null);
                    userRepo.store(managerMember);
                    companyRepo.store(company);
                    NotifyPayload payload = new NotifyPayload("Your manager role has been removed from company " + company.getCompanyName(), null, companyId);
                    NotifyDTO notifyDTO = new NotifyDTO( NotifyType.KICKOUT_TAB_NAVIGATION,payload);
                    sendOrSaveNotification(managerMember.getIdentifier(), notifyDTO);
                    logger.info("removeManagerAppointment succeeded sending notification for userId: " + managerMember.getIdentifier());

                    logger.info("removeManagerAppointment succeeded for managerId: " + managerId);
                    return Response.ok(true);
                } catch (SecurityException e) {
                    status.setRollbackOnly();
                    logger.warning("removeManagerAppointment unauthorized: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (IllegalStateException e) {
                    status.setRollbackOnly();
                    logger.warning("removeManagerAppointment invalid state: " + e.getMessage());
                    return Response.error(e.getMessage());
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in removeManagerAppointment: " + e.getMessage());
                    return Response.error("Unexpected error: " + e.getMessage());
                }
            })
        );
    }

    /**
     * Returns companies where the calling user holds a role (FOUNDER, OWNER, or MANAGER).
     * Delegates filtering to the repository.
     */
    public Response<List<CompanyDTO>> getMyCompanies(String token) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("getMyCompanies called");
            try {
                String role = getValidatedRole(token);
                if (role == null) {
                    return Response.error("Invalid or expired token");
                }
                if (role.equals("GUEST")) {
                    return Response.error("Guests do not have company roles");
                }
                int userId = getUserIdFromToken(token);
                if(userId == -1) return Response.error("Invalid or expired token");
                List<CompanyDTO> result = companyRepo.findByUserRole(userId);
                logger.info("getMyCompanies succeeded, found " + result.size() + " companies for user " + userId);
                return Response.ok(result);
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("getMyCompanies failed: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }
    public Response<Boolean> deactivateCompany(String ownerToken, int companyId) {
        return RetryHelper.executeWithRetry(() ->
            transactionTemplate.execute(status -> {
                logger.info("deactivateCompany called");
                try {
                    String role = getValidatedRole(ownerToken);
                    if (role == null) {
                        return new Response<>(false, "Invalid token");
                    }
                    int userId = getUserIdFromToken(ownerToken);
                    if(userId == -1) {
                        return new Response<>(false, "Invalid or expired token");
                    }
                    if (suspensionRepo.haveActiveSuspension(userId)) {
                        logger.severe("User does not have write access caused by suspension");
                        return new Response<>(false, "user does not have write access caused by suspension.");
                    }
                    Company company = companyRepo.findById(companyId);
                    if (company == null) {
                        logger.warning("deactivateCompany failed: company not found, id: " + companyId);
                        return new Response<>(false, "Company not found");
                    }
                    if (!company.isOwner(userId)) {
                        logger.warning("deactivateCompany failed: user isn't owner of the company");
                        return new Response<>(false, "user is not owner of the company");
                    }
                    if (company.isActive()) {
                        company.deactivate();
                        companyRepo.store(company);
                        Set<Integer> allStaff = new HashSet<>(company.getOwnerIds());
                        allStaff.addAll(company.getCompanyPermission().getManagers());
                        for(Integer staffId : allStaff){
                            NotifyPayload payload = new NotifyPayload("Alert: Company " + company.getCompanyName() + " has been deactivated.", null, companyId);
                            NotifyDTO notifyDTO = new NotifyDTO(NotifyType.KICKOUT_TAB_NAVIGATION, payload);
                            sendOrSaveNotification(userRepo.getUserEmail(staffId), notifyDTO);
                            logger.info("deactivateCompany succeeded sending notification for userId: " + staffId);
                        }
                        logger.info("deactivateCompany succeeded for companyId: " + companyId);
                        return new Response<>(true, "Company deactivated successfully");
                    } else {
                        logger.warning("deactivateCompany failed: company is already deactivated, id: " + companyId);
                        return new Response<>(false, "Company is already deactivated");
                    }
                } catch (NoSuchElementException e) {
                    status.setRollbackOnly();
                    logger.warning("deactivateCompany failed: company not found, id: " + companyId);
                    return new Response<>(false, "Company not found");
                } catch (OptimisticLockingFailureException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.severe("Unexpected error in deactivateCompany: " + e.getMessage());
                    return new Response<>(false, "Unexpected error: " + e.getMessage());
                }
            })
        );
    }
       private int getUserIdFromToken(String token) {
            String email = auth.getUserEmail(token).getValue();
            if (email == null) {
                return -1;
            }
                Member m = userRepo.findUserByEmail(email);
                if (m != null) return m.getUserId();
            return -1; //for guest or invalid
        }

    private void notifyTokenExpired(String token) {
        try{
            NotifyPayload payload = new NotifyPayload("Your session has expired");
            NotifyDTO expiredNotify = new NotifyDTO(NotifyType.TOKEN_EXPIRED, payload);
            notifier.notifyTab(token, expiredNotify);
            logger.info("Sent TOKEN_EXPIRED notification to tab: " + token);
        } catch (Exception e) {
            logger.warning("Failed to send TOKEN_EXPIRED notification: " + e.getMessage());
        }
    }

    private String getValidatedRole(String token) {
        Response<String> roleRes = auth.getRole(token);
        if (roleRes.getValue() == null) {
            notifyTokenExpired(token);
            return null;
        }
        return roleRes.getValue();
    }
    // Helper method to send a real-time notification or save it as pending if the
    // user is offline.
    private Response<Void> sendOrSaveNotification(String userIdentifier, NotifyDTO notifyDTO) {
        Member member = userRepo.findUserByEmail(userIdentifier);
        boolean isGuest = (member == null);
        if (isGuest) {
            boolean isDelivered = notifier.notifyUser(userIdentifier, notifyDTO);
            if (isDelivered) {
                return new Response<>(null, "Notification sent successfully to guest");
            } else {
                logger.warning("Guest is offline. Notification dropped for: " + userIdentifier);
                return new Response<>(null, "Guest offline, notification dropped");
            }
        }
        Long savedNotificationId = null;
        boolean dbSaveFailed = false;
        try{
            Response<Long> savedNotificationIdRes = saveDelayedNotificationAsPending(userIdentifier, notifyDTO);
            savedNotificationId = (savedNotificationIdRes != null) ? savedNotificationIdRes.getValue() : null;
            if(savedNotificationId != null && savedNotificationId == -1L){
                dbSaveFailed = true;
            }
        } catch (Exception e){
            logger.severe("Database connection/commit failed outside lambda: " + e.getMessage());
            dbSaveFailed = true;
        }
        boolean isDelivered = notifier.notifyUser(userIdentifier, notifyDTO);
        if (dbSaveFailed && !isDelivered) {
            logger.severe("CRITICAL: DB transaction failed. Notification lost for: " + userIdentifier);
            return new Response<>(null, "Failed to handle notification due to DB error");
        }
        if (isDelivered) {
                markNotificationAsDelivered(userIdentifier, savedNotificationId); //if we succeed sending in real time we need to mark as delivered
                return new Response<>(null, "Notification sent successfully as DELIVERED");
            }
            logger.info("Member is offline. Notification remains PENDING for: " + userIdentifier);
            return new Response<>(null, "Notification saved as PENDING");
    }
    //for saving the notifications as pending in order to handle Persistence before trying to send in real-time
    private Response<Long> saveDelayedNotificationAsPending(String userIdentifier, NotifyDTO notifyDTO) {
        return RetryHelper.executeWithRetry(() ->
                transactionTemplate.execute(status -> {
                    try {
                        Member member = userRepo.findUserByEmail(userIdentifier);

                        if (member == null) {
                            logger.warning("User not found for identifier: " + userIdentifier);
                            return new Response<>(null, "User not found");
                        }
                        UserNotification userNotification = new UserNotification(notifyDTO.getType(),notifyDTO.getPayload());
                        member.addPendingNotification(userNotification);
                        userRepo.store(member);
                        member=userRepo.findUserByEmail(userIdentifier);
                        Long msgId=member.getMessageId(userNotification);

                        logger.info("Pending notification saved successfully for: " + member.getIdentifier());
                        return new Response<>(msgId, "Notification saved as pending");

                    } catch (OptimisticLockingFailureException e) {
                        status.setRollbackOnly();
                        throw e;
                    }catch (TransientDataAccessException e) {
                        status.setRollbackOnly();
                        logger.warning("Transient DB error detected, retrying... " + e.getMessage());
                        throw e;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        logger.severe("Fatal error during notification save: " + e.getMessage());
                        return new Response<>(-1L, "Fatal error");
                    }
                })
        );
    }

    //marking notification as delivered because we succeed in real-time
    private Response<Boolean> markNotificationAsDelivered(String userIdentifier, Long notificationId) {
        return RetryHelper.executeWithRetry(() ->
                transactionTemplate.execute(status -> {
                    try {
                        Member member = userRepo.findUserByEmail(userIdentifier);
                        if (member != null) {
                            member.setMessageStatus(notificationId,NotificationStatus.DELIVERED);
                            userRepo.store(member);
                        }
                        return new Response<>(true, "Notification marked as DELIVERED");

                    } catch (OptimisticLockingFailureException e) {
                        status.setRollbackOnly();
                        throw e;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        logger.warning("Failed to mark notification as delivered: " + e.getMessage());
                        return new Response<>(false, "Failed to mark notification as delivered");
                    }
                })
        );
    }

}
