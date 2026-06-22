package application.dbFailure;

import DTO.DiscountDTO;
import app.config.SystemProperties;
import application.*;
import domain.Suspension.ISuspensionRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.company.Permissions;
import domain.dataType.PermissionType;
import DTO.CompanyDTO;
import DTO.CompanyDetailsDTO;
import DTO.HierarchyDTO;
import DTO.RolesPermissionsTreeDTO;
import domain.user.IUserRepo;
import domain.user.Member;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyServiceDbFailureTest {

    private static final String TOKEN = "token";
    private static final String USER_IDENTIFIER = "user@test.com";
    private static final String MANAGER_IDENTIFIER = "manager@test.com";
    private static final int USER_ID = 1;
    private static final int MANAGER_ID = 2;
    private static final int COMPANY_ID = 10;
    private static final String COMPANY_NAME = "DB Failure Company";

    private IAuth auth;
    private ICompanyRepo companyRepo;
    private IUserRepo userRepo;
    private ISuspensionRepo suspensionRepo;
    private TransactionStatus transactionStatus;
    private CompanyService companyService;

    @BeforeAll
    static void disableLogs() {
        Logger.getLogger("").setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() {
        auth = mock(IAuth.class);
        companyRepo = mock(ICompanyRepo.class);
        userRepo = mock(IUserRepo.class);
        suspensionRepo = mock(ISuspensionRepo.class);
        INotifier notifier = mock(INotifier.class);

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        transactionStatus = mock(TransactionStatus.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });

        mockValidToken(USER_ID, USER_IDENTIFIER);
        when(suspensionRepo.haveActiveSuspension(USER_ID)).thenReturn(false);

        companyService = new CompanyService(
                auth,
                companyRepo,
                userRepo,
                suspensionRepo,
                notifier,
                transactionTemplate
        );
    }
    @BeforeEach
    void resetRetryHelperConfig() {
        SystemProperties systemProperties = new SystemProperties();
        systemProperties.setRetryCount(50);
        new RetryHelper(systemProperties);
    }

    // ============================================================================
    // Coverage note
    // ============================================================================
    //
    // This file injects DB failures through CompanyService use cases.
    // Domain entities are used only as mocked setup objects needed to drive the
    // service flow to the relevant repository call.
    //
    // Covered ICompanyRepo calls:
    // - findById: createProductionCompany / getProductionCompany /
    //             viewRolesAndPermissionsTree / addDiscountToCompany /
    //             updateManagerPermissions / deactivateCompany
    // - existsByName: createProductionCompany
    // - store: createProductionCompany / addDiscountToCompany /
    //          updateManagerPermissions / deactivateCompany
    // - getAll: getAvailableCompanies
    // - findByUserRole: getMyCompanies
    //
    // Covered IUserRepo calls:
    // - findUserByEmail: token to user resolution / notification persistence
    // - findById: createProductionCompany / getProductionCompany /
    //             updateManagerPermissions
    // - store: createProductionCompany / getProductionCompany /
    //          updateManagerPermissions / notification persistence
    // - getUserEmail: deactivateCompany staff notification flow
    //
    // Covered ISuspensionRepo calls:
    // - haveActiveSuspension: createProductionCompany / addDiscountToCompany /
    //                         updateManagerPermissions / deactivateCompany
    //
    // Recovery coverage:
    // - companyRepo.store fails once and is retried during addDiscountToCompany.
    // - userRepo.findUserByEmail fails once and is retried during createProductionCompany.
    // - companyRepo.getAll fails once and is retried during getAvailableCompanies.

    // ============================================================================
    // Company repo failure tests
    // ============================================================================

    @Test
    void GivenCompanyRepoFindByIdFailure_WhenCreateProductionCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member member = mockMember(USER_ID, USER_IDENTIFIER);

        when(userRepo.findById(USER_ID)).thenReturn(member);
        when(companyRepo.findById(COMPANY_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Company> response = assertDoesNotThrow(this::createValidCompany);

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeastOnce()).findById(COMPANY_ID);
        verify(companyRepo, never()).store(any(Company.class));
    }

    @Test
    void GivenCompanyRepoExistsByNameFailure_WhenCreateProductionCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member member = mockMember(USER_ID, USER_IDENTIFIER);

        when(userRepo.findById(USER_ID)).thenReturn(member);
        when(companyRepo.findById(COMPANY_ID))
                .thenThrow(new NoSuchElementException("Company does not exist"));
        when(companyRepo.existsByName(COMPANY_NAME))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Company> response = assertDoesNotThrow(this::createValidCompany);

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeastOnce()).existsByName(COMPANY_NAME);
        verify(companyRepo, never()).store(any(Company.class));
    }

    @Test
    void GivenCompanyRepoStoreFailure_WhenCreateProductionCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member member = mockMember(USER_ID, USER_IDENTIFIER);

        when(userRepo.findById(USER_ID)).thenReturn(member);
        when(companyRepo.findById(COMPANY_ID))
                .thenThrow(new NoSuchElementException("Company does not exist"));
        when(companyRepo.existsByName(COMPANY_NAME)).thenReturn(false);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(companyRepo)
                .store(any(Company.class));

        // Act
        Response<Company> response = assertDoesNotThrow(this::createValidCompany);

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeastOnce()).store(any(Company.class));
        verify(userRepo, never()).store(any(Member.class));
    }

    @Test
    void GivenCompanyRepoFindByIdFailure_WhenGetProductionCompany_ThenControlledErrorReturned() {
        // Arrange
        when(companyRepo.findById(COMPANY_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<CompanyDetailsDTO> response = assertDoesNotThrow(
                () -> companyService.getProductionCompany(TOKEN, COMPANY_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(companyRepo, atLeastOnce()).findById(COMPANY_ID);
    }

    @Test
    void GivenCompanyRepoFindByIdFailure_WhenViewRolesAndPermissionsTree_ThenControlledErrorReturned() {
        // Arrange
        when(companyRepo.findById(COMPANY_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<RolesPermissionsTreeDTO> response = assertDoesNotThrow(
                () -> companyService.viewRolesAndPermissionsTree(TOKEN, COMPANY_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(companyRepo, atLeastOnce()).findById(COMPANY_ID);
    }

    @Test
    void GivenCompanyRepoFindByIdFailure_WhenAddDiscountToCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        DiscountDTO discountDTO = mock(DiscountDTO.class);

        when(companyRepo.findById(COMPANY_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> companyService.addDiscountToCompany(TOKEN, COMPANY_ID, discountDTO)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeastOnce()).findById(COMPANY_ID);
    }

    @Test
    void GivenCompanyRepoStoreFailure_WhenAddDiscountToCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        Company company = mockActiveCompany();
        DiscountDTO discountDTO = mock(DiscountDTO.class);

        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(companyRepo)
                .store(company);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> companyService.addDiscountToCompany(TOKEN, COMPANY_ID, discountDTO)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeastOnce()).store(company);
    }

    @Test
    void GivenCompanyRepoGetAllFailure_WhenGetAvailableCompanies_ThenRetryControlledErrorReturned() {
        // Arrange
        when(companyRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<List<CompanyDTO>> response = assertDoesNotThrow(
                () -> companyService.getAvailableCompanies(TOKEN)
        );

        // Assert
        assertControlledFailure(response);
        verify(companyRepo, atLeast(2)).getAll();
    }

    @Test
    void GivenCompanyRepoFindByUserRoleFailure_WhenGetMyCompanies_ThenRetryControlledErrorReturned() {
        // Arrange
        when(companyRepo.findByUserRole(USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<List<CompanyDTO>> response = assertDoesNotThrow(
                () -> companyService.getMyCompanies(TOKEN)
        );

        // Assert
        assertControlledFailure(response);
        verify(companyRepo, atLeast(2)).findByUserRole(USER_ID);
    }

    // ============================================================================
    // User repo failure tests
    // ============================================================================

    @Test
    void GivenUserRepoFindUserByEmailFailure_WhenCreateProductionCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(userRepo.findUserByEmail(USER_IDENTIFIER))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Company> response = assertDoesNotThrow(this::createValidCompany);

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).findUserByEmail(USER_IDENTIFIER);
        verify(companyRepo, never()).store(any(Company.class));
    }

    @Test
    void GivenUserRepoFindByIdFailure_WhenCreateProductionCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(userRepo.findById(USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Company> response = assertDoesNotThrow(this::createValidCompany);

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).findById(USER_ID);
        verify(companyRepo, never()).store(any(Company.class));
    }

    @Test
    void GivenUserRepoStoreFailure_WhenCreateProductionCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member member = mockMember(USER_ID, USER_IDENTIFIER);

        when(userRepo.findById(USER_ID)).thenReturn(member);
        when(companyRepo.findById(COMPANY_ID))
                .thenThrow(new NoSuchElementException("Company does not exist"));
        when(companyRepo.existsByName(COMPANY_NAME)).thenReturn(false);
        doNothing().when(companyRepo).store(any(Company.class));

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(userRepo)
                .store(member);

        // Act
        Response<Company> response = assertDoesNotThrow(this::createValidCompany);

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).store(member);
    }

    @Test
    void GivenUserRepoFindByIdFailure_WhenGetProductionCompany_ThenControlledErrorReturned() {
        // Arrange
        Company company = mockActiveCompany();

        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);
        when(userRepo.findById(USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<CompanyDetailsDTO> response = assertDoesNotThrow(
                () -> companyService.getProductionCompany(TOKEN, COMPANY_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(userRepo, atLeastOnce()).findById(USER_ID);
    }

    @Test
    void GivenUserRepoStoreFailure_WhenGetProductionCompany_ThenControlledErrorReturned() {
        // Arrange
        Company company = mockActiveCompany();
        Member member = mockMember(USER_ID, USER_IDENTIFIER);

        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);
        when(userRepo.findById(USER_ID)).thenReturn(member);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(userRepo)
                .store(member);

        // Act
        Response<CompanyDetailsDTO> response = assertDoesNotThrow(
                () -> companyService.getProductionCompany(TOKEN, COMPANY_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(userRepo, atLeastOnce()).store(member);
    }

    @Test
    void GivenUserRepoFindByIdFailure_WhenUpdateManagerPermissions_ThenRollbackControlledErrorReturned() {
        // Arrange
        Company company = mockOwnerCompany();

        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);
        when(userRepo.findById(MANAGER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> companyService.updateManagerPermissions(
                        TOKEN,
                        COMPANY_ID,
                        MANAGER_ID,
                        Set.of(PermissionType.CREATE_EVENT)
                )
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).findById(MANAGER_ID);
    }

    @Test
    void GivenUserRepoGetUserEmailFailure_WhenDeactivateCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        Company company = mockOwnerCompanyWithStaff();

        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);
        doNothing().when(companyRepo).store(company);

        when(userRepo.getUserEmail(MANAGER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> companyService.deactivateCompany(TOKEN, COMPANY_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).getUserEmail(MANAGER_ID);
    }

    // ============================================================================
    // Suspension repo failure tests
    // ============================================================================

    @Test
    void GivenSuspensionRepoFailure_WhenCreateProductionCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member member = mockMember(USER_ID, USER_IDENTIFIER);

        when(userRepo.findById(USER_ID)).thenReturn(member);
        when(suspensionRepo.haveActiveSuspension(USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Company> response = assertDoesNotThrow(this::createValidCompany);

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(suspensionRepo, atLeastOnce()).haveActiveSuspension(USER_ID);
        verify(companyRepo, never()).store(any(Company.class));
    }

    @Test
    void GivenSuspensionRepoFailure_WhenAddDiscountToCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        DiscountDTO discountDTO = mock(DiscountDTO.class);

        when(suspensionRepo.haveActiveSuspension(USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> companyService.addDiscountToCompany(TOKEN, COMPANY_ID, discountDTO)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(suspensionRepo, atLeastOnce()).haveActiveSuspension(USER_ID);
        verify(companyRepo, never()).findById(COMPANY_ID);
    }

    // ============================================================================
    // Full service flow DB recovery tests
    // ============================================================================

    @Test
    void GivenCompanyRepoStoreFailsOnce_WhenAddDiscountToCompany_ThenRetryIsPerformed() {
        // Arrange
        Company company = mockActiveCompany();
        DiscountDTO discountDTO = mock(DiscountDTO.class);

        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);

        doThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .doNothing()
                .when(companyRepo)
                .store(company);

        // Act
        assertDoesNotThrow(
                () -> companyService.addDiscountToCompany(TOKEN, COMPANY_ID, discountDTO)
        );

        // Assert
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeast(2)).store(company);
    }

    @Test
    void GivenUserRepoFindUserByEmailFailsOnce_WhenCreateProductionCompany_ThenRetryIsPerformed() {
        // Arrange
        Member member = mockMember(USER_ID, USER_IDENTIFIER);

        when(userRepo.findUserByEmail(USER_IDENTIFIER))
                .thenThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .thenReturn(member);

        when(userRepo.findById(USER_ID)).thenReturn(member);
        when(companyRepo.findById(COMPANY_ID))
                .thenThrow(new NoSuchElementException("Company does not exist"));
        when(companyRepo.existsByName(COMPANY_NAME)).thenReturn(false);

        // Act
        assertDoesNotThrow(this::createValidCompany);

        // Assert
        verify(userRepo, atLeast(2)).findUserByEmail(USER_IDENTIFIER);
    }

    @Test
    void GivenCompanyRepoGetAllFailsOnce_WhenGetAvailableCompanies_ThenRetrySucceeds() {
        // Arrange
        Company company = mockActiveCompany();

        when(companyRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .thenReturn(List.of(company));

        // Act
        Response<List<CompanyDTO>> response = assertDoesNotThrow(
                () -> companyService.getAvailableCompanies(TOKEN)
        );

        // Assert
        assertNotNull(response);
        assertNotNull(response.getValue());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
        verify(companyRepo, atLeast(2)).getAll();
    }

    private Response<Company> createValidCompany() {
        return companyService.createProductionCompany(
                TOKEN,
                COMPANY_ID,
                COMPANY_NAME,
                "company@test.com",
                "050-123-4567",
                "123456789"
        );
    }

    private void mockValidToken(int userId, String identifier) {
        Member member = mockMember(userId, identifier);

        when(auth.getRole(TOKEN)).thenReturn(new Response<>("MEMBER", "Role found"));
        when(auth.getUserEmail(TOKEN)).thenReturn(new Response<>(identifier, "Email found"));
        when(userRepo.findUserByEmail(identifier)).thenReturn(member);
    }

    private Member mockMember(int userId, String identifier) {
        Member member = mock(Member.class);

        when(member.getUserId()).thenReturn(userId);
        when(member.getIdentifier()).thenReturn(identifier);

        return member;
    }

    private Company mockActiveCompany() {
        Company company = mock(Company.class);
        Permissions permissions = mockPermissions();

        when(company.getCompanyId()).thenReturn(COMPANY_ID);
        when(company.getCompanyName()).thenReturn(COMPANY_NAME);
        when(company.isActive()).thenReturn(true);
        when(company.getCompanyPermission()).thenReturn(permissions);
        when(company.getManagerPermissions(USER_ID)).thenReturn(Set.of(PermissionType.CREATE_EVENT));
        when(company.getUserRoleName(USER_ID)).thenReturn("OWNER");

        return company;
    }

    private Company mockOwnerCompany() {
        Company company = mockActiveCompany();

        when(company.isOwner(USER_ID)).thenReturn(true);

        return company;
    }

    private Company mockOwnerCompanyWithStaff() {
        Company company = mockOwnerCompany();
        Permissions permissions = mockPermissions();

        when(company.getOwnerIds()).thenReturn(Set.of(MANAGER_ID));
        when(company.getCompanyPermission()).thenReturn(permissions);
        when(permissions.getManagers()).thenReturn(Collections.emptySet());

        return company;
    }

    private Permissions mockPermissions() {
        Permissions permissions = mock(Permissions.class);

        when(permissions.isOwner(USER_ID)).thenReturn(true);
        when(permissions.checkPermission(eq(USER_ID), any(PermissionType.class))).thenReturn(true);
        when(permissions.getCompanyTree()).thenReturn(new HashMap<Integer, HierarchyDTO>());
        when(permissions.getOwnerIds()).thenReturn(Collections.emptySet());
        when(permissions.getManagers()).thenReturn(Collections.emptySet());

        return permissions;
    }

    private void assertControlledFailure(Response<?> response) {
        assertNotNull(response);
        assertTrue(response.getValue() == null || Boolean.FALSE.equals(response.getValue()));
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
    }
}