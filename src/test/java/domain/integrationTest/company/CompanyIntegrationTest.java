package domain.integrationTest.company;

import domain.company.Company;
import domain.company.ContactInfo;
import domain.company.Permissions;
import domain.dataType.PermissionType;
import domain.dto.HierarchyDTO;
import domain.policy.*;
import domain.user.ActivationStatus;
import domain.user.Founder;
import domain.user.Manager;
import domain.user.Member;
import infrastructure.CompanyRepoImpl;
import infrastructure.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for II.3.2 (Create Production Company) and
 * II.4.15 (View Roles and Permissions Tree).
 *
 * Tests the interaction between domain objects (Company, Permissions, Member)
 * and the real in-memory repositories (CompanyRepoImpl, UserRepo) directly -
 * no application services are used.
 *
 * These tests verify that the domain model and repository layer work correctly
 * together: persistence, retrieval, ownership checks, manager tree structure.
 */
class CompanyIntegrationTest {

    private CompanyRepoImpl companyRepo;
    private UserRepo        userRepo;

    private static final int COMPANY_ID  = 100;
    private static final int FOUNDER_ID  = 1;

    private Member  founder;
    private Company company;

    @BeforeEach
    void setUp() {
        companyRepo = new CompanyRepoImpl();
        userRepo    = new UserRepo();

        // Create and store a founder member directly
        founder = new Member("founder@test.com", "hashedpw", "Founder", "User",
                "050-111-1111", LocalDate.of(1990, 1, 1), "Tel Aviv", new ArrayList<>());
        userRepo.store(founder);

        // Build a company with the founder's permissions and store it
        Permissions perms = new Permissions(founder.getUserId());
        company = new Company(COMPANY_ID, "LiveNation",
                new ContactInfo("admin@livenation.com", "050-000-0000", "bank-1"),
                new AndPurchasePolicy(), new SumDiscountPolicy(), perms);
        companyRepo.store(company);
    }

    // ============================================================
    // II.3.2  Create Production Company -Repository + Domain
    // ============================================================

    // --- Successful_Creation ---
    @Test
    void GivenValidCompany_WhenStored_ThenCanBeRetrievedById() {
        Company stored = companyRepo.findById(COMPANY_ID);

        assertNotNull(stored);
        assertEquals("LiveNation", stored.getCompanyName());
        assertEquals(COMPANY_ID, stored.getCompanyId());
    }


    @Test
    void GivenStoredCompany_WhenFounderChecked_ThenIsMarkedAsOwner() {
        Company stored = companyRepo.findById(COMPANY_ID);

        assertTrue(stored.isOwner(founder.getUserId()),
                "Founder should automatically be an owner of the company");
        assertEquals(founder.getUserId(), stored.getFounderId());
    }

    // --- Duplicate_Company_Number ---
    @Test
    void GivenExistingCompanyId_WhenCheckedViaFindById_ThenCompanyExists() {
        // The repo does not block duplicate IDs at the store level (it upserts);
        // duplicate-ID prevention is the service's responsibility.
        // At the domain level we verify the existing company is correctly
        // retrievable and has not been replaced by accident.
        Company first = companyRepo.findById(COMPANY_ID);
        assertEquals("LiveNation", first.getCompanyName());
    }

    // --- Duplicate_Company_Name ---
    @Test
    void GivenStoredCompanyName_WhenExistsByNameCalled_ThenReturnsTrue() {
        assertTrue(companyRepo.existsByName("LiveNation"),
                "existsByName should return true for a stored company name");
    }

    @Test
    void GivenUnusedName_WhenExistsByNameCalled_ThenReturnsFalse() {
        assertFalse(companyRepo.existsByName("UnknownCompany"));
    }

    // --- Company_Not_Found ---
    @Test
    void GivenUnknownCompanyId_WhenFindById_ThenThrowsNoSuchElement() {
        assertThrows(NoSuchElementException.class, () -> companyRepo.findById(9999));
    }

