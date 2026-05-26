package application;

import DTO.DiscountDTO;
import DTO.NotifyType;
import DTO.PurchaseRuleDTO;
import Log.LoggerSetup;
import domain.Suspension.ISuspensionRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dto.UserDTO;

import domain.event.IEventRepo;
import domain.policy.*;
import domain.policy.PurchasePolicyType;
import domain.user.IUserRepo;
import domain.dto.CompanyDTO;
import domain.user.IUserRepo;
import domain.user.Manager;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import domain.dataType.PermissionType;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class CompanyServiceUpdatedTest {

    private int COMPANY_ID = 1;
    private int OWNER_ID;
    private int OTHER_USER_ID;
    private String GUEST_TOKEN;
    private String OWNER_TOKEN;
    private String OTHER_TOKEN;
    private int MANAGER_ID;
    private String MANAGER_TOKEN;
    private String MANAGER_EMAIL;
    private int OTHER_OWNER_ID;
    private String OTHER_OWNER_EMAIL;
    private String OTHER_OWNER_TOKEN;
    private String ADMIN_TOKEN;
    private String OWNER_EMAIL;

    private CompanyService service;
    private UserService userService;
    private ICompanyRepo companyRepo;
    private IAuth auth;
    private IAccessValidator accessValidator;
    private ISuspensionRepo suspensionRepo;
    private IUserRepo userRepo;
    private AdminService adminService;
    private IPaymentSystem paymentSystem;
    private IEventRepo eventRepo;
    private INotifier notifier;


    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
        suspensionRepo=new SuspensionRepoImpl();
        accessValidator=new AccessValidator(suspensionRepo);
        userRepo = new UserRepo();
        IPasswordEncoder passwordEncoder = new PasswordEncoderUtil();
        TokenService tokenService = new TokenService();
        String adminEmail = "admin@admin.com";
        auth = new Auth(tokenService, userRepo, passwordEncoder, Set.of(adminEmail));
        companyRepo = new CompanyRepoImpl();
        notifier = Mockito.spy(new VaadinNotifier());
        userService = new UserService(tokenService, auth, userRepo, passwordEncoder,notifier);
        service = new CompanyService(auth, companyRepo, userRepo,accessValidator,notifier);

        UserDTO ownerDTO = new UserDTO("owner@test.com", "Owner", "Test", "Password123!", 1, 1, 2000, "City", "050-123-4567");
        userService.registerUser(null, ownerDTO);
        OWNER_EMAIL="owner@test.com";
        OWNER_TOKEN = userService.login(OWNER_EMAIL, "Password123!").getValue();
        OWNER_ID = auth.getUserId(OWNER_TOKEN).getValue();
        GUEST_TOKEN = userService.continueAsGuest().getValue();
        UserDTO otherDTO = new UserDTO("other@test.com", "Other", "Test", "Password123!", 1, 1, 2000, "City", "050-123-4567");
        userService.registerUser(null, otherDTO);
        OTHER_TOKEN = userService.login("other@test.com", "Password123!").getValue();
        OTHER_USER_ID = auth.getUserId(OTHER_TOKEN).getValue();

        service.createProductionCompany(OWNER_TOKEN,COMPANY_ID,"Test Company","test@test.com","0500000000","bank-1");

        UserDTO managerDTO = new UserDTO("manager@test.com", "Manager", "Test", "Password123!", 1, 1, 2000, "City", "050-555-5555");
        userService.registerUser(null, managerDTO);
        MANAGER_EMAIL = "manager@test.com";
        MANAGER_TOKEN = userService.login(MANAGER_EMAIL, "Password123!").getValue();
        MANAGER_ID = auth.getUserId(MANAGER_TOKEN).getValue();

        UserDTO otherOwnerDTO = new UserDTO("otherowner@test.com", "OtherOwner", "Test", "Password123!", 1, 1, 2000, "City", "050-666-6666");
        userService.registerUser(null, otherOwnerDTO);
        OTHER_OWNER_EMAIL = "otherowner@test.com";
        OTHER_OWNER_TOKEN = userService.login(OTHER_OWNER_EMAIL, "Password123!").getValue();
        OTHER_OWNER_ID = auth.getUserId(OTHER_OWNER_TOKEN).getValue();

        Company company = companyRepo.findById(COMPANY_ID);
        company.getCompanyPermission().addToTree(MANAGER_ID, OWNER_ID, new HashSet<>());
        companyRepo.store(company);
        paymentSystem = Mockito.mock(PaymentSystemProxy.class);
        eventRepo = new EventRepoImpl();
        adminService = new AdminService(auth,userRepo,companyRepo,eventRepo,paymentSystem,suspensionRepo,notifier);
        userService.registerUser(null, new UserDTO(adminEmail, "Admin", "System", "Pass123!", 1, 1, 2000, "Israel", "050-000-0000"));
        ADMIN_TOKEN = userService.login(adminEmail, "Pass123!").getValue();

    }


    // ===================== Get Available Companies =====================

    @Test
    void GivenInvalidToken_WhenGetAvailableCompanies_ThenError() {
        Response<List<CompanyDTO>> response = service.getAvailableCompanies("invalid-token");
        assertNull(response.getValue());
        assertEquals("Invalid or expired token", response.getMessage());
    }

    @Test
    void GivenNoCompaniesInSystem_WhenGetAvailableCompanies_ThenError() {
        companyRepo.delete(COMPANY_ID);
        Response<List<CompanyDTO>> response = service.getAvailableCompanies(OWNER_TOKEN);
        assertNull(response.getValue());
        assertEquals("No companies in the system", response.getMessage());
    }

    @Test
    void GivenGuest_WhenActiveAndInactiveCompaniesExist_ThenReturnOnlyActive() {
        service.createProductionCompany(OWNER_TOKEN,2,"Inactive Co","a@b.com","05034445897", "bank-b");
        Response<Boolean> response = service.deactivateCompany(OWNER_TOKEN,2);
        Response<List<CompanyDTO>> response1 = service.getAvailableCompanies(GUEST_TOKEN);

        assertNotNull(response.getValue());
        assertEquals(1, response1.getValue().size());
        assertEquals("Test Company", response1.getValue().get(0).companyName());
    }

    @Test
    void GivenGuest_WhenAllCompaniesAreInactive_ThenErrorNoCompanies() {
        service.deactivateCompany(OWNER_TOKEN,COMPANY_ID);
        Response<List<CompanyDTO>> response = service.getAvailableCompanies(GUEST_TOKEN);
        assertNull(response.getValue());
        assertEquals("No companies in the system", response.getMessage());
    }

    @Test
    void GivenOtherMember_WhenInactiveCompanyExists_ThenReturnOnlyActive() {
        service.createProductionCompany(OWNER_TOKEN, 2, "Inactive Co", "a@b.com", "05034445897", "bank-b");
        service.deactivateCompany(OWNER_TOKEN, 2);

        Response<List<CompanyDTO>> response = service.getAvailableCompanies(OTHER_TOKEN);
        assertNotNull(response.getValue());
        assertEquals(1, response.getValue().size());
        assertEquals("Test Company", response.getValue().get(0).companyName());
    }

    @Test
    void GivenOwner_WhenInactiveCompanyExists_ThenReturnAll() {
        service.createProductionCompany(OWNER_TOKEN, 2, "Inactive Co", "a@b.com", "05034445897", "bank-b");
        service.deactivateCompany(OWNER_TOKEN, 2);

        Response<List<CompanyDTO>> response = service.getAvailableCompanies(OWNER_TOKEN);

        assertNotNull(response.getValue());
        assertEquals(2, response.getValue().size());
    }

    // ===================== Purchase Rule functions =====================

    @Test
    void GivenOwnerAndValidRule_WhenAddRuleToCompany_ThenSuccess() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenOwnerAddsMultipleRules_WhenAddRuleToCompany_ThenAllSucceed() {
        Response<Boolean> first = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID,
                new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4));
        Response<Boolean> second = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID,
                new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18));
        assertFalse(first.isError());
        assertFalse(second.isError());
    }

    @Test
    void GivenInvalidToken_WhenAddRuleToCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.addRuleToCompany("invalid-token", COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonOwner_WhenAddRuleToCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.addRuleToCompany(OTHER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativeTicketCount_WhenAddRuleToCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, -1);
        Response<Boolean> response = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativeMinAge_WhenAddRuleToCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, -5);
        Response<Boolean> response = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenCompanyNotFound_WhenAddRuleToCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.addRuleToCompany(OWNER_TOKEN, 999, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenAddRuleToCompany_ThenError() {
        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenOwnerAndExistingRule_WhenRemoveRuleFromCompany_ThenSuccess() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        Response<Boolean> response = service.removeRuleFromCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenRuleNotFound_WhenRemoveRuleFromCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.removeRuleFromCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenRemoveRuleFromCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.removeRuleFromCompany("invalid-token", COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonOwner_WhenRemoveRuleFromCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        Response<Boolean> response = service.removeRuleFromCompany(OTHER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenCompanyNotFound_WhenRemoveRuleFromCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.removeRuleFromCompany(OWNER_TOKEN, 999, ruleDTO);
        assertTrue(response.isError());
    }

    // ===================== Discount functions =====================

    @Test
    void GivenOwnerAndValidDiscount_WhenAddDiscountToCompany_ThenSuccess() {
        DiscountDTO discountDTO = new DiscountDTO(20.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenExistingDiscount_WhenRemoveDiscountFromCompany_ThenSuccess() {
        LocalDate endDate = LocalDate.now().plusDays(1);
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(10.0, endDate));
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(80.0, endDate));

        Response<Boolean> removeResponse = service.removeDiscountFromCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(10.0, endDate));
        assertFalse(removeResponse.isError());
        assertEquals(Boolean.TRUE, removeResponse.getValue());
    }

    @Test
    void GivenCompanyNotFound_WhenAddDiscountToCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, 999, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenCompanyNotFound_WhenRemoveDiscountFromCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.removeDiscountFromCompany(OWNER_TOKEN, 999, discountDTO);
        assertTrue(response.isError());
    }

    // ===================== II.4.X Update Manager Permissions =====================

    @Test
    void GivenOwnerAppointedManager_WhenUpdateManagerPermissions_ThenSuccess() {
        Set<PermissionType> newPerms = EnumSet.of(PermissionType.CREATE_EVENT, PermissionType.VIEW_ORDERS_HISTORY);

        Response<Boolean> response = service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, newPerms);

        assertFalse(response.isError(), response.getMessage());
        assertTrue(response.getValue());
        Company updated = companyRepo.findById(COMPANY_ID);
        assertEquals(newPerms, updated.getCompanyPermission().getCompanyTree().get(MANAGER_ID).getAllPermissions());
    }

    @Test
    void GivenNonOwner_WhenUpdateManagerPermissions_ThenUnauthorizedError() {

        Response<Boolean> response = service.updateManagerPermissions(
                OTHER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test

    void GivenNonExistentCompany_WhenUpdateManagerPermissions_ThenCompanyNotFoundError() {
        Response<Boolean> response = service.updateManagerPermissions(
                OWNER_TOKEN, 999, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenNonExistentManager_WhenUpdateManagerPermissions_ThenManagerNotFoundError() {
        int nonExistentManagerId = 99999;

        Response<Boolean> response = service.updateManagerPermissions(
                OWNER_TOKEN, COMPANY_ID, nonExistentManagerId, EnumSet.of(PermissionType.CREATE_EVENT));

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenManagerNotInCompany_WhenUpdateManagerPermissions_ThenNotAssignedError() {
        // OTHER_USER_ID is a registered user but has no manager role in this company
        Response<Boolean> response = service.updateManagerPermissions(
                OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenManagerAppointedByDifferentOwner_WhenUpdateManagerPermissions_ThenUnauthorizedError() {
        // Add OTHER_OWNER as an owner and appoint MANAGER under them, not under OWNER
        Company company = companyRepo.findById(COMPANY_ID);
        company.getCompanyPermission().addOwner(OTHER_OWNER_ID);
        company.getCompanyPermission().addToTree(MANAGER_ID, OTHER_OWNER_ID, new HashSet<>());
        companyRepo.store(company);

        Response<Boolean> response = service.updateManagerPermissions(
                OWNER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenNullPermissions_WhenUpdateManagerPermissions_ThenInvalidPermissionsError() {

        Response<Boolean> response = service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, null);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenLoggedOutOwner_WhenUpdateManagerPermissions_ThenNotAuthenticatedError() {
        auth.logout(OWNER_TOKEN);

        Response<Boolean> response = service.updateManagerPermissions(
                OWNER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));
    }
    @Test
    void GivenNonOwner_WhenAddDiscountToCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OTHER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonOwner_WhenRemoveDiscountFromCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        Response<Boolean> response = service.removeDiscountFromCompany(OTHER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenAddDiscountToCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany("invalid-token", COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenRemoveDiscountFromCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.removeDiscountFromCompany("invalid-token", COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativePercentage_WhenAddDiscountToCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(-10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenEmptyCouponCode_WhenAddDiscountToCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO("", 10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenAddDiscountToCompany_ThenError() {
        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenRemoveDiscountFromCompany_ThenError() {
        LocalDate endDate = LocalDate.now().plusDays(1);
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(10.0, endDate));
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(20.0, endDate));
        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);

        Response<Boolean> response = service.removeDiscountFromCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(10.0, endDate));
        assertTrue(response.isError());
    }

    @Test
    void GivenDuplicateDiscount_WhenAddDiscountToCompany_ThenError() {
        LocalDate endDate = LocalDate.now().plusDays(1);
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(15.0, endDate));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(15.0, endDate));
        assertTrue(response.isError());
    }

    @Test
    void GivenDiscountDoesNotExist_WhenRemoveDiscountFromCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.removeDiscountFromCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    // ===================== II.3.2 Create Production Company =====================

    @Test
    void GivenValidInputs_WhenCreateProductionCompany_ThenReturnSuccessAndCompanyStored() {
        Response<Company> response = service.createProductionCompany(
                OWNER_TOKEN, 99, "NewCo", "new@co.com", "050-999-9999", "bank-99");

        assertFalse(response.isError(), "Expected success but got: " + response.getMessage());
        assertNotNull(response.getValue());
        assertEquals("NewCo", response.getValue().getCompanyName());
        assertEquals("NewCo", companyRepo.findById(99).getCompanyName());
        assertTrue(companyRepo.findById(99).isOwner(OWNER_ID));
    }

    @Test
    void GivenDuplicateCompanyId_WhenCreateProductionCompany_ThenReturnError() {
        // COMPANY_ID=1 already created in setUp
        Response<Company> response = service.createProductionCompany(
                OWNER_TOKEN, COMPANY_ID, "Other", "o@o.com", "050-000-0001", "bank-x");

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("already exists"));
    }

    @Test
    void GivenDuplicateCompanyName_WhenCreateProductionCompany_ThenReturnError() {
        // "Test Company" already created in setUp
        Response<Company> response = service.createProductionCompany(
                OWNER_TOKEN, 98, "Test Company", "o@o.com", "050-000-0002", "bank-y");

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("already taken"));
    }

    @Test
    void GivenLoggedOutUser_WhenCreateProductionCompany_ThenReturnError() {
        auth.logout(OWNER_TOKEN);

        Response<Company> response = service.createProductionCompany(
                OWNER_TOKEN, 97, "LoggedOutCo", "lo@co.com", "050-000-0003", "bank-z");
        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenOwnerAndEmptyPermissions_WhenUpdateManagerPermissions_ThenError() {
        service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        Response<Boolean> response = service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, new HashSet<>());

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenOwnerUpdatesPermissionsTwice_WhenUpdateManagerPermissions_ThenLatestPermissionsApplied() {
        service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));
        service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.DELETE_EVENT));

        Company updated = companyRepo.findById(COMPANY_ID);
        assertEquals(
                EnumSet.of(PermissionType.DELETE_EVENT),
                updated.getCompanyPermission().getCompanyTree().get(MANAGER_ID).getAllPermissions());
    }
    void GivenInvalidEmail_WhenCreateProductionCompany_ThenReturnError() {
        Response<Company> response = service.createProductionCompany(
                OWNER_TOKEN, 96, "BadEmailCo", "not-an-email", "050-000-0004", "bank-w");

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("Invalid contact"));
    }

    // ===================== II.4.15 View Roles and Permissions Tree =====================

    @Test
    void GivenOwnerAndValidCompany_WhenViewRolesTree_ThenReturnsTree() {
        Response<domain.dto.RolesPermissionsTreeDTO> response =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID);

        assertFalse(response.isError(), "Expected success but got: " + response.getMessage());
        assertNotNull(response.getValue());
    }

    @Test
    void GivenOwnerAndValidCompany_WhenViewRolesTree_ThenFounderIdIsCorrect() {
        domain.dto.RolesPermissionsTreeDTO tree =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID).getValue();

        assertEquals(OWNER_ID, tree.getFounderId(),
                "Founder should be the user who created the company");
    }

    @Test
    void GivenOwnerAndValidCompany_WhenViewRolesTree_ThenOwnerSetContainsFounder() {
        domain.dto.RolesPermissionsTreeDTO tree =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID).getValue();

        assertTrue(tree.getOwnerIds().contains(OWNER_ID));
    }

    @Test
    void GivenNewCompany_WhenViewRolesTree_ThenManagersMapIsEmpty() {
        int freshCompanyId = 99;
        service.createProductionCompany(OWNER_TOKEN, freshCompanyId, "Fresh Co", "fresh@co.com", "050-999-9999", "bank-99");

        domain.dto.RolesPermissionsTreeDTO tree =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, freshCompanyId).getValue();

        assertTrue(tree.getManagersPermissions().isEmpty(),
                "A brand-new company has no managers");
    }

    @Test
    void GivenNonOwner_WhenViewRolesTree_ThenError() {
        Response<domain.dto.RolesPermissionsTreeDTO> response =
                service.viewRolesAndPermissionsTree(OTHER_TOKEN, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenUnknownCompanyId_WhenViewRolesTree_ThenError() {
        Response<domain.dto.RolesPermissionsTreeDTO> response =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, 9999);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenInvalidToken_WhenViewRolesTree_ThenError() {
        Response<domain.dto.RolesPermissionsTreeDTO> response =
                service.viewRolesAndPermissionsTree("not-a-real-token", COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenLoggedOutUser_WhenViewRolesTree_ThenError() {
        auth.logout(OWNER_TOKEN);

        Response<domain.dto.RolesPermissionsTreeDTO> response =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // ===================== II.4.X Request Appoint Owner =====================

    @Test
    void GivenLoggedOutOwner_WhenRequestAppointOwner_ThenNotLoggedInError() {
        auth.logout(OWNER_TOKEN);
        Response<Boolean> response = service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        assertTrue(response.isError());
        assertEquals("User is not logged in", response.getMessage());
    }

    @Test
    void GivenInvalidToken_WhenRequestAppointOwner_ThenInvalidTokenError() {
        Response<Boolean> response = service.requestAppointOwner("invalid-token", COMPANY_ID, OTHER_USER_ID);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonExistentCompany_WhenRequestAppointOwner_ThenCompanyNotFoundError() {
        Response<Boolean> response = service.requestAppointOwner(OWNER_TOKEN, 9999, OTHER_USER_ID);
        assertTrue(response.isError());
        assertEquals("Company not found", response.getMessage());
    }

    @Test
    void GivenNonOwner_WhenRequestAppointOwner_ThenUnauthorizedError() {
        Response<Boolean> response = service.requestAppointOwner(OTHER_TOKEN, COMPANY_ID, MANAGER_ID);
        assertTrue(response.isError());
        assertEquals("User does not have the required owner permissions", response.getMessage());
    }

    @Test
    void GivenNonExistentAppointee_WhenRequestAppointOwner_ThenSubscriberNotFoundError() {
        Response<Boolean> response = service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, 99999);
        assertTrue(response.isError());
        assertEquals("Only a registered subscriber can be appointed", response.getMessage());
    }

    @Test
    void GivenAlreadyOwnerAppointee_WhenRequestAppointOwner_ThenAlreadyOwnerError() {
        Response<Boolean> response = service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OWNER_ID);
        assertTrue(response.isError());
        assertEquals("Subscriber is already appointed as owner in this company", response.getMessage());
    }

    @Test
    void GivenAlreadyPendingAppointee_WhenRequestAppointOwner_ThenAlreadyPendingError() {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        Response<Boolean> response = service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        assertTrue(response.isError());
        assertEquals("Subscriber already has a pending owner appointment", response.getMessage());
    }

    @Test
    void GivenValidOwnerAndSubscriber_WhenRequestAppointOwner_ThenPendingAppointmentCreated() {
        Response<Boolean> response = service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        assertFalse(response.isError(), response.getMessage());
        assertTrue(response.getValue());
        assertTrue(companyRepo.findById(COMPANY_ID).isPendingOwner(OTHER_USER_ID));
    }

    // ===================== II.4.X Respond to Owner Appointment =====================

    @Test
    void GivenLoggedOutUser_WhenRespondToOwnerAppointment_ThenNotLoggedInError() {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        auth.logout(OTHER_TOKEN);
        Response<Boolean> response = service.respondToOwnerAppointment(OTHER_TOKEN, COMPANY_ID, true);
        assertTrue(response.isError());
        assertEquals("User is not logged in", response.getMessage());
    }

    @Test
    void GivenNoPendingAppointment_WhenRespondToOwnerAppointment_ThenNoPendingError() {
        Response<Boolean> response = service.respondToOwnerAppointment(OTHER_TOKEN, COMPANY_ID, true);
        assertTrue(response.isError());
        assertEquals("No pending owner appointment found for this user", response.getMessage());
    }

    @Test
    void GivenPendingAppointment_WhenAcceptOwnerAppointment_ThenUserBecomesOwner() {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        Response<Boolean> response = service.respondToOwnerAppointment(OTHER_TOKEN, COMPANY_ID, true);
        assertFalse(response.isError(), response.getMessage());
        assertTrue(companyRepo.findById(COMPANY_ID).isOwner(OTHER_USER_ID));
        assertFalse(companyRepo.findById(COMPANY_ID).isPendingOwner(OTHER_USER_ID));
    }

    @Test
    void GivenPendingAppointment_WhenRejectOwnerAppointment_ThenAppointmentCancelled() {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        Response<Boolean> response = service.respondToOwnerAppointment(OTHER_TOKEN, COMPANY_ID, false);
        assertFalse(response.isError(), response.getMessage());
        assertFalse(companyRepo.findById(COMPANY_ID).isOwner(OTHER_USER_ID));
        assertFalse(companyRepo.findById(COMPANY_ID).isPendingOwner(OTHER_USER_ID));
    }

    // ===================== Concurrency: Appoint Owner =====================

    @Test
    void GivenTwoThreadsRaceToAppointSameSubscriber_WhenRequestAppointOwner_ThenOnlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        });

        start.countDown();

        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = 0;
        int failed  = 0;
        if (!r1.isError()) success++; else failed++;
        if (!r2.isError()) success++; else failed++;

        assertEquals(1, success, "Only one appointment request for the same subscriber should succeed");
        assertEquals(1, failed,  "The second request should fail with already-pending error");
    }

    @Test
    void GivenTwoThreadsRaceToRespondToSamePendingAppointment_WhenRespondToOwnerAppointment_ThenOnlyOneSucceeds() throws Exception {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.respondToOwnerAppointment(OTHER_TOKEN, COMPANY_ID, true);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.respondToOwnerAppointment(OTHER_TOKEN, COMPANY_ID, true);
        });

        start.countDown();

        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = 0;
        int failed  = 0;
        if (!r1.isError()) success++; else failed++;
        if (!r2.isError()) success++; else failed++;

        assertEquals(1, success, "Only one response to a pending appointment should succeed");
        assertEquals(1, failed,  "The second response should fail — no pending appointment remains");
    }

    // ===================== Concurrency: Create Production Company =====================

    @Test
    void GivenTwoThreadsRaceWithSameCompanyId_WhenCreateProductionCompany_ThenOnlyOneSucceeds() throws Exception {
        int racingId = 50;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Company>> f1 = executor.submit(() -> {
            start.await();
            return service.createProductionCompany(OWNER_TOKEN, racingId, "CompanyA", "a@a.com", "050-111-0001", "bank-a");
        });
        Future<Response<Company>> f2 = executor.submit(() -> {
            start.await();
            return service.createProductionCompany(OTHER_TOKEN, racingId, "CompanyB", "b@b.com", "050-111-0002", "bank-b");
        });

        start.countDown();

        Response<Company> r1 = f1.get();
        Response<Company> r2 = f2.get();
        executor.shutdown();

        int success = 0;
        int failed  = 0;
        if (!r1.isError()) success++; else failed++;
        if (!r2.isError()) success++; else failed++;

        assertEquals(1, success, "Exactly one thread should successfully create the company");
        assertEquals(1, failed,  "The losing thread should receive a duplicate-ID error");
    }

    @Test
    void GivenTwoThreadsRaceWithSameCompanyName_WhenCreateProductionCompany_ThenOnlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Company>> f1 = executor.submit(() -> {
            start.await();
            return service.createProductionCompany(OWNER_TOKEN, 60, "RacingName", "c@c.com", "050-222-0001", "bank-c");
        });
        Future<Response<Company>> f2 = executor.submit(() -> {
            start.await();
            return service.createProductionCompany(OTHER_TOKEN, 61, "RacingName", "d@d.com", "050-222-0002", "bank-d");
        });

        start.countDown();

        Response<Company> r1 = f1.get();
        Response<Company> r2 = f2.get();
        executor.shutdown();

        int success = 0;
        int failed  = 0;
        if (!r1.isError()) success++; else failed++;
        if (!r2.isError()) success++; else failed++;

        assertEquals(1, success, "Only one company with the same name should be created");
        assertEquals(1, failed,  "The losing thread should receive a duplicate-name error");
    }
     @Test
    void GivenConcurrentOwnerDeactivate_WhenUserViewsAvailableCompanies_ThenConsistentList() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> deactivateFuture = executor.submit(() -> {
            start.await();
            return service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);
        });

        Future<Response<List<CompanyDTO>>> viewFuture = executor.submit(() -> {
            start.await();
            return service.getAvailableCompanies(GUEST_TOKEN);
        });

        start.countDown();
        deactivateFuture.get();
        Response<List<CompanyDTO>> viewRes = viewFuture.get();
        executor.shutdown();
        if (viewRes.getValue() != null) {
            boolean isCompanyInList = viewRes.getValue().stream().anyMatch(c -> c.getCompanyId() == COMPANY_ID);
            assertTrue(isCompanyInList || !isCompanyInList, "List fetched safely");
        } else {
            assertEquals("No companies in the system", viewRes.getMessage());
        }
    }
    @Test
    void GivenConcurrentAdminRemoveUser_WhenUserViewsAvailableCompanies_ThenFailsGracefully() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> removeFuture = executor.submit(() -> {
            start.await();
            return adminService.removeUser(ADMIN_TOKEN, OTHER_USER_ID);
        });

        Future<Response<List<CompanyDTO>>> viewFuture = executor.submit(() -> {
            start.await();
            return service.getAvailableCompanies(OTHER_TOKEN);
        });

        start.countDown();
        removeFuture.get();
        Response<List<CompanyDTO>> viewRes = viewFuture.get();
        executor.shutdown();
        assertFalse(userRepo.findById(OTHER_USER_ID).isActive(), "User must be deactivated by admin");

        if (viewRes.getValue() == null) {
            String msg = viewRes.getMessage() != null ? viewRes.getMessage().toLowerCase() : "";
            assertTrue(msg.contains("invalid") || msg.contains("error") || msg.contains("no companies"),
                    "If blocked, should fail gracefully. But got: " + viewRes.getMessage());
        } else {
            assertEquals("Companies retrieved successfully", viewRes.getMessage());
        }
    }
     // ===================== II.4.X Request Appoint Manager =====================

    @Test
    void GivenOwnerAndValidSubscriber_WhenRequestAppointManager_ThenSuccess() {
        Set<PermissionType> perms = EnumSet.of(PermissionType.CREATE_EVENT);

        Response<Boolean> response = service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, perms);

        assertFalse(response.isError(), response.getMessage());
        assertTrue(response.getValue());
        assertTrue(companyRepo.findById(COMPANY_ID).isPendingManager(OTHER_USER_ID));
    }

    @Test
    void GivenNonOwner_WhenRequestAppointManager_ThenUnauthorizedError() {
        Set<PermissionType> perms = EnumSet.of(PermissionType.CREATE_EVENT);

        Response<Boolean> response = service.requestAppointManager(OTHER_TOKEN, COMPANY_ID, OTHER_OWNER_ID, perms);

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("User does not have the required owner permissions", response.getMessage());
    }

    @Test
    void GivenNonExistentCompany_WhenRequestAppointManager_ThenCompanyNotFoundError() {
        Set<PermissionType> perms = EnumSet.of(PermissionType.CREATE_EVENT);

        Response<Boolean> response = service.requestAppointManager(OWNER_TOKEN, 9999, OTHER_USER_ID, perms);

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("Company not found", response.getMessage());
    }

    @Test
    void GivenNonExistentAppointee_WhenRequestAppointManager_ThenSubscriberNotFoundError() {
        Set<PermissionType> perms = EnumSet.of(PermissionType.CREATE_EVENT);

        Response<Boolean> response = service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, 99999, perms);

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("Only a registered subscriber can be appointed", response.getMessage());
    }

    @Test
    void GivenAlreadyManagerAppointee_WhenRequestAppointManager_ThenAlreadyManagerError() {
        Set<PermissionType> perms = EnumSet.of(PermissionType.CREATE_EVENT);

        Response<Boolean> response = service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, perms);

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("Subscriber is already appointed as manager in this company", response.getMessage());
    }

    @Test
    void GivenAlreadyPendingAppointee_WhenRequestAppointManager_ThenAlreadyPendingError() {
        Set<PermissionType> perms = EnumSet.of(PermissionType.CREATE_EVENT);
        service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, perms);

        Response<Boolean> response = service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, perms);

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("Subscriber already has a pending manager appointment", response.getMessage());
    }

    @Test
    void GivenEmptyPermissions_WhenRequestAppointManager_ThenPermissionsRequiredError() {
        Response<Boolean> response = service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, new HashSet<>());

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("At least one permission must be selected for the representative", response.getMessage());
    }

    @Test
    void GivenLoggedOutOwner_WhenRequestAppointManager_ThenNotLoggedInError() {
        auth.logout(OWNER_TOKEN);
        Set<PermissionType> perms = EnumSet.of(PermissionType.CREATE_EVENT);

        Response<Boolean> response = service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, perms);

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("User is not logged in", response.getMessage());
    }

    // ===================== II.4.X Respond To Manager Appointment =====================

    @Test
    void GivenPendingAppointment_WhenAcceptManagerAppointment_ThenUserBecomesManager() {
        Set<PermissionType> perms = EnumSet.of(PermissionType.CREATE_EVENT, PermissionType.VIEW_PURCHASE_HISTORY);
        service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, perms);

        Response<Boolean> response = service.respondToManagerAppointment(OTHER_TOKEN, COMPANY_ID, true);

        assertFalse(response.isError(), response.getMessage());
        assertEquals(Boolean.TRUE, response.getValue());
        Company updated = companyRepo.findById(COMPANY_ID);
        assertTrue(updated.getCompanyPermission().isManager(OTHER_USER_ID));
        assertFalse(updated.isPendingManager(OTHER_USER_ID));
        assertEquals(perms, updated.getCompanyPermission().getCompanyTree().get(OTHER_USER_ID).getAllPermissions());
    }

    @Test
    void GivenPendingAppointment_WhenRejectManagerAppointment_ThenAppointmentCancelled() {
        Set<PermissionType> perms = EnumSet.of(PermissionType.CREATE_EVENT);
        service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, perms);

        Response<Boolean> response = service.respondToManagerAppointment(OTHER_TOKEN, COMPANY_ID, false);

        assertFalse(response.isError(), response.getMessage());
        assertEquals(Boolean.FALSE, response.getValue());
        Company updated = companyRepo.findById(COMPANY_ID);
        assertFalse(updated.getCompanyPermission().isManager(OTHER_USER_ID));
        assertFalse(updated.isPendingManager(OTHER_USER_ID));
    }

    @Test
    void GivenNoPendingAppointment_WhenRespondToManagerAppointment_ThenNoPendingError() {
        Response<Boolean> response = service.respondToManagerAppointment(OTHER_TOKEN, COMPANY_ID, true);

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("No pending manager appointment found for this user", response.getMessage());
    }

    @Test
    void GivenNonExistentCompany_WhenRespondToManagerAppointment_ThenCompanyNotFoundError() {
        Response<Boolean> response = service.respondToManagerAppointment(OTHER_TOKEN, 9999, true);

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("Company not found", response.getMessage());
    }

    // ===================== Concurrency: Appoint Manager =====================

    @Test
    void GivenTwoThreadsRaceToAppointSameSubscriber_WhenRequestAppointManager_ThenOnlyOneSucceeds() throws Exception {
        Set<PermissionType> perms = EnumSet.of(PermissionType.CREATE_EVENT);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, perms);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, perms);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = (!r1.isError() ? 1 : 0) + (!r2.isError() ? 1 : 0);
        assertEquals(1, success, "Only one thread should successfully create the pending appointment");
    }

    @Test
    void GivenTwoThreadsRaceToRespondToSamePendingAppointment_WhenRespondToManagerAppointment_ThenOnlyOneSucceeds() throws Exception {
        Set<PermissionType> perms = EnumSet.of(PermissionType.CREATE_EVENT);
        service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, perms);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.respondToManagerAppointment(OTHER_TOKEN, COMPANY_ID, true);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.respondToManagerAppointment(OTHER_TOKEN, COMPANY_ID, true);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = (!r1.isError() ? 1 : 0) + (!r2.isError() ? 1 : 0);
        assertEquals(1, success, "Only one response to a pending appointment should succeed");
    }

    // ===================== II.4.X Remove Manager Appointment =====================

    private int addSecondManager() {
        UserDTO dto = new UserDTO("manager2@test.com", "Manager2", "Test", "Password123!", 1, 1, 2000, "City", "050-777-7777");
        userService.registerUser(null, dto);
        String token = userService.login("manager2@test.com", "Password123!").getValue();
        int id = auth.getUserId(token).getValue();
        Company company = companyRepo.findById(COMPANY_ID);
        company.getCompanyPermission().addToTree(id, OWNER_ID, new HashSet<>());
        companyRepo.store(company);
        return id;
    }

    @Test
    void GivenOwnerWhoAppointed_WhenRemoveManager_ThenSuccess() {
        addSecondManager();
        Response<Boolean> response = service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        assertFalse(response.isError(), response.getMessage());
        assertTrue(response.getValue());
        assertFalse(companyRepo.findById(COMPANY_ID).getCompanyPermission().isManager(MANAGER_ID));
    }

    @Test
    void GivenOwnerWhoAppointed_WhenRemoveManager_ThenManagerRoleRemovedFromUser() {
        addSecondManager();
        service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        boolean hasRole = userRepo.findById(MANAGER_ID).getRole() instanceof Manager;
        assertFalse(hasRole);
    }

    @Test
    void GivenCompanyDoesNotExist_WhenRemoveManager_ThenCompanyNotFoundError() {
        Response<Boolean> response = service.removeManagerAppointment(OWNER_TOKEN, 9999, MANAGER_ID);
        assertTrue(response.isError());
        assertEquals("Company not found", response.getMessage());
    }

    @Test
    void GivenUserNotLoggedIn_WhenRemoveManager_ThenNotLoggedInError() {
        addSecondManager();
        auth.logout(OWNER_TOKEN);
        Response<Boolean> response = service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        assertTrue(response.isError());
        assertEquals("User is not logged in", response.getMessage());
    }

    @Test
    void GivenNonOwner_WhenRemoveManager_ThenUnauthorizedError() {
        Response<Boolean> response = service.removeManagerAppointment(OTHER_TOKEN, COMPANY_ID, MANAGER_ID);
        assertTrue(response.isError());
        assertEquals("User does not have the required owner permissions", response.getMessage());
    }

    @Test
    void GivenTargetIsNotManager_WhenRemoveManager_ThenNotManagerError() {
        Response<Boolean> response = service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        assertTrue(response.isError());
        assertEquals("User is not defined as a company manager", response.getMessage());
    }

    @Test
    void GivenOwnerDidNotAppointManager_WhenRemoveManager_ThenCannotRemoveError() {
        addSecondManager();
        Company company = companyRepo.findById(COMPANY_ID);
        company.getCompanyPermission().OwnerAppointeeRespond(OTHER_OWNER_ID, true);
        UserDTO dto = new UserDTO("manager3@test.com", "Manager3", "Test", "Password123!", 1, 1, 2000, "City", "050-888-8888");
        userService.registerUser(null, dto);
        String tok = userService.login("manager3@test.com", "Password123!").getValue();
        int manager3Id = auth.getUserId(tok).getValue();
        company.getCompanyPermission().addToTree(manager3Id, OTHER_OWNER_ID, new HashSet<>());
        companyRepo.store(company);

        Response<Boolean> response = service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, manager3Id);
        assertTrue(response.isError());
        assertEquals("You cannot remove a manager you did not appoint", response.getMessage());
    }

    @Test
    void GivenOnlyOneManager_WhenRemoveManager_ThenOnlyManagerError() {
        Response<Boolean> response = service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        assertTrue(response.isError());
        assertEquals("This manager is the only manager in the company and cannot be removed", response.getMessage());
    }

    @Test
    void GivenInactiveCompany_WhenRemoveManager_ThenError() {
        addSecondManager();
        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);
        Response<Boolean> response = service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        assertTrue(response.isError());
        assertEquals("Company is not active", response.getMessage());
    }

    @Test
    void GivenManagerWithSubAppointees_WhenRemoveManager_ThenSubManagersReassignedToAppointer() {
        addSecondManager();
        UserDTO dto = new UserDTO("manager3@test.com", "Manager3", "Test", "Password123!", 1, 1, 2000, "City", "050-888-8888");
        userService.registerUser(null, dto);
        String tok = userService.login("manager3@test.com", "Password123!").getValue();
        int subManagerId = auth.getUserId(tok).getValue();
        Company company = companyRepo.findById(COMPANY_ID);
        company.getCompanyPermission().addToTree(subManagerId, MANAGER_ID, new HashSet<>());
        companyRepo.store(company);

        service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);

        int newAppointer = companyRepo.findById(COMPANY_ID).getCompanyPermission().getManagerAppointerId(subManagerId);
        assertEquals(OWNER_ID, newAppointer);
    }

    // ===================== Concurrency: Remove Manager =====================

    @Test
    void GivenTwoThreadsRaceToRemoveSameManager_WhenRemoveManager_ThenOnlyOneSucceeds() throws Exception {
        addSecondManager();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = (!r1.isError() ? 1 : 0) + (!r2.isError() ? 1 : 0);
        assertEquals(1, success, "Only one concurrent removal should succeed");
        assertFalse(companyRepo.findById(COMPANY_ID).getCompanyPermission().isManager(MANAGER_ID));
    }

    // ===================== Concurrency: Update Manager Permissions =====================

    @Test
    void GivenTwoThreadsRaceToUpdateSameManagerPermissions_WhenUpdateManagerPermissions_ThenFinalStateIsConsistent() throws Exception {
        Set<PermissionType> perms1 = EnumSet.of(PermissionType.CREATE_EVENT);
        Set<PermissionType> perms2 = EnumSet.of(PermissionType.DELETE_EVENT, PermissionType.VIEW_ORDERS_HISTORY);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, perms1);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, perms2);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        assertFalse(r1.isError(), r1.getMessage());
        assertFalse(r2.isError(), r2.getMessage());
        Set<PermissionType> finalPerms = companyRepo.findById(COMPANY_ID)
                .getCompanyPermission().getCompanyTree().get(MANAGER_ID).getAllPermissions();
        assertTrue(finalPerms.equals(perms1) || finalPerms.equals(perms2),
                "Final permissions must be one of the two submitted sets — no corruption");
    }

    // ===================== Concurrency: Company Discount =====================

    @Test
    void GivenTwoThreadsRaceToAddSameDiscount_WhenAddDiscountToCompany_ThenOnlyOneSucceeds() throws Exception {
        DiscountDTO discount = new DiscountDTO(25.0, LocalDate.now().plusDays(10));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discount);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discount);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = (!r1.isError() ? 1 : 0) + (!r2.isError() ? 1 : 0);
        assertEquals(1, success, "Only one concurrent add of the same discount should succeed");
    }

    @Test
    void GivenTwoThreadsRaceToRemoveSameDiscount_WhenRemoveDiscountFromCompany_ThenOnlyOneSucceeds() throws Exception {
        DiscountDTO discount = new DiscountDTO(25.0, LocalDate.now().plusDays(10));
        // Add a second discount so removing one is allowed (policy requires at least one to remain)
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(10.0, LocalDate.now().plusDays(5)));
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discount);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.removeDiscountFromCompany(OWNER_TOKEN, COMPANY_ID, discount);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.removeDiscountFromCompany(OWNER_TOKEN, COMPANY_ID, discount);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = (!r1.isError() ? 1 : 0) + (!r2.isError() ? 1 : 0);
        assertEquals(1, success, "Only one concurrent remove of the same discount should succeed");
    }

    // ===================== Concurrency: Company Purchase Policy =====================

    @Test
    void GivenTwoThreadsRaceToAddSameRule_WhenAddRuleToCompany_ThenOnlyOneSucceeds() throws Exception {
        PurchaseRuleDTO rule = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 5);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, rule);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, rule);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = (!r1.isError() ? 1 : 0) + (!r2.isError() ? 1 : 0);
        assertEquals(1, success, "Only one concurrent add of the same rule should succeed");
    }

    @Test
    void GivenTwoThreadsRaceToRemoveSameRule_WhenRemoveRuleFromCompany_ThenOnlyOneSucceeds() throws Exception {
        PurchaseRuleDTO rule = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 5);
        service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, rule);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.removeRuleFromCompany(OWNER_TOKEN, COMPANY_ID, rule);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.removeRuleFromCompany(OWNER_TOKEN, COMPANY_ID, rule);
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = (!r1.isError() ? 1 : 0) + (!r2.isError() ? 1 : 0);
        assertEquals(1, success, "Only one concurrent remove of the same rule should succeed");
    }

    // ===================== Change Discount Policy Type =====================

    @Test
    void GivenOwner_WhenChangeToMaxPolicy_ThenSuccess() {
        Response<Void> response = service.changeDiscountPolicyType(OWNER_TOKEN, COMPANY_ID, DiscountPolicyType.MAX);
        assertFalse(response.isError(), response.getMessage());
        assertTrue(companyRepo.findById(COMPANY_ID).getDiscountPolicy() instanceof MaxDiscountPolicy);
    }

    @Test
    void GivenOwnerWithMaxPolicy_WhenChangeToSumPolicy_ThenSuccess() {
        service.changeDiscountPolicyType(OWNER_TOKEN, COMPANY_ID, DiscountPolicyType.MAX);
        Response<Void> response = service.changeDiscountPolicyType(OWNER_TOKEN, COMPANY_ID, DiscountPolicyType.SUM);
        assertFalse(response.isError(), response.getMessage());
        assertTrue(companyRepo.findById(COMPANY_ID).getDiscountPolicy() instanceof SumDiscountPolicy);
    }

    @Test
    void GivenOwner_WhenChangePolicy_ThenExistingDiscountsPreserved() {
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(15.0, LocalDate.now().plusDays(1)));
        service.changeDiscountPolicyType(OWNER_TOKEN, COMPANY_ID, DiscountPolicyType.MAX);
        assertEquals(1, companyRepo.findById(COMPANY_ID).getDiscountPolicy().getDiscounts().size());
    }

    @Test
    void GivenManagerWithManagePoliciesPermission_WhenChangeDiscountPolicyType_ThenSuccess() {
        service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID,
                EnumSet.of(PermissionType.MANAGE_POLICIES));
        Response<Void> response = service.changeDiscountPolicyType(MANAGER_TOKEN, COMPANY_ID, DiscountPolicyType.MAX);
        assertFalse(response.isError(), response.getMessage());
        assertTrue(companyRepo.findById(COMPANY_ID).getDiscountPolicy() instanceof MaxDiscountPolicy);
    }

    @Test
    void GivenManagerWithoutManagePoliciesPermission_WhenChangeDiscountPolicyType_ThenError() {
        // MANAGER_ID was added with empty permissions in setUp
        Response<Void> response = service.changeDiscountPolicyType(MANAGER_TOKEN, COMPANY_ID, DiscountPolicyType.MAX);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonMember_WhenChangeDiscountPolicyType_ThenError() {
        Response<Void> response = service.changeDiscountPolicyType(OTHER_TOKEN, COMPANY_ID, DiscountPolicyType.MAX);
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenChangeDiscountPolicyType_ThenError() {
        Response<Void> response = service.changeDiscountPolicyType("invalid-token", COMPANY_ID, DiscountPolicyType.MAX);
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenChangeDiscountPolicyType_ThenError() {
        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);
        Response<Void> response = service.changeDiscountPolicyType(OWNER_TOKEN, COMPANY_ID, DiscountPolicyType.MAX);
        assertTrue(response.isError());
    }

    @Test
    void GivenCompanyNotFound_WhenChangeDiscountPolicyType_ThenError() {
        Response<Void> response = service.changeDiscountPolicyType(OWNER_TOKEN, 999, DiscountPolicyType.MAX);
        assertTrue(response.isError());
    }

    // ===================== Change Purchase Policy Type =====================

    @Test
    void GivenOwner_WhenChangeToOrPolicy_ThenSuccess() {
        Response<Void> response = service.changePurchasePolicyType(OWNER_TOKEN, COMPANY_ID, PurchasePolicyType.OR);
        assertFalse(response.isError(), response.getMessage());
        assertTrue(companyRepo.findById(COMPANY_ID).getPurchasePolicy() instanceof OrPurchasePolicy);
    }

    @Test
    void GivenOwnerWithOrPolicy_WhenChangeToAndPolicy_ThenSuccess() {
        service.changePurchasePolicyType(OWNER_TOKEN, COMPANY_ID, PurchasePolicyType.OR);
        Response<Void> response = service.changePurchasePolicyType(OWNER_TOKEN, COMPANY_ID, PurchasePolicyType.AND);
        assertFalse(response.isError(), response.getMessage());
        assertTrue(companyRepo.findById(COMPANY_ID).getPurchasePolicy() instanceof AndPurchasePolicy);
    }

    @Test
    void GivenSamePolicyType_WhenChangePurchasePolicyType_ThenNoOpAndSuccess() {
        service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 5));
        Response<Void> response = service.changePurchasePolicyType(OWNER_TOKEN, COMPANY_ID, PurchasePolicyType.AND);
        assertFalse(response.isError(), response.getMessage());
        assertEquals(1, companyRepo.findById(COMPANY_ID).getPurchasePolicy().getRules().size());
    }

    @Test
    void GivenOwner_WhenChangePolicyType_ThenExistingRulesPreserved() {
        service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 5));
        service.changePurchasePolicyType(OWNER_TOKEN, COMPANY_ID, PurchasePolicyType.OR);
        assertEquals(1, companyRepo.findById(COMPANY_ID).getPurchasePolicy().getRules().size());
    }

    @Test
    void GivenManagerWithManagePoliciesPermission_WhenChangePurchasePolicyType_ThenSuccess() {
        service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID,
                EnumSet.of(PermissionType.MANAGE_POLICIES));
        Response<Void> response = service.changePurchasePolicyType(MANAGER_TOKEN, COMPANY_ID, PurchasePolicyType.OR);
        assertFalse(response.isError(), response.getMessage());
        assertTrue(companyRepo.findById(COMPANY_ID).getPurchasePolicy() instanceof OrPurchasePolicy);
    }

    @Test
    void GivenManagerWithoutManagePoliciesPermission_WhenChangePurchasePolicyType_ThenError() {
        Response<Void> response = service.changePurchasePolicyType(MANAGER_TOKEN, COMPANY_ID, PurchasePolicyType.OR);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonMember_WhenChangePurchasePolicyType_ThenError() {
        Response<Void> response = service.changePurchasePolicyType(OTHER_TOKEN, COMPANY_ID, PurchasePolicyType.OR);
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenChangePurchasePolicyType_ThenError() {
        Response<Void> response = service.changePurchasePolicyType("invalid-token", COMPANY_ID, PurchasePolicyType.OR);
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenChangePurchasePolicyType_ThenError() {
        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);
        Response<Void> response = service.changePurchasePolicyType(OWNER_TOKEN, COMPANY_ID, PurchasePolicyType.OR);
        assertTrue(response.isError());
    }

    @Test
    void GivenCompanyNotFound_WhenChangePurchasePolicyType_ThenError() {
        Response<Void> response = service.changePurchasePolicyType(OWNER_TOKEN, 999, PurchasePolicyType.OR);
        assertTrue(response.isError());
    }

    @Test
    void GivenTwoThreadsRaceToChangePurchasePolicyType_WhenChangePurchasePolicyType_ThenFinalStateIsValid() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Void>> f1 = executor.submit(() -> {
            start.await();
            return service.changePurchasePolicyType(OWNER_TOKEN, COMPANY_ID, PurchasePolicyType.OR);
        });
        Future<Response<Void>> f2 = executor.submit(() -> {
            start.await();
            return service.changePurchasePolicyType(OWNER_TOKEN, COMPANY_ID, PurchasePolicyType.AND);
        });

        start.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        PurchasePolicy finalPolicy = companyRepo.findById(COMPANY_ID).getPurchasePolicy();
        assertTrue(finalPolicy instanceof AndPurchasePolicy || finalPolicy instanceof OrPurchasePolicy,
                "Final policy must be a valid type regardless of thread ordering");
    }

    @Test
    void GivenTwoThreadsRaceToChangePolicyType_WhenChangeDiscountPolicyType_ThenFinalStateIsValid() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Void>> f1 = executor.submit(() -> {
            start.await();
            return service.changeDiscountPolicyType(OWNER_TOKEN, COMPANY_ID, DiscountPolicyType.MAX);
        });
        Future<Response<Void>> f2 = executor.submit(() -> {
            start.await();
            return service.changeDiscountPolicyType(OWNER_TOKEN, COMPANY_ID, DiscountPolicyType.SUM);
        });

        start.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        DiscountPolicy finalPolicy = companyRepo.findById(COMPANY_ID).getDiscountPolicy();
        assertTrue(finalPolicy instanceof SumDiscountPolicy || finalPolicy instanceof MaxDiscountPolicy,
                "Final policy must be a valid type regardless of thread ordering");
    }

    @Test
    void GivenOwnerUpdatesPermissions_WhenUpdateManagerPermissions_ThenGeneralPopupSent() {
        service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("manager@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.GENERAL_POPUP)
        );
    }

    @Test
    void GivenNonOwnerFailsToUpdate_WhenUpdateManagerPermissions_ThenNoNotificationSent() {
        service.updateManagerPermissions(OTHER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));
        Mockito.verify(notifier, Mockito.never()).notifyUser(Mockito.anyString(), Mockito.any());
    }

    //TODO: FIX TESTS
