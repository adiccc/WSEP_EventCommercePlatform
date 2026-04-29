package domain.integrationTest.admin;

import application.*;
import domain.company.Company;
import domain.company.ContactInfo;
import domain.company.ICompanyRepo;
import domain.company.Permissions;
import domain.dataType.PermissionType;
import domain.dto.HierarchyDTO;
import domain.event.IEventRepo;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import domain.user.IUserRepo;
import domain.user.Member;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for II.6.1 — Remove User from System.
 *
 * Tests the interaction between AdminService, Auth, UserRepo, and CompanyRepoImpl
 * directly — no application services (UserService, CompanyService) are used.
 * Setup is done by writing directly to the real repositories and calling auth.login()
 * to obtain tokens, which is the correct integration-test approach.
 *
 * IPaymentSystem is mocked because it is an external dependency (payment gateway).
 */
class RemoveUserIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String PASSWORD    = "Password123!";
    private static final int    COMPANY_ID  = 200;

    private UserRepo         userRepo;
    private CompanyRepoImpl  companyRepo;
    private IEventRepo       eventRepo;
    private IAuth            auth;
    private IPasswordEncoder passwordEncoder;
    private AdminService     adminService;

    private String adminToken;
    private String nonAdminToken;
    private int    founderId;
    private int    nonAdminId;

    @BeforeEach
    void setUp() {
        userRepo        = new UserRepo();
        companyRepo     = new CompanyRepoImpl();
        eventRepo       = new EventRepoImpl();
        passwordEncoder = new PasswordEncoderUtil();

        TokenService tokenService = new TokenService();
        auth = new Auth(tokenService, userRepo, passwordEncoder, Set.of(ADMIN_EMAIL));

        adminService = new AdminService(auth, userRepo, companyRepo, eventRepo, mock(IPaymentSystem.class));

        // Store admin user directly in the repo
        Member admin = new Member(ADMIN_EMAIL, passwordEncoder.encodePassword(PASSWORD),
                "Admin", "User", "050-000-0000", LocalDate.of(1990, 1, 1), "City");
        userRepo.store(admin);
        founderId  = admin.getUserId();
        adminToken = auth.login(ADMIN_EMAIL, PASSWORD).getValue();

        // Store a regular (non-admin) user directly in the repo
        Member regular = new Member("user@test.com", passwordEncoder.encodePassword(PASSWORD),
                "Regular", "User", "050-111-1111", LocalDate.of(1992, 3, 15), "City");
        userRepo.store(regular);
        nonAdminId    = regular.getUserId();
        nonAdminToken = auth.login("user@test.com", PASSWORD).getValue();

        // Create the company directly in the repo — founder is the admin
        Permissions perms = new Permissions(founderId);
        Company company = new Company(COMPANY_ID, "RemovalTestCo",
                new ContactInfo("co@test.com", "050-222-2222", "bank-1"),
                new PurchasePolicy(), new DiscountPolicy(), perms);
        companyRepo.store(company);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /** Store a new member directly in the repo and return their auto-assigned userId. */
    private int storeUser(String email) {
        Member m = new Member(email, passwordEncoder.encodePassword(PASSWORD),
                "Test", "User", "050-999-0000", LocalDate.of(1995, 6, 1), "City");
        userRepo.store(m);
        return m.getUserId();
    }

    // ══════════════════════════════════════════════════════════════
    // Successful_Removal
    // ══════════════════════════════════════════════════════════════

    @Test
    void GivenPlainActiveUser_WhenRemoveUser_ThenUserDeactivated() {
        int plainId = storeUser("plain@test.com");

        Response<Boolean> response = adminService.removeUser(adminToken, plainId);

        assertFalse(response.isError(), "Expected success but got: " + response.getMessage());
        assertTrue(response.getValue());
        assertFalse(userRepo.findById(plainId).isActive(),
                "Member should be deactivated in the repo after removal");
    }

    // ══════════════════════════════════════════════════════════════
    // Unauthorized
    // ══════════════════════════════════════════════════════════════

    @Test
    void GivenNonAdminToken_WhenRemoveUser_ThenReturnUnauthorized() {
        Response<Boolean> response = adminService.removeUser(nonAdminToken, founderId);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
        assertTrue(userRepo.findById(founderId).isActive(), "Founder should remain active");
    }

    @Test
    void GivenInvalidToken_WhenRemoveUser_ThenReturnUnauthorized() {
        Response<Boolean> response = adminService.removeUser("not-a-real-token", nonAdminId);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
    }

    // ══════════════════════════════════════════════════════════════
    // User_Not_Found
    // ══════════════════════════════════════════════════════════════

    @Test
    void GivenNonExistentUserId_WhenRemoveUser_ThenReturnError() {
        Response<Boolean> response = adminService.removeUser(adminToken, 9999);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("not found"));
    }

    // ══════════════════════════════════════════════════════════════
    // User_Already_Removed
    // ══════════════════════════════════════════════════════════════

    @Test
    void GivenAlreadyRemovedUser_WhenRemoveUserAgain_ThenReturnError() {
        int userId = storeUser("once@test.com");
        adminService.removeUser(adminToken, userId); // first removal — succeeds

        Response<Boolean> response = adminService.removeUser(adminToken, userId);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("already removed"));
    }

    // ══════════════════════════════════════════════════════════════
    // Founder_Blocked
    // ══════════════════════════════════════════════════════════════

    @Test
    void GivenUserIsCompanyFounder_WhenRemoveUser_ThenReturnError() {
        Response<Boolean> response = adminService.removeUser(adminToken, founderId);

        assertTrue(response.isError());
        assertTrue(response.getMessage().toLowerCase().contains("founder"),
                "Error should mention founder; got: " + response.getMessage());
        assertTrue(userRepo.findById(founderId).isActive(), "Founder must remain active");
    }

    // ══════════════════════════════════════════════════════════════
    // Owner_Cascade
    // ══════════════════════════════════════════════════════════════

    @Test
    void GivenUserIsOwner_WhenRemoveUser_ThenRemovedFromOwnerIdsAndManagerReassigned() {
        int ownerId   = storeUser("owner@test.com");
        int managerId = storeUser("mgr@test.com");

        // Inject owner + manager directly into the company's permission tree
        Company company = companyRepo.findById(COMPANY_ID);
        company.getCompanyPermission().addOwner(ownerId);
        company.getCompanyPermission().getCompanyTree().put(managerId, new HierarchyDTO(
                ownerId, new ArrayList<>(), EnumSet.noneOf(PermissionType.class)));
        companyRepo.store(company);

        Response<Boolean> response = adminService.removeUser(adminToken, ownerId);

        assertFalse(response.isError(), "Expected success but got: " + response.getMessage());
        assertFalse(userRepo.findById(ownerId).isActive());

        Company updated = companyRepo.findById(COMPANY_ID);
        assertFalse(updated.getCompanyPermission().isOwner(ownerId),
                "Removed owner should no longer be in ownerIds");
        assertEquals(founderId,
                updated.getCompanyPermission().getCompanyTree().get(managerId).getMyManager(),
                "Manager's appointer should be reassigned from removed owner to founder");
    }

    // ══════════════════════════════════════════════════════════════
    // Manager_Cascade
    // ══════════════════════════════════════════════════════════════

    @Test
    void GivenUserIsManager_WhenRemoveUser_ThenRemovedFromTreeAndSubManagerReassigned() {
        int managerId = storeUser("manager@test.com");
        int subMgrId  = storeUser("submgr@test.com");

        // manager appointed by founder; sub-manager appointed by manager
        Company company = companyRepo.findById(COMPANY_ID);
        company.getCompanyPermission().getCompanyTree().put(managerId, new HierarchyDTO(
                founderId, new ArrayList<>(List.of(subMgrId)), EnumSet.noneOf(PermissionType.class)));
        company.getCompanyPermission().getCompanyTree().put(subMgrId, new HierarchyDTO(
                managerId, new ArrayList<>(), EnumSet.noneOf(PermissionType.class)));
        companyRepo.store(company);

        Response<Boolean> response = adminService.removeUser(adminToken, managerId);

        assertFalse(response.isError(), "Expected success but got: " + response.getMessage());
        assertFalse(userRepo.findById(managerId).isActive());

        Company updated = companyRepo.findById(COMPANY_ID);
        assertFalse(updated.getCompanyPermission().getCompanyTree().containsKey(managerId),
                "Manager should be removed from companyTree");
        assertEquals(founderId,
                updated.getCompanyPermission().getCompanyTree().get(subMgrId).getMyManager(),
                "Sub-manager's appointer should be reassigned to founder");
    }
}
