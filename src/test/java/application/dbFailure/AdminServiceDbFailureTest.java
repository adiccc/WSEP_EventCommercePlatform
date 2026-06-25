package application.dbFailure;

import DTO.AdminPurchaseHistoryDTO;
import app.config.SystemProperties;
import DTO.HierarchyDTO;
import DTO.SuspensionDTO;
import application.*;
import domain.Suspension.ISuspensionRepo;
import domain.Suspension.Suspension;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.company.Permissions;
import domain.event.Event;
import domain.event.IEventRepo;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminServiceDbFailureTest {

    private static final String TOKEN = "admin-token";
    private static final String ADMIN_IDENTIFIER = "admin@test.com";
    private static final String TARGET_IDENTIFIER = "target@test.com";
    private static final int ADMIN_ID = 1;
    private static final int TARGET_USER_ID = 2;
    private static final int COMPANY_ID = 10;
    private static final int EVENT_ID = 20;

    private IAuth auth;
    private IUserRepo userRepo;
    private ICompanyRepo companyRepo;
    private IEventRepo eventRepo;
    private ISuspensionRepo suspensionRepo;
    private TransactionStatus transactionStatus;
    private AdminService adminService;

    @BeforeAll
    static void disableLogs() {
        Logger.getLogger("").setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() {
        auth = mock(IAuth.class);
        userRepo = mock(IUserRepo.class);
        companyRepo = mock(ICompanyRepo.class);
        eventRepo = mock(IEventRepo.class);
        suspensionRepo = mock(ISuspensionRepo.class);

        IPaymentSystem paymentSystem = mock(IPaymentSystem.class);
        INotifier notifier = mock(INotifier.class);
        ITicketSupply ticketSupply = mock(ITicketSupply.class);

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        transactionStatus = mock(TransactionStatus.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });

        mockValidAdminToken();

        adminService = new AdminService(
                auth,
                userRepo,
                companyRepo,
                eventRepo,
                paymentSystem,
                suspensionRepo,
                notifier,
                transactionTemplate,
                ticketSupply
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
    // This file injects DB failures through AdminService use cases.
    // Domain entities are used only as mocked setup objects needed to drive the
    // service flow to the relevant repository call.
    //
    // Covered IUserRepo calls:
    // - findById: SuspendUser / UnsuspendUser / removeUser
    // - findUserByEmail: admin token resolution / notification flow
    // - store: SuspendUser / UnsuspendUser / removeUser
    //
    // Covered ISuspensionRepo calls:
    // - store: SuspendUser / UnsuspendUser
    // - findLastSuspensionByUserId: UnsuspendUser
    // - getAll: getAllUsersSuspensions
    //
    // Covered ICompanyRepo calls:
    // - getAll: removeUser
    // - store: removeUser / closeCompanyByAdmin
    // - findById: closeCompanyByAdmin / removeUser event creator reassignment
    //
    // Covered IEventRepo calls:
    // - findByCreator: removeUser
    // - store: removeUser / closeCompanyByAdmin
    // - findByCompany: closeCompanyByAdmin
    // - findById: closeCompanyByAdmin
    // - getAll: getGlobalOrders
    // - getAllPurchasers: getAllPurchasers
    //
    // Recovery coverage:
    // - suspensionRepo.store fails once and is retried during SuspendUser.
    // - eventRepo.getAllPurchasers fails once and is retried during getAllPurchasers.

    // ============================================================================
    // User repo failure tests
    // ============================================================================

    @Test
    void GivenUserRepoFindByIdFailure_WhenSuspendUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(userRepo.findById(TARGET_USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.SuspendUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).findById(TARGET_USER_ID);
    }

    @Test
    void GivenUserRepoStoreFailure_WhenSuspendUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member target = mockActiveTargetMember();

        when(userRepo.findById(TARGET_USER_ID)).thenReturn(target);
        doNothing().when(suspensionRepo).store(any(Suspension.class));

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(userRepo)
                .store(target);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.SuspendUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).store(target);
    }

    @Test
    void GivenUserRepoFindByIdFailure_WhenUnsuspendUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(userRepo.findById(TARGET_USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.UnsuspendUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).findById(TARGET_USER_ID);
    }

    @Test
    void GivenUserRepoStoreFailure_WhenUnsuspendUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member target = mockSuspendedTargetMember();
        Suspension suspension = mock(Suspension.class);

        when(userRepo.findById(TARGET_USER_ID)).thenReturn(target);
        when(suspensionRepo.findLastSuspensionByUserId(TARGET_USER_ID)).thenReturn(suspension);
        doNothing().when(suspensionRepo).store(suspension);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(userRepo)
                .store(target);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.UnsuspendUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).store(target);
    }

    @Test
    void GivenUserRepoFindByIdFailure_WhenRemoveUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(userRepo.findById(TARGET_USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.removeUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).findById(TARGET_USER_ID);
    }

    @Test
    void GivenUserRepoStoreFailure_WhenRemoveUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member target = mockActiveTargetMember();

        when(userRepo.findById(TARGET_USER_ID)).thenReturn(target);
        when(companyRepo.getAll()).thenReturn(Collections.emptyList());
        when(eventRepo.findByCreator(TARGET_USER_ID)).thenReturn(Collections.emptyList());

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(userRepo)
                .store(target);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.removeUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).store(target);
    }

    // ============================================================================
    // Suspension repo failure tests
    // ============================================================================

    @Test
    void GivenSuspensionRepoStoreFailure_WhenSuspendUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member target = mockActiveTargetMember();

        when(userRepo.findById(TARGET_USER_ID)).thenReturn(target);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(suspensionRepo)
                .store(any(Suspension.class));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.SuspendUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(suspensionRepo, atLeastOnce()).store(any(Suspension.class));
        verify(userRepo, never()).store(target);
    }

    @Test
    void GivenSuspensionRepoFindLastFailure_WhenUnsuspendUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member target = mockSuspendedTargetMember();

        when(userRepo.findById(TARGET_USER_ID)).thenReturn(target);
        when(suspensionRepo.findLastSuspensionByUserId(TARGET_USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.UnsuspendUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(suspensionRepo, atLeastOnce()).findLastSuspensionByUserId(TARGET_USER_ID);
    }

    @Test
    void GivenSuspensionRepoStoreFailure_WhenUnsuspendUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member target = mockSuspendedTargetMember();
        Suspension suspension = mock(Suspension.class);

        when(userRepo.findById(TARGET_USER_ID)).thenReturn(target);
        when(suspensionRepo.findLastSuspensionByUserId(TARGET_USER_ID)).thenReturn(suspension);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(suspensionRepo)
                .store(suspension);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.UnsuspendUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(suspensionRepo, atLeastOnce()).store(suspension);
        verify(userRepo, never()).store(target);
    }

    @Test
    void GivenSuspensionRepoGetAllFailure_WhenGetAllUsersSuspensions_ThenControlledErrorReturned() {
        // Arrange
        when(suspensionRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<List<SuspensionDTO>> response = assertDoesNotThrow(
                () -> adminService.getAllUsersSuspensions(TOKEN)
        );

        // Assert
        assertControlledFailure(response);
        verify(suspensionRepo, atLeastOnce()).getAll();
    }

    // ============================================================================
    // Company repo failure tests
    // ============================================================================

    @Test
    void GivenCompanyRepoGetAllFailure_WhenRemoveUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member target = mockActiveTargetMember();

        when(userRepo.findById(TARGET_USER_ID)).thenReturn(target);
        when(companyRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.removeUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeastOnce()).getAll();
    }

    @Test
    void GivenCompanyRepoStoreFailure_WhenRemoveUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member target = mockActiveTargetMember();
        Company company = mockCompanyWhereTargetIsOwner();

        when(userRepo.findById(TARGET_USER_ID)).thenReturn(target);
        when(companyRepo.getAll()).thenReturn(List.of(company));

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(companyRepo)
                .store(company);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.removeUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeastOnce()).store(company);
    }

    @Test
    void GivenCompanyRepoFindByIdFailure_WhenCloseCompanyByAdmin_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(companyRepo.findById(COMPANY_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.closeCompanyByAdmin(TOKEN, COMPANY_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeastOnce()).findById(COMPANY_ID);
    }

    @Test
    void GivenCompanyRepoStoreFailure_WhenCloseCompanyByAdmin_ThenRollbackControlledErrorReturned() {
        // Arrange
        Company company = mockClosableCompany();

        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(companyRepo)
                .store(company);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.closeCompanyByAdmin(TOKEN, COMPANY_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeastOnce()).store(company);
    }

    // ============================================================================
    // Event repo failure tests
    // ============================================================================

    @Test
    void GivenEventRepoFindByCreatorFailure_WhenRemoveUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member target = mockActiveTargetMember();

        when(userRepo.findById(TARGET_USER_ID)).thenReturn(target);
        when(companyRepo.getAll()).thenReturn(Collections.emptyList());
        when(eventRepo.findByCreator(TARGET_USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.removeUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findByCreator(TARGET_USER_ID);
    }

    @Test
    void GivenEventRepoStoreFailure_WhenRemoveUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member target = mockActiveTargetMember();
        Event event = mock(Event.class);
        Company eventCompany = mock(Company.class);

        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getCompanyId()).thenReturn(COMPANY_ID);

        when(eventCompany.getFounderId()).thenReturn(ADMIN_ID);

        when(userRepo.findById(TARGET_USER_ID)).thenReturn(target);
        when(companyRepo.getAll()).thenReturn(Collections.emptyList());
        when(eventRepo.findByCreator(TARGET_USER_ID)).thenReturn(List.of(event));
        when(companyRepo.findById(COMPANY_ID)).thenReturn(eventCompany);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(eventRepo)
                .store(event);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.removeUser(TOKEN, TARGET_USER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).store(event);
    }

    @Test
    void GivenEventRepoFindByCompanyFailure_WhenCloseCompanyByAdmin_ThenRollbackControlledErrorReturned() {
        // Arrange
        Company company = mockClosableCompany();

        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);
        doNothing().when(companyRepo).store(company);

        when(eventRepo.findByCompany(COMPANY_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.closeCompanyByAdmin(TOKEN, COMPANY_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findByCompany(COMPANY_ID);
    }

    @Test
    void GivenEventRepoStoreFailure_WhenCloseCompanyByAdmin_ThenRollbackControlledErrorReturned() {
        // Arrange
        Company company = mockClosableCompany();
        Event event = mockClosableEvent();

        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);
        doNothing().when(companyRepo).store(company);
        when(eventRepo.findByCompany(COMPANY_ID)).thenReturn(List.of(event));

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(eventRepo)
                .store(event);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.closeCompanyByAdmin(TOKEN, COMPANY_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).store(event);
    }

    @Test
    void GivenEventRepoFindByIdFailure_WhenCloseCompanyByAdmin_ThenRollbackControlledErrorReturned() {
        // Arrange
        Company company = mockClosableCompany();
        Event event = mockClosableEvent();

        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);
        doNothing().when(companyRepo).store(company);
        when(eventRepo.findByCompany(COMPANY_ID)).thenReturn(List.of(event));
        doNothing().when(eventRepo).store(event);

        when(eventRepo.findById(EVENT_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> adminService.closeCompanyByAdmin(TOKEN, COMPANY_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findById(EVENT_ID);
    }

    @Test
    void GivenEventRepoGetAllFailure_WhenGetGlobalOrders_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(eventRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<List<AdminPurchaseHistoryDTO>> response = assertDoesNotThrow(
                () -> adminService.getGlobalOrders(TOKEN, null, null, null)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).getAll();
    }

    @Test
    void GivenEventRepoGetAllPurchasersFailure_WhenGetAllPurchasers_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(eventRepo.getAllPurchasers())
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<List<String>> response = assertDoesNotThrow(
                () -> adminService.getAllPurchasers(TOKEN)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).getAllPurchasers();
    }

    // ============================================================================
    // Full service flow DB recovery tests
    // ============================================================================

    @Test
    void GivenSuspensionRepoStoreFailsOnce_WhenSuspendUser_ThenRetryIsPerformed() {
        // Arrange
        Member target = mockActiveTargetMember();

        when(userRepo.findById(TARGET_USER_ID)).thenReturn(target);

        doThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .doNothing()
                .when(suspensionRepo)
                .store(any(Suspension.class));

        // Act
        assertDoesNotThrow(() -> adminService.SuspendUser(TOKEN, TARGET_USER_ID));

        // Assert
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(suspensionRepo, atLeast(2)).store(any(Suspension.class));
    }

    @Test
    void GivenEventRepoGetAllPurchasersFailsOnce_WhenGetAllPurchasers_ThenRetrySucceeds() {
        // Arrange
        when(eventRepo.getAllPurchasers())
                .thenThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .thenReturn(Collections.emptyList());

        // Act
        Response<List<String>> response = assertDoesNotThrow(
                () -> adminService.getAllPurchasers(TOKEN)
        );

        // Assert
        assertNotNull(response);
        assertNotNull(response.getValue());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
        verify(eventRepo, atLeast(2)).getAllPurchasers();
    }

    private void mockValidAdminToken() {
        Member admin = mock(Member.class);

        when(admin.getUserId()).thenReturn(ADMIN_ID);
        when(admin.getIdentifier()).thenReturn(ADMIN_IDENTIFIER);

        when(auth.getRole(TOKEN)).thenReturn(new Response<>("ADMIN", "Role found"));
        when(auth.isUserEmailAdmin(anyString(),anyString())).thenReturn(new Response<>(false,"not admin"));
        when(userRepo.getUserEmail(anyInt())).thenReturn("email");
        when(auth.isAdmin(TOKEN)).thenReturn(new Response<>(true, "Admin verified"));
        when(auth.getUserEmail(TOKEN)).thenReturn(new Response<>(ADMIN_IDENTIFIER, "Email found"));
        when(userRepo.findUserByEmail(ADMIN_IDENTIFIER)).thenReturn(admin);
    }

    private Member mockActiveTargetMember() {
        Member member = mock(Member.class);

        when(member.getUserId()).thenReturn(TARGET_USER_ID);
        when(member.getIdentifier()).thenReturn(TARGET_IDENTIFIER);
        when(member.isSuspended()).thenReturn(false);
        when(member.isActive()).thenReturn(true);

        return member;
    }

    private Member mockSuspendedTargetMember() {
        Member member = mock(Member.class);

        when(member.getUserId()).thenReturn(TARGET_USER_ID);
        when(member.getIdentifier()).thenReturn(TARGET_IDENTIFIER);
        when(member.isSuspended()).thenReturn(true);
        when(member.isActive()).thenReturn(true);

        return member;
    }

    private Company mockCompanyWhereTargetIsOwner() {
        Company company = mock(Company.class);
        Permissions permissions = mock(Permissions.class);

        when(company.getCompanyId()).thenReturn(COMPANY_ID);
        when(company.getCompanyName()).thenReturn("Company");
        when(company.getCompanyPermission()).thenReturn(permissions);

        when(permissions.getFounderId()).thenReturn(ADMIN_ID);
        when(permissions.isOwner(TARGET_USER_ID)).thenReturn(true);
        when(permissions.isManager(TARGET_USER_ID)).thenReturn(false);
        when(permissions.getCompanyTree())
                .thenReturn(new HashMap<Integer, HierarchyDTO>());

        return company;
    }

    private Company mockClosableCompany() {
        Company company = mock(Company.class, RETURNS_DEEP_STUBS);
        Permissions permissions = mock(Permissions.class);

        when(company.getCompanyId()).thenReturn(COMPANY_ID);
        when(company.getCompanyName()).thenReturn("Company");
        when(company.isActive()).thenReturn(true);
        when(company.getCompanyPermission()).thenReturn(permissions);

        when(permissions.getOwnerIds()).thenReturn(Collections.emptySet());
        when(permissions.getCompanyTree())
                .thenReturn(new HashMap<Integer, HierarchyDTO>());

        return company;
    }

    private Event mockClosableEvent() {
        Event event = mock(Event.class);

        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getCompanyId()).thenReturn(COMPANY_ID);
        when(event.getOrders()).thenReturn(Collections.emptyList());

        return event;
    }

    private void assertControlledFailure(Response<?> response) {
        assertNotNull(response);
        assertTrue(response.getValue() == null || Boolean.FALSE.equals(response.getValue()));
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
    }
}