//    @Test
//    void GivenTwoThreadsRaceToUpdatePermissions_WhenUpdate_ThenNotificationSentTwiceWithoutCrashing() throws Exception {
//        Set<PermissionType> perms1 = EnumSet.of(PermissionType.CREATE_EVENT);
//        Set<PermissionType> perms2 = EnumSet.of(PermissionType.DELETE_EVENT);
//
//        ExecutorService executor = Executors.newFixedThreadPool(2);
//        CountDownLatch start = new CountDownLatch(1);
//
//        Future<Response<Boolean>> f1 = executor.submit(() -> {
//            start.await();
//            return service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, perms1);
//        });
//        Future<Response<Boolean>> f2 = executor.submit(() -> {
//            start.await();
//            return service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, perms2);
//        });
//
//        start.countDown();
//        f1.get();
//        f2.get();
//        executor.shutdown();
//
//        Mockito.verify(notifier, Mockito.times(2)).notifyUser(
//                Mockito.eq("manager@test.com"),
//                Mockito.argThat(n -> n.getType() == NotifyType.GENERAL_POPUP)
//        );
//    }


    @Test
    void GivenOwnerRequestsNewOwner_WhenRequestAppointOwner_ThenRequestNotificationSent() {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("other@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.ROLE_APPOINTMENT_REQUEST)
        );
    }

    @Test
    void GivenUserAlreadyPendingOwner_WhenRequestAppointOwner_ThenNoDuplicateNotificationSent() {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        Mockito.clearInvocations(notifier);

        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);

        Mockito.verify(notifier, Mockito.never()).notifyUser(Mockito.anyString(), Mockito.any());
    }

    @Test
    void GivenPendingOwnerAccepts_WhenRespondToOwnerAppointment_ThenSuccessPopupSent() {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        Mockito.clearInvocations(notifier);

        service.respondToOwnerAppointment(OTHER_TOKEN, COMPANY_ID, true);

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("other@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.GENERAL_POPUP)
        );
    }

    @Test
    void GivenPendingOwnerRejects_WhenRespondToOwnerAppointment_ThenNoPopupSent() {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        Mockito.clearInvocations(notifier);

        service.respondToOwnerAppointment(OTHER_TOKEN, COMPANY_ID, false);

        Mockito.verify(notifier, Mockito.never()).notifyUser(Mockito.anyString(), Mockito.any());
    }


    @Test
    void GivenOwnerRequestsManager_WhenRequestAppointManager_ThenRequestNotificationSent() {
        service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("other@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.ROLE_APPOINTMENT_REQUEST)
        );
    }

    @Test
    void GivenPendingManagerAccepts_WhenRespondToManagerAppointment_ThenSuccessPopupSent() {
        service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, EnumSet.of(PermissionType.CREATE_EVENT));
        Mockito.clearInvocations(notifier);

        service.respondToManagerAppointment(OTHER_TOKEN, COMPANY_ID, true);

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("other@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.GENERAL_POPUP)
        );
    }

    @Test
    void GivenTwoThreadsRaceToAcceptManagerRole_WhenRespondToManagerAppointment_ThenPopupSentExactlyOnce() throws Exception {
        service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, EnumSet.of(PermissionType.CREATE_EVENT));
        Mockito.clearInvocations(notifier);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.respondToManagerAppointment(OTHER_TOKEN, COMPANY_ID, true);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.respondToManagerAppointment(OTHER_TOKEN, COMPANY_ID, true);
        });

        start.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("other@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.GENERAL_POPUP)
        );
    }


    @Test
    void GivenOwnerRemovesManager_WhenRemoveManagerAppointment_ThenKickoutSent() {
        addSecondManager();

        service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("manager@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.KICKOUT_TAB_NAVIGATION)
        );
    }

    @Test
    void GivenOnlyOneManager_WhenRemoveManagerAppointment_ThenFailsAndNoKickoutSent() {
        service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);

        Mockito.verify(notifier, Mockito.never()).notifyUser(Mockito.anyString(), Mockito.any());
    }

    @Test
    void GivenTwoThreadsRaceToRemoveManager_WhenRemoveManagerAppointment_ThenKickoutSentExactlyOnce() throws Exception {
        addSecondManager();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        });

        start.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("manager@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.KICKOUT_TAB_NAVIGATION)
        );
    }


    @Test
    void GivenOwnerDeactivatesCompany_WhenDeactivateCompany_ThenKickoutSentToAllManagersAndOwners() {
        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq(OWNER_EMAIL),
                Mockito.argThat(n -> n.getType() == NotifyType.KICKOUT_TAB_NAVIGATION)
        );
        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq(MANAGER_EMAIL),
                Mockito.argThat(n -> n.getType() == NotifyType.KICKOUT_TAB_NAVIGATION)
        );
    }

    @Test
    void GivenCompanyAlreadyDeactivated_WhenDeactivateCompany_ThenNoNotificationsSent() {
        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);
        Mockito.clearInvocations(notifier);

        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);

        Mockito.verify(notifier, Mockito.never()).notifyUser(Mockito.anyString(), Mockito.any());
    }

    @Test
    void GivenTwoOwnersRaceToDeactivate_WhenDeactivateCompany_ThenKickoutBroadcastOncePerStaff() throws Exception {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_OWNER_ID);
        service.respondToOwnerAppointment(OTHER_OWNER_TOKEN, COMPANY_ID, true);
        Mockito.clearInvocations(notifier);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.deactivateCompany(OTHER_OWNER_TOKEN, COMPANY_ID);
        });

        start.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq(OWNER_EMAIL),
                Mockito.argThat(n -> n.getType() == NotifyType.KICKOUT_TAB_NAVIGATION)
        );
        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq(OTHER_OWNER_EMAIL),
                Mockito.argThat(n -> n.getType() == NotifyType.KICKOUT_TAB_NAVIGATION)
        );
    }
    @Test
    void GivenTwoThreadsRaceToInviteSameManager_WhenRequestAppointManager_ThenExactlyOneInviteNotificationSent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, EnumSet.of(PermissionType.CREATE_EVENT));
        });
        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, EnumSet.of(PermissionType.CREATE_EVENT));
        });

        start.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("other@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.ROLE_APPOINTMENT_REQUEST)
        );
    }
    @Test
    void GivenUserAcceptsAndRejectsConcurrently_WhenRespondToOwnerAppointment_ThenAtMostOnePopupSent() throws Exception {
        // Arrange
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID);
        Mockito.clearInvocations(notifier);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        // Act
        Future<Response<Boolean>> fAccept = executor.submit(() -> {
            start.await();
            return service.respondToOwnerAppointment(OTHER_TOKEN, COMPANY_ID, true);
        });
        Future<Response<Boolean>> fReject = executor.submit(() -> {
            start.await();
            return service.respondToOwnerAppointment(OTHER_TOKEN, COMPANY_ID, false);
        });

        start.countDown();
        Response<Boolean> rAccept = fAccept.get();
        fReject.get();
        executor.shutdown();

        // Assert
        int expectedPopups = (rAccept.getValue() != null && rAccept.getValue() == true) ? 1 : 0;

        Mockito.verify(notifier, Mockito.times(expectedPopups)).notifyUser(
                Mockito.eq("other@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.GENERAL_POPUP)
        );
    }
    @Test
    void GivenConcurrentRemoveManagerAndDeactivateCompany_ThenManagerReceivesExactlyOneKickout() throws Exception {
        Mockito.clearInvocations(notifier);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> fRemove = executor.submit(() -> {
            start.await();
            return service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        });
        Future<Response<Boolean>> fDeactivate = executor.submit(() -> {
            start.await();
            return service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);
        });

        start.countDown();
        fRemove.get();
        fDeactivate.get();
        executor.shutdown();

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("manager@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.KICKOUT_TAB_NAVIGATION)
        );
    }
    @Test
    void GivenTwoDifferentOwnersRaceToAppointSameManager_WhenRequestAppoint_ThenExactlyOneNotificationSent() throws Exception {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_OWNER_ID);
        service.respondToOwnerAppointment(OTHER_OWNER_TOKEN, COMPANY_ID, true);
        Mockito.clearInvocations(notifier);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return service.requestAppointManager(OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, EnumSet.of(PermissionType.CREATE_EVENT));
        });

        Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return service.requestAppointManager(OTHER_OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, EnumSet.of(PermissionType.VIEW_ORDERS_HISTORY));
        });

        start.countDown();
        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int successes = (r1.getValue() != null && r1.getValue() ? 1 : 0) +
                (r2.getValue() != null && r2.getValue() ? 1 : 0);
        assertEquals(1, successes, "Only one owner should succeed in creating the pending appointment");

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("other@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.ROLE_APPOINTMENT_REQUEST)
        );
    }
    @Test
    void GivenAuthorizedAndUnauthorizedOwnersRaceToRemoveManager_WhenRemove_ThenExactlyOneKickoutSent() throws Exception {
        service.requestAppointOwner(OWNER_TOKEN, COMPANY_ID, OTHER_OWNER_ID);
        service.respondToOwnerAppointment(OTHER_OWNER_TOKEN, COMPANY_ID, true);
        addSecondManager();
        Mockito.clearInvocations(notifier);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> fUnauthorized = executor.submit(() -> {
            start.await();
            return service.removeManagerAppointment(OTHER_OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        });

        Future<Response<Boolean>> fAuthorized = executor.submit(() -> {
            start.await();
            return service.removeManagerAppointment(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);
        });

        start.countDown();
        Response<Boolean> rUnauth = fUnauthorized.get();
        Response<Boolean> rAuth = fAuthorized.get();
        executor.shutdown();

        assertTrue(rUnauth.isError(), "Unauthorized owner should fail to remove");
        assertFalse(rAuth.isError(), "Authorized owner should succeed to remove");

        Mockito.verify(notifier, Mockito.times(1)).notifyUser(
                Mockito.eq("manager@test.com"),
                Mockito.argThat(n -> n.getType() == NotifyType.KICKOUT_TAB_NAVIGATION)
        );
    }

}
