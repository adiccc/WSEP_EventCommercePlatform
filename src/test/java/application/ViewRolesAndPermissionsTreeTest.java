package application;

import domain.company.ICompanyRepo;
import domain.dataType.PermissionType;
import domain.dto.RolesPermissionsTreeDTO;
import domain.dto.UserDTO;
import domain.user.IUserRepo;
import infrastructure.Auth;
import infrastructure.CompanyRepoImpl;
import infrastructure.PasswordEncoderUtil;
import infrastructure.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Acceptance tests for II.4.15 – View Roles and Permissions Tree.
 *
 * Use-case: a company owner requests the roles/permissions tree for their company.
 *
 * Actor         : company owner
 * Pre-conditions:
 *   - User is logged in (valid token)
 *   - Company exists in the system
 *   - Requesting user is an owner of that company
 *
 * Test categories (black-box, no mocks — full real stack):
 *   Successful_View        – owner gets the full tree (founder + owners, no managers = empty map)
 *   Company_With_No_Managers – newly created company has empty managers map
 *   Unauthorized_Access    – non-owner is rejected
 *   Company_Not_Found      – unknown companyId returns error
 *   Logged_Out_User_Access – invalid/expired token returns error
 */
class ViewRolesAndPermissionsTreeTest {

    private static final int COMPANY_ID = 1;

    private CompanyService companyService;
    private UserService    userService;
    private IAuth          auth;

    private String ownerToken;
    private String nonOwnerToken;
    private int    ownerId;

    @BeforeEach
    void setUp() {
        IUserRepo        userRepo        = new UserRepo();
        ICompanyRepo     companyRepo     = new CompanyRepoImpl();
        IPasswordEncoder passwordEncoder = new PasswordEncoderUtil();
        TokenService     tokenService    = new TokenService();

        auth           = new Auth(tokenService, userRepo, passwordEncoder);
        userService    = new UserService(tokenService, auth, userRepo, passwordEncoder);
        companyService = new CompanyService(auth, companyRepo, userRepo);

        // Register and log in the future company owner
        UserDTO ownerDTO = new UserDTO(
                "owner@test.com", "Owner", "Tester", "Password123!",
                1, 1, 1990, "Tel Aviv", "050-111-1111");
        userService.registerUser(null, ownerDTO);
        ownerToken = userService.login("owner@test.com", "Password123!").getValue();
        ownerId    = auth.getUserId(ownerToken).getValue();

        // Register and log in a user who will NOT be an owner of any company
        UserDTO nonOwnerDTO = new UserDTO(
                "viewer@test.com", "Viewer", "Tester", "Password123!",
                1, 1, 1992, "Haifa", "050-222-2222");
        userService.registerUser(null, nonOwnerDTO);
        nonOwnerToken = userService.login("viewer@test.com", "Password123!").getValue();

        // Create a company — the logged-in owner automatically becomes the founder
        companyService.createProductionCompany(
                ownerToken, COMPANY_ID, "RoleTestCompany",
                "info@roletest.com", "050-333-3333", "bank-acc-1"
        );
    }

    // ── Successful_View ────────────────────────────────────────────────────────

    @Test
    void GivenOwnerAndValidCompany_WhenViewRolesTree_ThenReturnsTree() {
        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(ownerToken, COMPANY_ID);

        assertFalse(response.isError(), "Expected success but got: " + response.getMessage());
        assertNotNull(response.getValue());
    }

    @Test
    void GivenOwnerAndValidCompany_WhenViewRolesTree_ThenFounderIdIsCorrect() {
        RolesPermissionsTreeDTO tree =
                companyService.viewRolesAndPermissionsTree(ownerToken, COMPANY_ID).getValue();

        assertEquals(ownerId, tree.getFounderId(),
                "Founder should be the user who created the company");
    }

    @Test
    void GivenOwnerAndValidCompany_WhenViewRolesTree_ThenOwnerSetContainsFounder() {
        RolesPermissionsTreeDTO tree =
                companyService.viewRolesAndPermissionsTree(ownerToken, COMPANY_ID).getValue();

        assertTrue(tree.getOwnerIds().contains(ownerId),
                "Founder is automatically an owner");
    }

    // --- Company_With_No_Managers ---

    @Test
    void GivenNewCompanyWithNoManagers_WhenViewRolesTree_ThenManagersMapIsEmpty() {
        RolesPermissionsTreeDTO tree =
                companyService.viewRolesAndPermissionsTree(ownerToken, COMPANY_ID).getValue();

        assertTrue(tree.getManagersPermissions().isEmpty(),
                "A brand-new company has no managers");
    }

    // ── Unauthorized_Access ────────────────────────────────────────────────────

    @Test
    void GivenNonOwner_WhenViewRolesTree_ThenError() {
        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(nonOwnerToken, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // ── Company_Not_Found ──────────────────────────────────────────────────────

    @Test
    void GivenUnknownCompanyId_WhenViewRolesTree_ThenError() {
        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(ownerToken, 9999);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // ── Logged_Out_User_Access ─────────────────────────────────────────────────

    @Test
    void GivenInvalidToken_WhenViewRolesTree_ThenError() {
        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree("not-a-real-token", COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenLoggedOutUser_WhenViewRolesTree_ThenError() {
        // Log the owner out so their token is no longer valid
        auth.logout(ownerToken);

        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(ownerToken, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }
}
