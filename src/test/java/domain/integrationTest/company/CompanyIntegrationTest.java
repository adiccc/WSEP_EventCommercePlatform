package domain.integrationTest.company;

import application.CompanyService;
import application.IAuth;
import application.Response;
import application.TokenService;
import application.UserService;
import application.IPasswordEncoder;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dataType.PermissionType;
import domain.dto.HierarchyDTO;
import domain.dto.RolesPermissionsTreeDTO;
import domain.dto.UserDTO;
import domain.user.IUserRepo;
import infrastructure.Auth;
import infrastructure.CompanyRepoImpl;
import infrastructure.PasswordEncoderUtil;
import infrastructure.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for II.3.2 (Create Production Company) and
 * II.4.15 (View Roles and Permissions Tree).
 *
 * Uses real in-memory repositories (CompanyRepoImpl, UserRepo) and the real
 * Auth service — Auth is an internal service, not an external dependency,
 * so it must not be mocked at the integration level. Only external services
 * (e.g. payment gateways) would be mocked here.
 */
class CompanyIntegrationTest {

    private CompanyRepoImpl companyRepo;
    private UserRepo userRepo;
    private IAuth auth;
    private CompanyService companyService;
    private UserService userService;

    private static final int COMPANY_ID = 100;

    private String founderToken;
    private String nonOwnerToken;
    private int founderId;

    @BeforeEach
    void setUp() {
        userRepo    = new UserRepo();
        companyRepo = new CompanyRepoImpl();

        IPasswordEncoder passwordEncoder = new PasswordEncoderUtil();
        TokenService tokenService = new TokenService();

        auth = new Auth(tokenService, userRepo, passwordEncoder);
        userService = new UserService(tokenService, auth, userRepo, passwordEncoder);
        companyService = new CompanyService(auth, companyRepo, userRepo);

        // Register and log in the founder
        UserDTO founderDTO = new UserDTO(
                "founder@test.com", "Founder", "User", "Password123!",
                1, 1, 1990, "Tel Aviv", "050-111-1111");
        userService.registerUser(null, founderDTO);
        founderToken = userService.login("founder@test.com", "Password123!").getValue();
        founderId = auth.getUserId(founderToken).getValue();

        // Register and log in a second user who is NOT an owner of any company
        UserDTO otherDTO = new UserDTO(
                "other@test.com", "Other", "User", "Password123!",
                1, 1, 1992, "Haifa", "050-222-2222");
        userService.registerUser(null, otherDTO);
        nonOwnerToken = userService.login("other@test.com", "Password123!").getValue();
    }

    // ══════════════════════════════════════════════════════════════
    // II.3.2  Create Production Company — Integration Tests
    // ══════════════════════════════════════════════════════════════

    // --- Successful_Creation ---
    @Test
    void GivenValidInputs_WhenCreateProductionCompany_ThenCompanyPersistedAndFounderRoleAdded() {
        Response<Company> response = companyService.createProductionCompany(
                founderToken, COMPANY_ID, "LiveNation",
                "admin@livenation.com", "0501234567", "bank-123"
        );

        assertFalse(response.isError(), "Expected success, got: " + response.getMessage());
        assertNotNull(response.getValue());
        assertEquals("LiveNation", response.getValue().getCompanyName());

        // Company is actually stored and can be retrieved
        Company stored = companyRepo.findById(COMPANY_ID);
        assertNotNull(stored);
        assertEquals("LiveNation", stored.getCompanyName());
        assertTrue(stored.isOwner(founderId), "Founder should be an owner of the new company");
    }

    // --- Duplicate_Company_Number ---
    @Test
    void GivenExistingCompanyId_WhenCreateProductionCompany_ThenReturnError() {
        companyService.createProductionCompany(founderToken, COMPANY_ID, "First",
                "first@test.com", "0501234567", "bank-1");

        Response<Company> response = companyService.createProductionCompany(
                founderToken, COMPANY_ID, "Second",
                "second@test.com", "0501234568", "bank-2"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("already exists"));
        // Original company is unchanged
        assertEquals("First", companyRepo.findById(COMPANY_ID).getCompanyName());
    }

    // --- Duplicate_Company_Name ---
    @Test
    void GivenExistingCompanyName_WhenCreateProductionCompany_ThenReturnError() {
        companyService.createProductionCompany(founderToken, COMPANY_ID, "UniqueName",
                "first@test.com", "0501234567", "bank-1");

        Response<Company> response = companyService.createProductionCompany(
                founderToken, COMPANY_ID + 1, "UniqueName",
                "second@test.com", "0501234568", "bank-2"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("already taken"));
        // Second company must NOT be stored
        assertThrows(NoSuchElementException.class, () -> companyRepo.findById(COMPANY_ID + 1));
    }

