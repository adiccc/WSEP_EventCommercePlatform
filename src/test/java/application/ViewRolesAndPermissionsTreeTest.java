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

    private static final String OWNER_TOKEN     = "owner-token";
    private static final String NON_OWNER_TOKEN = "non-owner-token";
    private static final String INVALID_TOKEN   = "invalid-or-expired-token";

    private CompanyService service;
    private Company company;

    private IAuth buildAuth() {
        return new IAuth() {
            @Override public Response<String> login(String u, String p) { return Response.ok(""); }
            @Override public Response<Boolean> logout(String token) {
                return Response.ok(false);
            }
            @Override public Response<Boolean> isLoggedIn(String token) {
                if(OWNER_TOKEN.equals(token) || NON_OWNER_TOKEN.equals(token)){
                    return new Response<>(true, null);
                }
                else return new Response<>(false,"") ;
            }
            @Override public Response<Integer> getUserId(String token) {
                if (OWNER_TOKEN.equals(token))     return new Response<>(OWNER_ID, "");
                if (NON_OWNER_TOKEN.equals(token)) return new Response<>(NON_OWNER_ID,"");
                return new Response<>(-1, "");
            }
        };
    }

    private IOrderRepo emptyOrderRepo() {
        return new IOrderRepo() {
            @Override public Order findById(Integer id) { return null; }
            @Override public List<Order> getAll() { return new ArrayList<>(); }
            @Override public void delete(Integer id) {}
            @Override public void store(Order o) {}
            @Override public int getTicketsBoughtByUserForEvent(int userId, int eventId) { return 0; }
        };
    }

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

        ICompanyRepo companyRepo = new CompanyRepoImpl();
        companyRepo.store(company);

        service = new CompanyService(buildAuth(), companyRepo, mock(IUserRepo.class));
    }

    // ── Successful_View ────────────────────────────────────────────────────────

    @Test
    void GivenOwnerAndValidCompany_WhenViewRolesTree_ThenReturnsFullTree() {
        Response<RolesPermissionsTreeDTO> response = service.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID);

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
        Company freshCompany = new Company(
                2, "Fresh Co", FOUNDER_ID,
                new ContactInfo("a@b.com", "050", "bank"),
                new PurchasePolicy(), new DiscountPolicy()
        );
        ICompanyRepo repo2 = new CompanyRepoImpl();
        repo2.store(freshCompany);

        IAuth auth2 = new IAuth() {
            @Override public Response<String> login(String u, String p) { return Response.ok(""); }
            @Override public Response<Boolean> logout(String t) {
                return Response.ok(false);
            }
            @Override public Response<Boolean> isLoggedIn(String t) {
                if(OWNER_TOKEN.equals(t)){
                    return new Response<>(true,null);
                }
                else return new Response<>(false,"");
            }
            @Override public Response<Integer> getUserId(String t) {
                return new Response<>(FOUNDER_ID, "");
            }
        };

        CompanyService svc2 = new CompanyService(auth2, repo2, mock(IUserRepo.class));

        Response<RolesPermissionsTreeDTO> response = svc2.viewRolesAndPermissionsTree(OWNER_TOKEN, 2);

        assertFalse(response.isError());
        assertTrue(response.getValue().getManagersPermissions().isEmpty());
    }

    // ── Unauthorized_Access ────────────────────────────────────────────────────

    @Test
    void GivenNonOwner_WhenViewRolesTree_ThenError() {
        Response<RolesPermissionsTreeDTO> response = service.viewRolesAndPermissionsTree(NON_OWNER_TOKEN, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // ── Company_Not_Found ──────────────────────────────────────────────────────

    @Test
    void GivenUnknownCompanyId_WhenViewRolesTree_ThenError() {
        Response<RolesPermissionsTreeDTO> response = service.viewRolesAndPermissionsTree(OWNER_TOKEN, 999);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // ── Logged_Out_User_Access ─────────────────────────────────────────────────

    @Test
    void GivenInvalidToken_WhenViewRolesTree_ThenError() {
        Response<RolesPermissionsTreeDTO> response = service.viewRolesAndPermissionsTree(INVALID_TOKEN, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenExpiredToken_WhenViewRolesTree_ThenError() {
        Response<RolesPermissionsTreeDTO> response = service.viewRolesAndPermissionsTree("-1", COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }
}
