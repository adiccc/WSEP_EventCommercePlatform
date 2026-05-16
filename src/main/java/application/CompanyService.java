package application;

import java.util.*;
import java.util.NoSuchElementException;
import java.util.logging.Logger;
import Exception.OptimisticLockingFailureException;
import domain.company.*;
import domain.dataType.PermissionType;
import domain.dto.CompanyDTO;
import domain.dto.CompanyDetailsDTO;
import domain.dto.HierarchyDTO;
import domain.dto.RolesPermissionsTreeDTO;
import DTO.DiscountDTO;
import DTO.PurchaseRuleDTO;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import domain.user.Founder;
import domain.user.IUserRepo;
import domain.user.Manager;
import domain.user.Member;
import domain.user.Owner;
import domain.user.User;

public class CompanyService {

    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final IAuth auth;
    private final IAccessValidator accessValidator;
    private final ICompanyRepo companyRepo;
    private final IUserRepo userRepo;


    public CompanyService( IAuth auth, ICompanyRepo companyRepo,
                          IUserRepo userRepo, IAccessValidator accessValidator) {
        this.auth = auth;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.accessValidator = accessValidator;
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
                if (!auth.isLoggedIn(sessionToken).getValue()) {
                    return new Response<>(null, "User must be logged in to create a company.");
                }
                if(!accessValidator.hasWriteAccess(userId)){
                    logger.warning("User " + userId + " does not have write access.");
                    return new Response<>(null, "user does not have write access.");
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

                    user.changeState(new Founder());

                    companyRepo.store(newCompany);
                    userRepo.store(user);

                    logger.info("Company " + companyName + " created successfully");
                    return Response.ok(newCompany);
                }

            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch(Exception e){
                logger.severe("Failed to create company " + companyName + ". Error: " + e.getMessage());
                return new Response<>(null, "System error occurred: " + e.getMessage());
            }
        });
    }

    public Response<CompanyDetailsDTO> getProductionCompany(String sessionToken, int companyId) {
        return RetryHelper.executeWithRetry(()-> {
            logger.info("viewRolesAndPermissionsTree called for companyId: " + companyId);
            try {
                // 1. Validate token
                int userId = auth.getUserId(sessionToken).getValue();
                if (userId == -1) {
                    logger.warning("viewRolesAndPermissionsTree failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                // 2. Company must exist
                Company company = companyRepo.findById(companyId);
                Member member = userRepo.findById(userId);
                member.changeState(company.getCompanyPermission().getUserState(userId));
                userRepo.store(member);
                logger.info("viewRolesAndPermissionsTree successful");
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
                // 1. Validate token
                int userId = auth.getUserId(token).getValue();
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

    public Response<Boolean> addRuleToCompany(String token, int companyId, PurchaseRuleDTO ruleDTO) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("addRuleToCompany called for companyId: " + companyId);
            try {
                int userId = auth.getUserId(token).getValue();
                if (userId == -1) {
                    logger.warning("addRuleToCompany failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                if(!accessValidator.hasWriteAccess(userId)){
                    logger.warning("addRuleToCompany failed: invalid or expired token");
                    return new Response<>(null, "user does not have write access.");
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
                logger.warning("addRuleToCompany unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warning("addRuleToCompany invalid data: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalStateException e) {
                logger.warning("addRuleToCompany invalid state: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
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
                int userId = auth.getUserId(token).getValue();
                if (userId == -1) {
                    logger.warning("removeRuleFromCompany failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                if(!accessValidator.hasWriteAccess(userId)){
                    logger.warning("removeRuleFromCompany failed: invalid or expired token");
                    return new Response<>(null, "user does not have write access.");
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
                logger.warning("removeRuleFromCompany unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warning("removeRuleFromCompany invalid data: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalStateException e) {
                logger.warning("removeRuleFromCompany invalid state: " + e.getMessage());
                return Response.error(e.getMessage());

            } catch (OptimisticLockingFailureException e) {
                throw e;
            }
            catch (Exception e) {
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
                int userId = auth.getUserId(token).getValue();
                if (userId == -1) {
                    logger.warning("addDiscountToCompany failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                if(!accessValidator.hasWriteAccess(userId)){
                    logger.warning("addDiscountToCompany failed: invalid or expired token");
                    return new Response<>(null, "user does not have write access.");
                }

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

            } catch (OptimisticLockingFailureException e) {
                throw e;
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
                int userId = auth.getUserId(token).getValue();
                if (userId == -1) {
                    logger.warning("removeDiscountFromCompany failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                if(!accessValidator.hasWriteAccess(userId)){
                    logger.warning("removeDiscountFromCompany failed: invalid or expired token");
                    return new Response<>(null, "user does not have write access.");
                }

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

            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in removeDiscountFromCompany: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<List<CompanyDTO>> getAvailableCompanies(String token) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("getAvailableCompanies called");
            try {
                String role = auth.getRole(token).getValue();
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
                int userId = auth.getUserId(token).getValue(); //for guest returns -1
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
        return RetryHelper.executeWithRetry(() -> {
            logger.info("updateManagerPermissions called for companyId: " + companyId + ", managerId: " + managerId);
            try {
                if (!auth.isLoggedIn(token).getValue()) {
                    logger.warning("updateManagerPermissions failed: user is not logged in");
                    return Response.error("User is not logged in");
                }
                int userId = auth.getUserId(token).getValue();
                if (userId == -1) {
                    logger.warning("updateManagerPermissions failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                if(!accessValidator.hasWriteAccess(userId)){
                    return new Response<>(null, "user does not have write access.");
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
                logger.info("updateManagerPermissions succeeded for managerId: " + managerId);
                return Response.ok(true);
            } catch (NoSuchElementException e) {
                logger.warning("updateManagerPermissions failed: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (SecurityException e) {
                logger.warning("updateManagerPermissions unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warning("updateManagerPermissions invalid argument: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in updateManagerPermissions: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> requestAppointOwner(String token, int companyId, int appointeeId) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("requestAppointOwner called for companyId: " + companyId + ", appointeeId: " + appointeeId);
            try {
                if (!auth.isLoggedIn(token).getValue()) {
                    logger.warning("requestAppointOwner failed: user is not logged in");
                    return Response.error("User is not logged in");
                }
                int appointerId = auth.getUserId(token).getValue();
                if (appointerId == -1) {
                    logger.warning("requestAppointOwner failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                if(!accessValidator.hasWriteAccess(appointerId)){
                    logger.warning("requestAppointOwner failed: appointee " + appointeeId + " doesnt have write access");
                    return new Response<>(null, "user does not have write access.");
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
                logger.info("requestAppointOwner succeeded: pending appointment created for " + appointeeId);
                return Response.ok(true);
            } catch (SecurityException e) {
                logger.warning("requestAppointOwner unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalStateException e) {
                logger.warning("requestAppointOwner invalid state: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in requestAppointOwner: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> respondToOwnerAppointment(String token, int companyId, boolean accept) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("respondToOwnerAppointment called for companyId: " + companyId + ", accept: " + accept);
            try {
                if (!auth.isLoggedIn(token).getValue()) {
                    logger.warning("respondToOwnerAppointment failed: user is not logged in");
                    return Response.error("User is not logged in");
                }
                int userId = auth.getUserId(token).getValue();
                if (userId == -1) {
                    logger.warning("respondToOwnerAppointment failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                if(!accessValidator.hasWriteAccess(userId)){
                    logger.warning("respondToOwnerAppointment failed: user does not have write access");
                    return new Response<>(null, "user does not have write access.");
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
                    logger.info("respondToOwnerAppointment: user " + userId + " accepted and became owner of company " + companyId);
                } else {
                    logger.info("respondToOwnerAppointment: user " + userId + " rejected appointment for company " + companyId);
                }
                companyRepo.store(company);
                return Response.ok(accept);
            } catch (NoSuchElementException e) {
                logger.warning("respondToOwnerAppointment failed: company not found, id: " + companyId);
                return Response.error("Company not found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in respondToOwnerAppointment: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> requestAppointManager(String token, int companyId, int appointeeId,
                                                    Set<PermissionType> permissions) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("requestAppointManager called for companyId: " + companyId + ", appointeeId: " + appointeeId);
            try {
                if (!auth.isLoggedIn(token).getValue()) {
                    logger.warning("requestAppointManager failed: user is not logged in");
                    return Response.error("User is not logged in");
                }
                int appointerId = auth.getUserId(token).getValue();
                if (appointerId == -1) {
                    logger.warning("requestAppointManager failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                if(!accessValidator.hasWriteAccess(appointerId)){
                    logger.warning("requestAppointManager failed: appointee does not have write access");
                    return new Response<>(null, "user does not have write access.");
                }
                Company company;
                try {
                    company = companyRepo.findById(companyId);
                } catch (NoSuchElementException e) {
                    logger.warning("requestAppointManager failed: company not found, id: " + companyId);
                    return Response.error("Company not found");
                }
                try {
                    userRepo.findById(appointeeId);
                } catch (NoSuchElementException e) {
                    logger.warning("requestAppointManager failed: appointee not found, id: " + appointeeId);
                    return Response.error("Only a registered subscriber can be appointed");
                }
                company.requestAppointManager(appointerId, appointeeId, permissions);
                companyRepo.store(company);
                logger.info("requestAppointManager succeeded for appointeeId: " + appointeeId);
                return Response.ok(true);
            } catch (SecurityException e) {
                logger.warning("requestAppointManager unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalStateException e) {
                logger.warning("requestAppointManager invalid state: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                logger.warning("requestAppointManager invalid argument: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in requestAppointManager: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> respondToManagerAppointment(String token, int companyId, boolean accept) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("respondToManagerAppointment called for companyId: " + companyId + ", accept: " + accept);
            try {
                if (!auth.isLoggedIn(token).getValue()) {
                    logger.warning("respondToManagerAppointment failed: user is not logged in");
                    return Response.error("User is not logged in");
                }
                int userId = auth.getUserId(token).getValue();
                if (userId == -1) {
                    logger.warning("respondToManagerAppointment failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                if(!accessValidator.hasWriteAccess(userId)){
                    logger.warning("respondToManagerAppointment failed: user doesnt have write access");
                    return new Response<>(null, "user does not have write access.");
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
                logger.info("respondToManagerAppointment succeeded for userId: " + userId + ", accepted: " + accept);
                return Response.ok(accept);
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in respondToManagerAppointment: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> removeManagerAppointment(String token, int companyId, int managerId) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("removeManagerAppointment called for companyId: " + companyId + ", managerId: " + managerId);
            try {
                if (!auth.isLoggedIn(token).getValue()) {
                    logger.warning("removeManagerAppointment failed: user is not logged in");
                    return Response.error("User is not logged in");
                }
                int actingOwnerId = auth.getUserId(token).getValue();
                if (actingOwnerId == -1) {
                    logger.warning("removeManagerAppointment failed: invalid or expired token");
                    return Response.error("Invalid or expired token");
                }
                if(!accessValidator.hasWriteAccess(actingOwnerId)){
                    logger.warning("removeManagerAppointment failed: user doesnt have write access");
                    return new Response<>(null, "user does not have write access.");
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
                logger.info("removeManagerAppointment succeeded for managerId: " + managerId);
                return Response.ok(true);
            } catch (SecurityException e) {
                logger.warning("removeManagerAppointment unauthorized: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (IllegalStateException e) {
                logger.warning("removeManagerAppointment invalid state: " + e.getMessage());
                return Response.error(e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in removeManagerAppointment: " + e.getMessage());
                return Response.error("Unexpected error: " + e.getMessage());
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
            if(!accessValidator.hasWriteAccess(auth.getUserId(ownerToken).getValue())){
                logger.warning("deactivateCompany failed: user doesnt have write access");
                return new Response<>(null, "user does not have write access.");
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
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Unexpected error in deactivateCompany: " + e.getMessage());
                return new Response<>(false, "Unexpected error: " + e.getMessage());
            }
        });
    }
}