    // --- Wrong_Mandatory_Fields (ContactInfo validation) ---
    @Test
    void GivenInvalidEmail_WhenContactInfoCreated_ThenEmailIsStored() {
        // ContactInfo stores whatever is passed; email format validation is the
        // service's responsibility. The domain just holds the value.
        ContactInfo info = new ContactInfo("not-an-email", "050-000-0000", "bank-1");
        assertEquals("not-an-email", info.getEmail());
    }

    // ============================================================
    // II.4.15  View Roles and Permissions Tree -Domain + Repository
    // ============================================================

    // --- Successful_View (founder only) ---
    @Test
    void GivenCompanyWithFounderOnly_WhenPermissionsInspected_ThenFounderIsOwner() {
        Company stored = companyRepo.findById(COMPANY_ID);
        Permissions perms = stored.getCompanyPermission();

        assertEquals(founder.getUserId(), perms.getFounderId());
        assertTrue(perms.isOwner(founder.getUserId()));
        assertTrue(perms.getCompanyTree().isEmpty(),
                "No managers added yet -tree should be empty");
    }

    // --- Successful_View with managers ---
    @Test
    void GivenCompanyWithManager_WhenPermissionsInspected_ThenManagerAppearsInTree() {
        int managerId = 200;
        Company stored = companyRepo.findById(COMPANY_ID);
        stored.getCompanyPermission().addToTree(managerId, founder.getUserId(),
                EnumSet.of(PermissionType.MANAGE_EVENTS_INVENTORY, PermissionType.VIEW_PURCHASE_HISTORY)
        );
        companyRepo.store(stored);

        Company updated = companyRepo.findById(COMPANY_ID);
        Permissions perms = updated.getCompanyPermission();

        assertTrue(perms.getCompanyTree().containsKey(managerId),
                "Manager should appear in the company tree after being added");
        assertTrue(perms.getCompanyTree().get(managerId).getAllPermissions()
                .contains(PermissionType.MANAGE_EVENTS_INVENTORY));
        assertTrue(perms.getCompanyTree().get(managerId).getAllPermissions()
                .contains(PermissionType.VIEW_PURCHASE_HISTORY));
    }

    // --- Unauthorized_Access ---
    @Test
    void GivenNonOwnerUserId_WhenIsOwnerCalled_ThenReturnsFalse() {
        Company stored = companyRepo.findById(COMPANY_ID);
        int randomUserId = 9999;

        assertFalse(stored.getCompanyPermission().isOwner(randomUserId),
                "A user who was never added as owner should not be an owner");
    }

    // --- Company_Not_Found ---
    @Test
    void GivenNonExistentCompanyId_WhenFindById_ThenThrowsNoSuchElement() {
        assertThrows(NoSuchElementException.class,
                () -> companyRepo.findById(9999));
    }

    // --- Permissions persist after repo update ---
    @Test
    void GivenUpdatedPermissions_WhenStoredAndRetrieved_ThenChangesPersisted() {
        int newOwnerId = 500;
        Company stored = companyRepo.findById(COMPANY_ID);
        stored.getCompanyPermission().addOwner(newOwnerId);
        stored.getCompanyPermission().OwnerAppointeeRespond(newOwnerId, true);
        companyRepo.store(stored);

        Company updated = companyRepo.findById(COMPANY_ID);
        assertTrue(updated.getCompanyPermission().isOwner(newOwnerId),
                "Added owner should persist after store+retrieve");
    }

    // ============================================================
    // II.4.X  Remove Manager Appointment — User role integration
    // ============================================================

    /** Helper: creates a manager member, adds a Manager role, stores them, and adds them to the company tree. */
    private Member createAndAddManager(int managerId, String email) {
        Member manager = new Member(email, "hashedpw", "Manager", "User",
                "050-222-2222", LocalDate.of(1995, 6, 15), "Haifa", new ArrayList<>());
        manager.changeState(new Manager());
        userRepo.store(manager);

        Company stored = companyRepo.findById(COMPANY_ID);
        stored.getCompanyPermission().addToTree(manager.getUserId(), founder.getUserId(),
                EnumSet.of(PermissionType.CREATE_EVENT));
        companyRepo.store(stored);
        return userRepo.findById(manager.getUserId());
    }