    // --- User_Not_Found ---
    @Test
    void GivenUserDeletedFromRepo_WhenCreateProductionCompany_ThenReturnError() {
        // Token is still valid in auth, but the user has been removed from the user store
        // (e.g., account deleted by an admin). Auth passes but userRepo.findById returns null.
        userRepo.delete(founderId);

        Response<Company> response = companyService.createProductionCompany(
                founderToken, COMPANY_ID, "GhostCo",
                "ghost@test.com", "0501234567", "bank-1"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("not found"));
        assertThrows(NoSuchElementException.class, () -> companyRepo.findById(COMPANY_ID));
    }

    // --- Logged_Out_User_Access ---
    @Test
    void GivenLoggedOutUser_WhenCreateProductionCompany_ThenReturnError() {
        auth.logout(founderToken);

        Response<Company> response = companyService.createProductionCompany(
                founderToken, COMPANY_ID, "NoAuthCo",
                "noauth@test.com", "0501234567", "bank-1"
        );

        assertNull(response.getValue());
        assertTrue(response.isError(), "Expected error for logged-out user");
        assertThrows(NoSuchElementException.class, () -> companyRepo.findById(COMPANY_ID));
    }

    // --- Wrong_Mandatory_Fields ---
    @Test
    void GivenInvalidEmail_WhenCreateProductionCompany_ThenReturnError() {
        Response<Company> response = companyService.createProductionCompany(
                founderToken, COMPANY_ID, "BadEmail",
                "not-an-email", "0501234567", "bank-1"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("Invalid contact"));
        assertThrows(NoSuchElementException.class, () -> companyRepo.findById(COMPANY_ID));
    }

    // ══════════════════════════════════════════════════════════════
    // II.4.15  View Roles and Permissions Tree — Integration Tests
    // ══════════════════════════════════════════════════════════════

    // --- Successful_View (founder only) ---
    @Test
    void GivenFounderToken_WhenViewRolesAndPermissionsTree_ThenReturnCorrectTree() {
        companyService.createProductionCompany(founderToken, COMPANY_ID, "RoleCo",
                "role@co.com", "050-000-0000", "bank-1");

        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(founderToken, COMPANY_ID);

        assertFalse(response.isError(), "Expected success, got: " + response.getMessage());
        RolesPermissionsTreeDTO tree = response.getValue();
        assertNotNull(tree);
        assertEquals(founderId, tree.getFounderId());
        assertTrue(tree.getOwnerIds().contains(founderId));
        assertTrue(tree.getManagersPermissions().isEmpty(),
                "No managers added yet — map should be empty");
    }

    // --- Successful_View with managers in tree ---
    @Test
    void GivenCompanyWithManager_WhenViewRolesAndPermissionsTree_ThenReturnManagerPermissions() {
        companyService.createProductionCompany(founderToken, COMPANY_ID, "MgrCo",
                "mgr@co.com", "050-000-0001", "bank-2");

        // Directly inject a manager into the stored company (simulating EventCompanyManageService)
        int managerId = 500;
        Company company = companyRepo.findById(COMPANY_ID);
        company.getCompanyPermission().getCompanyTree().put(managerId, new HierarchyDTO(
                founderId, new ArrayList<>(),
                EnumSet.of(PermissionType.MANAGE_EVENTS_INVENTORY, PermissionType.VIEW_PURCHASE_HISTORY)
        ));
        companyRepo.store(company);

        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(founderToken, COMPANY_ID);

        assertFalse(response.isError());
        assertTrue(response.getValue().getManagersPermissions().containsKey(managerId));
        assertTrue(response.getValue().getManagersPermissions().get(managerId)
                .contains(PermissionType.MANAGE_EVENTS_INVENTORY));
        assertTrue(response.getValue().getManagersPermissions().get(managerId)
                .contains(PermissionType.VIEW_PURCHASE_HISTORY));
    }

    // --- Unauthorized_Access ---
    @Test
    void GivenNonOwner_WhenViewRolesAndPermissionsTree_ThenReturnError() {
        companyService.createProductionCompany(founderToken, COMPANY_ID, "AuthCo",
                "auth@co.com", "050-000-0002", "bank-3");

        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(nonOwnerToken, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // --- Company_Not_Found ---
    @Test
    void GivenCompanyNotFound_WhenViewRolesAndPermissionsTree_ThenReturnError() {
        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(founderToken, 9999);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // --- Logged_Out_User_Access ---
    @Test
    void GivenLoggedOutUser_WhenViewRolesAndPermissionsTree_ThenReturnError() {
        companyService.createProductionCompany(founderToken, COMPANY_ID, "SecureCo",
                "secure@co.com", "050-000-0003", "bank-4");

        // Log out so the token is no longer valid
        auth.logout(founderToken);

        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(founderToken, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }
}
