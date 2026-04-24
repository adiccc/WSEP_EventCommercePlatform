package application;

import domain.company.Company;
import domain.company.ContactInfo;
import domain.company.ICompanyRepo;
import domain.company.ManagerAppointment;
import domain.dataType.PermissionType;
import domain.dto.RolesPermissionsTreeDTO;
import domain.event.IOrderRepo;
import domain.event.Order;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import domain.user.IUserRepo;
import infrastructure.CompanyRepoImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Acceptance tests for II.4.15 – View roles and permissions tree.
 *
 * Use-case: a company owner requests the roles/permissions tree for their company.
 *
 * Actors       : company owner
 * Pre-conditions:
 *   - User is connected (valid token)
 *   - Company exists in the system
 *   - Requesting user is an owner of that company
 *
 * Acceptance tests (from the spec):
 *   Successful_View           – owner gets full tree (founder, owners, managers + permissions)
 *   Unauthorized_Access       – non-owner is rejected
 *   Company_Not_Found         – unknown companyId returns error
 *   Logged_Out_User_Access    – invalid/expired token returns error
 */
class ViewRolesAndPermissionsTreeTest {

    private static final int COMPANY_ID   = 1;
    private static final int FOUNDER_ID   = 100;   // also an owner
    private static final int OWNER_ID     = 123;
    private static final int NON_OWNER_ID = 456;
    private static final String MANAGER_ID = "mgr-1";

    private CompanyService service;
    private TokenService tokenService;
    private Company company;

    private String ownerToken;
    private String nonOwnerToken;

    @BeforeEach
    void setUp() {
        // Build the company with a founder, one extra owner, and one manager
        company = new Company(
                COMPANY_ID, "Test Company", FOUNDER_ID,
                new ContactInfo("test@test.com", "0500000000", "bank-1"),
                new PurchasePolicy(), new DiscountPolicy()
        );

        // Add an extra owner (OWNER_ID)
        company.getOwnerIds().add(OWNER_ID);

        // Add a manager with some permissions
        ManagerAppointment managerAppt = new ManagerAppointment(
                MANAGER_ID,
                EnumSet.of(PermissionType.MANAGE_EVENTS_INVENTORY, PermissionType.VIEW_PURCHASE_HISTORY)
        );
        company.getManagersPermissionsMap().put(MANAGER_ID, managerAppt);

        // Token service
        tokenService = new TokenService();
        ownerToken    = tokenService.generateToken("owner");
        nonOwnerToken = tokenService.generateToken("nonOwner");

        // Auth stub: maps tokens to user IDs
        IAuth auth = new IAuth() {
            @Override public Response<String> login(String u, String p) { return Response.ok(tokenService.generateToken(u)); }
            @Override public boolean isLoggedIn(String token) { return tokenService.validateToken(token); }
            @Override public void logout(String token) { return; }
            @Override public int getUserId(String token) {
                if (token.equals(ownerToken))    return OWNER_ID;
                if (token.equals(nonOwnerToken)) return NON_OWNER_ID;
                return -1;
            }
        };

        // Company repo with the pre-built company
        ICompanyRepo companyRepo = new CompanyRepoImpl();
        companyRepo.store(company);

        // Minimal order/user repos (not used by this use-case)
        IOrderRepo orderRepo = new IOrderRepo() {
            @Override public Order findById(Integer id) { return null; }
            @Override public List<Order> getAll() { return new ArrayList<>(); }
            @Override public void delete(Integer id) {}
            @Override public void store(Order o) {}
            @Override public int getTicketsBoughtByUserForEvent(int userId, int eventId) { return 0; }
        };

        IUserRepo userRepo = mock(IUserRepo.class);

        service = new CompanyService(tokenService, auth, companyRepo, userRepo, orderRepo);
    }

    // ── Successful_View ────────────────────────────────────────────────────────

    @Test
    void GivenOwnerAndValidCompany_WhenViewRolesTree_ThenReturnsFullTree() {
        Response<RolesPermissionsTreeDTO> response = service.viewRolesAndPermissionsTree(ownerToken, COMPANY_ID);

        assertFalse(response.isError(), "Expected success but got: " + response.getMessage());
        RolesPermissionsTreeDTO tree = response.getValue();
        assertNotNull(tree);

        // Founder is correct
        assertEquals(FOUNDER_ID, tree.getFounderId());

        // Owner set contains both founder and the extra owner
        assertTrue(tree.getOwnerIds().contains(FOUNDER_ID));
        assertTrue(tree.getOwnerIds().contains(OWNER_ID));

        // Manager entry with expected permissions
        assertTrue(tree.getManagersPermissions().containsKey(MANAGER_ID));
        assertTrue(tree.getManagersPermissions().get(MANAGER_ID).contains(PermissionType.MANAGE_EVENTS_INVENTORY));
        assertTrue(tree.getManagersPermissions().get(MANAGER_ID).contains(PermissionType.VIEW_PURCHASE_HISTORY));
    }

    @Test
    void GivenCompanyWithNoManagers_WhenViewRolesTree_ThenManagersMapIsEmpty() {
        // Create a fresh company with no managers
        Company freshCompany = new Company(
                2, "Fresh Co", FOUNDER_ID,
                new ContactInfo("a@b.com", "050", "bank"),
                new PurchasePolicy(), new DiscountPolicy()
        );
        ICompanyRepo repo2 = new CompanyRepoImpl();
        repo2.store(freshCompany);

        IAuth auth2 = new IAuth() {
            @Override public Response<String> login(String u, String p) { return Response.ok(""); }
            @Override public void logout(String t) {}   
            @Override public boolean isLoggedIn(String t) { return tokenService.validateToken(t); }
            @Override public int getUserId(String t) { return FOUNDER_ID; }  // ownerToken maps to founder here
        };

        CompanyService svc2 = new CompanyService(tokenService, auth2, repo2, mock(IUserRepo.class),
                new IOrderRepo() {
                    @Override public Order findById(Integer id) { return null; }
                    @Override public List<Order> getAll() { return new ArrayList<>(); }
                    @Override public void delete(Integer id) {}
                    @Override public void store(Order o) {}
                    @Override public int getTicketsBoughtByUserForEvent(int userId, int eventId) { return 0; }
                });

        Response<RolesPermissionsTreeDTO> response = svc2.viewRolesAndPermissionsTree(ownerToken, 2);

        assertFalse(response.isError());
        assertTrue(response.getValue().getManagersPermissions().isEmpty());
    }

    // ── Unauthorized_Access ────────────────────────────────────────────────────

    @Test
    void GivenNonOwner_WhenViewRolesTree_ThenError() {
        Response<RolesPermissionsTreeDTO> response = service.viewRolesAndPermissionsTree(nonOwnerToken, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // ── Company_Not_Found ──────────────────────────────────────────────────────

    @Test
    void GivenUnknownCompanyId_WhenViewRolesTree_ThenError() {
        int nonExistentCompanyId = 999;
        Response<RolesPermissionsTreeDTO> response = service.viewRolesAndPermissionsTree(ownerToken, nonExistentCompanyId);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // ── Logged_Out_User_Access ─────────────────────────────────────────────────

    @Test
    void GivenInvalidToken_WhenViewRolesTree_ThenError() {
        Response<RolesPermissionsTreeDTO> response = service.viewRolesAndPermissionsTree("invalid-or-expired-token", COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenExpiredToken_WhenViewRolesTree_ThenError() {
        // Invalidate (simulate logout) the owner token
        Response<RolesPermissionsTreeDTO> response = service.viewRolesAndPermissionsTree("-1", COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }
}