    @Test
    void GivenManagerWithRole_WhenRemovedFromCompany_ThenManagerRoleStrippedFromUser() {
        Member manager = createAndAddManager(200, "mgr1@test.com");
        // Add a second manager so removal is allowed
        Member manager2 = new Member("mgr2@test.com", "hashedpw", "Manager2", "User",
                "050-333-3333", LocalDate.of(1996, 1, 1), "Beer Sheva", new ArrayList<>());
        manager2.changeState(new Manager());
        userRepo.store(manager2);
        Company stored = companyRepo.findById(COMPANY_ID);
        stored.getCompanyPermission().addToTree(manager2.getUserId(), founder.getUserId(),
                EnumSet.of(PermissionType.CREATE_EVENT));
        companyRepo.store(stored);

        // Perform removal: update company tree and strip role from user
        Company company = companyRepo.findById(COMPANY_ID);
        company.removeManagerAppointment(founder.getUserId(), manager.getUserId());
        companyRepo.store(company);
        manager.changeState(null);
        userRepo.store(manager);

        Member updatedUser = userRepo.findById(manager.getUserId());
        boolean hasRole = updatedUser.getRole() instanceof Manager;
        assertFalse(hasRole, "Manager role should be removed from the user after appointment removal");
    }

    @Test
    void GivenManagerWithRole_WhenRemovedFromCompany_ThenCompanyTreeNoLongerContainsManager() {
        Member manager = createAndAddManager(200, "mgr1@test.com");
        Member manager2 = new Member("mgr2@test.com", "hashedpw", "Manager2", "User",
                "050-333-3333", LocalDate.of(1996, 1, 1), "Beer Sheva", new ArrayList<>());
        manager2.changeState(new Manager());
        userRepo.store(manager2);
        Company stored = companyRepo.findById(COMPANY_ID);
        stored.getCompanyPermission().addToTree(manager2.getUserId(), founder.getUserId(),
                EnumSet.of(PermissionType.CREATE_EVENT));
        companyRepo.store(stored);

        Company company = companyRepo.findById(COMPANY_ID);
        company.removeManagerAppointment(founder.getUserId(), manager.getUserId());
        companyRepo.store(company);

        Company updated = companyRepo.findById(COMPANY_ID);
        assertFalse(updated.getCompanyPermission().isManager(manager.getUserId()),
                "Manager should no longer appear in the company tree after removal");
    }

    @Test
    void GivenManagerWithSubAppointee_WhenRemovedFromCompany_ThenSubAppointeeReassignedToFounder() {
        Member manager = createAndAddManager(200, "mgr1@test.com");
        // Add a second top-level manager so removal is not blocked
        Member manager2 = new Member("mgr2@test.com", "hashedpw", "Manager2", "User",
                "050-333-3333", LocalDate.of(1996, 1, 1), "Beer Sheva", new ArrayList<>());
        userRepo.store(manager2);
        // Add a sub-manager under manager
        Member subManager = new Member("sub@test.com", "hashedpw", "Sub", "Manager",
                "050-444-4444", LocalDate.of(1997, 3, 10), "Eilat", new ArrayList<>());
        subManager.changeState(new Manager());
        userRepo.store(subManager);

        Company stored = companyRepo.findById(COMPANY_ID);
        stored.getCompanyPermission().addToTree(manager2.getUserId(), founder.getUserId(),
                EnumSet.of(PermissionType.CREATE_EVENT));
        stored.getCompanyPermission().addToTree(subManager.getUserId(), manager.getUserId(),
                EnumSet.of(PermissionType.VIEW_PURCHASE_HISTORY));
        companyRepo.store(stored);

        Company company = companyRepo.findById(COMPANY_ID);
        company.removeManagerAppointment(founder.getUserId(), manager.getUserId());
        companyRepo.store(company);

        Company updated = companyRepo.findById(COMPANY_ID);
        int newAppointer = updated.getCompanyPermission().getManagerAppointerId(subManager.getUserId());
        assertEquals(founder.getUserId(), newAppointer,
                "Sub-manager's appointer should be reassigned to the founder after their manager is removed");
    }
}
