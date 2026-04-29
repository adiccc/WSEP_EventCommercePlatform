package domain.unitTest.company;

import application.CompanyService;
import application.IAuth;
import application.Response;
import domain.company.Company;
import domain.company.ContactInfo;
import domain.company.ICompanyRepo;
import domain.company.Permissions;
import domain.dataType.PermissionType;
import domain.dto.HierarchyDTO;
import domain.dto.RolesPermissionsTreeDTO;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import domain.user.IUserRepo;
import domain.user.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyUnitTest {

    private ICompanyRepo companyRepoMock;
    private IUserRepo userRepoMock;
    private IAuth authMock;
    private CompanyService companyService;
    private Member mockUser;

    // Shared constants
    private static final int FOUNDER_USER_ID = 1;
    private static final int NON_OWNER_USER_ID = 99;
    private static final int COMPANY_ID = 555;
    private static final String VALID_TOKEN = "token123";
    private static final String OWNER_TOKEN = "owner-token";
    private static final String NON_OWNER_TOKEN = "non-owner-token";
    private static final String INVALID_TOKEN = "invalid-token";

    @BeforeEach
    public void setUp() {
        companyRepoMock = mock(ICompanyRepo.class);
        userRepoMock = mock(IUserRepo.class);
        authMock = mock(IAuth.class);

        companyService = new CompanyService(authMock, companyRepoMock, userRepoMock);

        mockUser = new Member("user123@test.com", "hashedpw", "Test", "User",
                "050-432-6677", LocalDate.of(2001, 5, 12), "Tel Aviv");
        mockUser.setConnected(true);

        // Default auth behaviour for createProductionCompany tests
        when(authMock.getUserId(anyString())).thenReturn(new Response<>(FOUNDER_USER_ID, ""));
        when(authMock.isLoggedIn(VALID_TOKEN)).thenReturn(new Response<>(true, ""));

        // Auth behaviour for viewRolesAndPermissionsTree tests
        // Note: viewRolesAndPermissionsTree uses isLoggedIn().isError(), so we need null message = ok
        when(authMock.isLoggedIn(OWNER_TOKEN)).thenReturn(Response.ok(true));
        when(authMock.getUserId(OWNER_TOKEN)).thenReturn(new Response<>(FOUNDER_USER_ID, null));

        when(authMock.isLoggedIn(NON_OWNER_TOKEN)).thenReturn(Response.ok(true));
        when(authMock.getUserId(NON_OWNER_TOKEN)).thenReturn(new Response<>(NON_OWNER_USER_ID, null));

        when(authMock.isLoggedIn(INVALID_TOKEN)).thenReturn(Response.error("Invalid or expired token"));
    }

    // ══════════════════════════════════════════════════════════════
    // II.3.2  Create Production Company
    // ══════════════════════════════════════════════════════════════

    // --- Successful_Creation ---
    @Test
    public void GivenValidInputs_WhenCreateProductionCompany_ThenReturnSuccessAndCreateCompany() {
        when(userRepoMock.findById(FOUNDER_USER_ID)).thenReturn(mockUser);
        when(companyRepoMock.findById(COMPANY_ID)).thenThrow(new NoSuchElementException());
        when(companyRepoMock.existsByName("LiveNation")).thenReturn(false);

        Response<Company> response = companyService.createProductionCompany(
                VALID_TOKEN, COMPANY_ID, "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        assertNotNull(response.getValue(), "The company object should not be null on success");
        assertEquals("LiveNation", response.getValue().getCompanyName());
        verify(companyRepoMock, times(1)).store(any(Company.class));
        verify(userRepoMock, times(1)).store(mockUser);
    }

    // --- Duplicate_Company_Number ---
    @Test
    public void GivenExistingCompanyId_WhenCreateProductionCompany_ThenReturnError() {
        when(userRepoMock.findById(FOUNDER_USER_ID)).thenReturn(mockUser);
        when(companyRepoMock.findById(COMPANY_ID)).thenReturn(mock(Company.class));

        Response<Company> response = companyService.createProductionCompany(
                VALID_TOKEN, COMPANY_ID, "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        assertNull(response.getValue(), "Company object should be null when ID already exists");
        assertTrue(response.getMessage().contains("already exists"));
        verify(companyRepoMock, never()).store(any(Company.class));
    }

    // --- Duplicate_Company_Name ---
    @Test
    public void GivenExistingCompanyName_WhenCreateProductionCompany_ThenReturnError() {
        when(userRepoMock.findById(FOUNDER_USER_ID)).thenReturn(mockUser);
        when(companyRepoMock.findById(COMPANY_ID)).thenThrow(new NoSuchElementException());
        when(companyRepoMock.existsByName("TakenName")).thenReturn(true);

        Response<Company> response = companyService.createProductionCompany(
                VALID_TOKEN, COMPANY_ID, "TakenName", "admin@test.com", "0501234567", "bank-123"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("already taken"));
        verify(companyRepoMock, never()).store(any(Company.class));
    }

    // --- Logged_Out_User_Access ---
    @Test
    public void GivenDisconnectedUser_WhenCreateProductionCompany_ThenReturnError() {
        // User exists in repo but is NOT logged in (isLoggedIn returns false)
        when(userRepoMock.findById(FOUNDER_USER_ID)).thenReturn(mockUser);
        when(authMock.isLoggedIn(VALID_TOKEN)).thenReturn(new Response<>(false, "not logged in"));

        Response<Company> response = companyService.createProductionCompany(
                VALID_TOKEN, COMPANY_ID, "LiveNation",
                "admin@livenation.com", "0501234567", "bank-123"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().toLowerCase().contains("logged in"),
                "Expected 'logged in' message but got: " + response.getMessage());
        verify(companyRepoMock, never()).store(any(Company.class));
        verify(userRepoMock, never()).store(any(Member.class));
    }

    // --- User_Not_Found ---
    @Test
    public void GivenUserNotFound_WhenCreateProductionCompany_ThenReturnError() {
        when(userRepoMock.findById(FOUNDER_USER_ID)).thenReturn(null);

        Response<Company> response = companyService.createProductionCompany(
                VALID_TOKEN, COMPANY_ID, "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("not found"));
        verify(companyRepoMock, never()).store(any(Company.class));
    }

    // --- Wrong_Mandatory_Fields ---
    @Test
    public void GivenInvalidEmail_WhenCreateProductionCompany_ThenReturnError() {
        when(userRepoMock.findById(FOUNDER_USER_ID)).thenReturn(mockUser);

        Response<Company> response = companyService.createProductionCompany(
                VALID_TOKEN, COMPANY_ID, "LiveNation", "invalidEmailFormat", "0501234567", "bank-123"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("Invalid contact"));
    }

    // ══════════════════════════════════════════════════════════════
    // II.4.15  View Roles and Permissions Tree
    // ══════════════════════════════════════════════════════════════

    /** Helper: build a company whose founder/owner is FOUNDER_USER_ID. */
    private Company buildCompanyWithFounder(int companyId) {
        Permissions permissions = new Permissions(FOUNDER_USER_ID);
        return new Company(companyId, "TestCo",
                new ContactInfo("co@test.com", "050-000-0000", "bank1"),
                new PurchasePolicy(), new DiscountPolicy(), permissions);
    }

    // --- Successful_View (founder only) ---
    @Test
    public void GivenOwner_WhenViewRolesAndPermissionsTree_ThenReturnSuccessWithCorrectTree() {
        Company company = buildCompanyWithFounder(COMPANY_ID);
        when(companyRepoMock.findById(COMPANY_ID)).thenReturn(company);

        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID);

        assertFalse(response.isError(), "Expected success but got: " + response.getMessage());
        RolesPermissionsTreeDTO tree = response.getValue();
        assertNotNull(tree);
        assertEquals(FOUNDER_USER_ID, tree.getFounderId());
        assertTrue(tree.getOwnerIds().contains(FOUNDER_USER_ID));
        assertTrue(tree.getManagersPermissions().isEmpty(),
                "No managers were added, so the map should be empty");
    }

    // --- Successful_View with managers ---
    @Test
    public void GivenCompanyWithManagers_WhenViewRolesAndPermissionsTree_ThenReturnManagerPermissions() {
        int managerId = 200;
        Permissions permissions = new Permissions(FOUNDER_USER_ID);
        permissions.getCompanyTree().put(managerId, new HierarchyDTO(
                FOUNDER_USER_ID,
                new ArrayList<>(),
                EnumSet.of(PermissionType.MANAGE_EVENTS_INVENTORY, PermissionType.VIEW_PURCHASE_HISTORY)
        ));
        Company company = new Company(COMPANY_ID, "TestCo",
                new ContactInfo("co@test.com", "050-000-0000", "bank1"),
                new PurchasePolicy(), new DiscountPolicy(), permissions);
        when(companyRepoMock.findById(COMPANY_ID)).thenReturn(company);

        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID);

        assertFalse(response.isError());
        assertTrue(response.getValue().getManagersPermissions().containsKey(managerId));
        assertTrue(response.getValue().getManagersPermissions().get(managerId)
                .contains(PermissionType.MANAGE_EVENTS_INVENTORY));
        assertTrue(response.getValue().getManagersPermissions().get(managerId)
                .contains(PermissionType.VIEW_PURCHASE_HISTORY));
    }

    // --- Unauthorized_Access ---
    @Test
    public void GivenNonOwner_WhenViewRolesAndPermissionsTree_ThenReturnError() {
        Company company = buildCompanyWithFounder(COMPANY_ID);
        when(companyRepoMock.findById(COMPANY_ID)).thenReturn(company);

        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(NON_OWNER_TOKEN, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // --- Company_Not_Found ---
    @Test
    public void GivenCompanyNotFound_WhenViewRolesAndPermissionsTree_ThenReturnError() {
        when(companyRepoMock.findById(999)).thenThrow(new NoSuchElementException("Company not found"));

        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(OWNER_TOKEN, 999);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // --- Logged_Out_User_Access ---
    @Test
    public void GivenInvalidToken_WhenViewRolesAndPermissionsTree_ThenReturnError() {
        Response<RolesPermissionsTreeDTO> response =
                companyService.viewRolesAndPermissionsTree(INVALID_TOKEN, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
        // company repo should never be touched for unauthenticated requests
        verify(companyRepoMock, never()).findById(anyInt());
    }
}
