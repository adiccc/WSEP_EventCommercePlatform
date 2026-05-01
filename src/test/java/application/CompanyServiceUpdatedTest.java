package application;

import Log.LoggerSetup;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dto.UserDTO;

import domain.policy.*;
import domain.user.IUserRepo;
import domain.dataType.PermissionType;
import domain.dto.CompanyDTO;
import domain.user.IUserRepo;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import domain.dataType.PermissionType;

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

    private Company company;
    private String OWNER_TOKEN;
    private String OTHER_TOKEN;
    private int MANAGER_ID;
    private String MANAGER_TOKEN;
    private int OTHER_OWNER_ID;
    private String OTHER_OWNER_TOKEN;

    private CompanyService service;
    private UserService userService;
    private ICompanyRepo companyRepo;
    private IUserRepo userRepo;
    private IAuth auth;

    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
        userRepo = new UserRepo();
        IPasswordEncoder passwordEncoder = new PasswordEncoderUtil();
        TokenService tokenService = new TokenService();
        auth = new Auth(tokenService, userRepo, passwordEncoder);
        companyRepo = new CompanyRepoImpl();

        userService = new UserService(tokenService, auth, userRepo, passwordEncoder);
        service = new CompanyService(auth, companyRepo, userRepo);

        UserDTO ownerDTO = new UserDTO("owner@test.com", "Owner", "Test", "Password123!", 1, 1, 2000, "City", "050-123-4567");
        userService.registerUser(null, ownerDTO);
        OWNER_TOKEN = userService.login("owner@test.com", "Password123!").getValue();
        OWNER_ID = auth.getUserId(OWNER_TOKEN).getValue();

        UserDTO otherDTO = new UserDTO("other@test.com", "Other", "Test", "Password123!", 1, 1, 2000, "City", "050-123-4567");
        userService.registerUser(null, otherDTO);
        OTHER_TOKEN = userService.login("other@test.com", "Password123!").getValue();
        OTHER_USER_ID = auth.getUserId(OTHER_TOKEN).getValue();

        service.createProductionCompany(OWNER_TOKEN,COMPANY_ID,"Test Company","test@test.com","0500000000","bank-1");

        UserDTO managerDTO = new UserDTO("manager@test.com", "Manager", "Test", "Password123!", 1, 1, 2000, "City", "050-555-5555");
        userService.registerUser(null, managerDTO);
        MANAGER_TOKEN = userService.login("manager@test.com", "Password123!").getValue();
        MANAGER_ID = auth.getUserId(MANAGER_TOKEN).getValue();

        UserDTO otherOwnerDTO = new UserDTO("otherowner@test.com", "OtherOwner", "Test", "Password123!", 1, 1, 2000, "City", "050-666-6666");
        userService.registerUser(null, otherOwnerDTO);
        OTHER_OWNER_TOKEN = userService.login("otherowner@test.com", "Password123!").getValue();
        OTHER_OWNER_ID = auth.getUserId(OTHER_OWNER_TOKEN).getValue();
    }

    private void appointManager(int managerId, int appointedBy) {
        Company company = companyRepo.findById(COMPANY_ID);
        company.getCompanyPermission().addToTree(managerId, appointedBy, new HashSet<>());
        companyRepo.store(company);
//        company = new Company(COMPANY_ID, "Test Company", OWNER_ID,
//                new ContactInfo("test@test.com", "0500000000", "bank-1"),
//                new PurchasePolicy(), new DiscountPolicy());
//        companyRepo.store(company);
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
        Response<List<CompanyDTO>> response = service.getAvailableCompanies("valid-token");
        assertNull(response.getValue());
        assertEquals("No companies in the system", response.getMessage());
    }

    @Test
    void GivenGuest_WhenActiveAndInactiveCompaniesExist_ThenReturnOnlyActive() {
        service.createProductionCompany(OWNER_TOKEN,2,"Inactive Co","a@b.com","05034445897", "bank-b");
        Response<Boolean> response = service.deactivateCompany(OWNER_TOKEN,2);
        Response<List<CompanyDTO>> response1 = service.getAvailableCompanies(null);

        assertNotNull(response.getValue());
        assertEquals(1, response1.getValue().size());
        assertEquals("Test Company", response1.getValue().get(0).companyName());
    }

    @Test
    void GivenGuest_WhenAllCompaniesAreInactive_ThenErrorNoCompanies() {
        service.deactivateCompany(OWNER_TOKEN,COMPANY_ID);
        Response<List<CompanyDTO>> response = service.getAvailableCompanies(null);
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

    // ===================== Update Purchase Policy =====================

    @Test
    void GivenOwnerAndValidPolicy_WhenUpdatePurchasePolicy_ThenSuccess() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));

        Response<Boolean> response = service.updatePurchasePolicy(OWNER_TOKEN, COMPANY_ID, policy);

        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenOwnerAndExistingPolicy_WhenUpdatePurchasePolicy_ThenPolicyReplaced() {
        PurchasePolicy oldPolicy = new PurchasePolicy();
        oldPolicy.addRule(new MaxTicketsRule(2));
        service.updatePurchasePolicy(OWNER_TOKEN, COMPANY_ID, oldPolicy);

        PurchasePolicy newPolicy = new PurchasePolicy();
        newPolicy.addRule(new MaxTicketsRule(4));
        newPolicy.addRule(new MinAgeRule(18));

        Response<Boolean> response = service.updatePurchasePolicy(OWNER_TOKEN, COMPANY_ID, newPolicy);

        assertFalse(response.isError());
        assertEquals(newPolicy.describe(), companyRepo.findById(COMPANY_ID).getPurchasePolicy().describe());
    }

    @Test
    void GivenInvalidToken_WhenUpdatePurchasePolicy_ThenError() {
        Response<Boolean> response = service.updatePurchasePolicy("invalid-token", COMPANY_ID, new PurchasePolicy());
        assertTrue(response.isError());
    }

    @Test
    void GivenNonOwner_WhenUpdatePurchasePolicy_ThenError() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        Response<Boolean> response = service.updatePurchasePolicy(OTHER_TOKEN, COMPANY_ID, policy);
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativeTicketCount_WhenUpdatePurchasePolicy_ThenError() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(-1));
        Response<Boolean> response = service.updatePurchasePolicy(OWNER_TOKEN, COMPANY_ID, policy);
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativeMinAge_WhenUpdatePurchasePolicy_ThenError() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MinAgeRule(-5));
        Response<Boolean> response = service.updatePurchasePolicy(OWNER_TOKEN, COMPANY_ID, policy);
        assertTrue(response.isError());
    }

    @Test
    void GivenCompanyNotFound_WhenUpdatePurchasePolicy_ThenError() {
        Response<Boolean> response = service.updatePurchasePolicy(OWNER_TOKEN, 999, new PurchasePolicy());
        assertTrue(response.isError());
    }

    // ===================== II.4.X Update Manager Permissions =====================

    @Test
    void SuccessfulManagerPermissionsUpdate() {
        appointManager(MANAGER_ID, OWNER_ID);
        Set<PermissionType> newPerms = EnumSet.of(PermissionType.CREATE_EVENT, PermissionType.VIEW_ORDERS_HISTORY);

        Response<Boolean> response = service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, newPerms);

        assertFalse(response.isError(), response.getMessage());
        assertTrue(response.getValue());
        Company updated = companyRepo.findById(COMPANY_ID);
        assertEquals(newPerms, updated.getCompanyPermission().getCompanyTree().get(MANAGER_ID).getAllPermissions());
    }

    @Test
    void UnauthorizedPermissionsUpdate() {
        appointManager(MANAGER_ID, OWNER_ID);

        Response<Boolean> response = service.updateManagerPermissions(
                OTHER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void CompanyNotFound() {
        appointManager(MANAGER_ID, OWNER_ID);

        Response<Boolean> response = service.updateManagerPermissions(
                OWNER_TOKEN, 999, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void ManagerNotFound() {
        int nonExistentManagerId = 99999;

        Response<Boolean> response = service.updateManagerPermissions(
                OWNER_TOKEN, COMPANY_ID, nonExistentManagerId, EnumSet.of(PermissionType.CREATE_EVENT));

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void ManagerNotAssignedToCompany() {
        // OTHER_USER_ID is a registered user but has no manager role in this company
        Response<Boolean> response = service.updateManagerPermissions(
                OWNER_TOKEN, COMPANY_ID, OTHER_USER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void OwnerDidNotAppointManager() {
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
    void InvalidPermissionsUpdate() {
        appointManager(MANAGER_ID, OWNER_ID);

        Response<Boolean> response = service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, null);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void LoggedOutUserAccess() {
        appointManager(MANAGER_ID, OWNER_ID);
        auth.logout(OWNER_TOKEN);

        Response<Boolean> response = service.updateManagerPermissions(
                OWNER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenOwnerAndEmptyPermissions_WhenUpdateManagerPermissions_ThenPermissionsClearedSuccessfully() {
        appointManager(MANAGER_ID, OWNER_ID);
        service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        Response<Boolean> response = service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, new HashSet<>());

        assertFalse(response.isError(), response.getMessage());
        Company updated = companyRepo.findById(COMPANY_ID);
        assertTrue(updated.getCompanyPermission().getCompanyTree().get(MANAGER_ID).getAllPermissions().isEmpty());
    }

    @Test
    void GivenOwnerUpdatesPermissionsTwice_WhenUpdateManagerPermissions_ThenLatestPermissionsApplied() {
        appointManager(MANAGER_ID, OWNER_ID);
        service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT));
        service.updateManagerPermissions(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, EnumSet.of(PermissionType.DELETE_EVENT));

        Company updated = companyRepo.findById(COMPANY_ID);
        assertEquals(
                EnumSet.of(PermissionType.DELETE_EVENT),
                updated.getCompanyPermission().getCompanyTree().get(MANAGER_ID).getAllPermissions());
    }
}
