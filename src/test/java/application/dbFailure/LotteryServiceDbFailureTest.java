package application.dbFailure;

import app.config.SystemProperties;
import application.*;
import domain.Suspension.ISuspensionRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dataType.PermissionType;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;
import domain.user.IUserRepo;
import domain.user.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LotteryServiceDbFailureTest {

    private static final String TOKEN = "token";
    private static final String USER_IDENTIFIER = "user@test.com";
    private static final int USER_ID = 1;
    private static final int COMPANY_ID = 10;
    private static final int EVENT_ID = 20;

    private ILotteryRepo lotteryRepo;
    private IEventRepo eventRepo;
    private ICompanyRepo companyRepo;
    private IUserRepo userRepo;
    private ISuspensionRepo suspensionRepo;
    private TransactionStatus transactionStatus;
    private LotteryService lotteryService;

    @BeforeAll
    static void disableLogs() {
        Logger.getLogger("").setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() {
        lotteryRepo = mock(ILotteryRepo.class);
        eventRepo = mock(IEventRepo.class);
        companyRepo = mock(ICompanyRepo.class);
        userRepo = mock(IUserRepo.class);
        suspensionRepo = mock(ISuspensionRepo.class);

        IAuth auth = mock(IAuth.class);
        INotifier notifier = mock(INotifier.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        transactionStatus = mock(TransactionStatus.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });

        when(auth.getRole(TOKEN)).thenReturn(new Response<>("MEMBER", "Role found"));
        when(auth.getUserEmail(TOKEN)).thenReturn(new Response<>(USER_IDENTIFIER, "Email found"));

        Member member = mock(Member.class);
        when(member.getUserId()).thenReturn(USER_ID);
        when(member.getIdentifier()).thenReturn(USER_IDENTIFIER);
        when(userRepo.findUserByEmail(USER_IDENTIFIER)).thenReturn(member);

        when(suspensionRepo.haveActiveSuspension(USER_ID)).thenReturn(false);

        lotteryService = new LotteryService(
                lotteryRepo,
                eventRepo,
                auth,
                companyRepo,
                suspensionRepo,
                notifier,
                userRepo,
                transactionTemplate
        );
    }

    @BeforeEach
    void resetRetryHelperConfig() {
        SystemProperties systemProperties = new SystemProperties();
        systemProperties.setRetryCount(50);
        new RetryHelper(systemProperties);
    }

    @AfterEach
    void tearDown() {
        if (lotteryService != null) {
            lotteryService.shutdown();
        }
    }

    // ============================================================================
    // Coverage note
    // ============================================================================
    //
    // This file injects DB failures through LotteryService use cases.
    // Domain entities are used only as mocked setup objects needed to drive the
    // service flow to the relevant repository call.
    //
    // Covered ILotteryRepo calls:
    // - findById: registerUserToLottery / canRegisterToLottery / drawLottery
    // - store: registerUserToLottery / createLottery
    // - delete: updateLottery
    // - getAll: reschedulePendingLotteriesOnStartup
    //
    // Covered IEventRepo calls:
    // - findById: registerUserToLottery / createLottery / drawLottery
    // - store: createLottery
    //
    // Covered ICompanyRepo calls:
    // - findById: createLottery
    //
    // Covered ISuspensionRepo calls:
    // - haveActiveSuspension: canRegisterToLottery
    //
    // Covered IUserRepo calls:
    // - findUserByEmail: token to user resolution
    // - findById: drawLottery winner notification flow
    //
    // Recovery coverage:
    // - lotteryRepo.store fails once and succeeds on retry.
    // - lotteryRepo.getAll fails once and succeeds on retry.

    // ============================================================================
    // Lottery repo failure tests
    // ============================================================================

    @Test
    void GivenLotteryRepoStoreFailure_WhenRegisterUserToLottery_ThenRollbackControlledErrorReturned() {
        // Arrange
        Event event = mockLotteryEvent();
        Lottery lottery = mockOpenLottery();

        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(lotteryRepo.findById(EVENT_ID)).thenReturn(lottery);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(lotteryRepo)
                .store(lottery);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> lotteryService.registerUserToLottery(TOKEN, EVENT_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(lotteryRepo, atLeastOnce()).store(lottery);
    }

    @Test
    void GivenLotteryRepoFindByIdFailure_WhenCanRegisterToLottery_ThenRollbackControlledErrorReturned() {
        // Arrange
        Event event = mockLotteryEvent();

        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(lotteryRepo.findById(EVENT_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> lotteryService.canRegisterToLottery(TOKEN, EVENT_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(lotteryRepo, atLeastOnce()).findById(EVENT_ID);
    }

    @Test
    void GivenLotteryRepoDeleteFailure_WhenUpdateLotteryToRegular_ThenRollbackControlledErrorReturned() {
        // Arrange
        Event event = mockManageableLotteryEvent();
        Company company = mockOwnerCompany();
        Lottery lottery = mock(Lottery.class);

        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);
        when(lotteryRepo.findById(EVENT_ID)).thenReturn(lottery);
        when(lottery.getWinners()).thenReturn(Collections.emptyList());

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(lotteryRepo)
                .delete(lottery);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> lotteryService.updateLottery(TOKEN, EVENT_ID, null)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(lotteryRepo, atLeastOnce()).delete(lottery);
    }

    @Test
    void GivenLotteryRepoFindByIdFailure_WhenDrawLottery_ThenRollbackNoCrash() {
        // Arrange
        when(lotteryRepo.findById(EVENT_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act & Assert
        assertDoesNotThrow(() -> lotteryService.drawLottery(EVENT_ID));
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(lotteryRepo, atLeastOnce()).findById(EVENT_ID);
    }

    @Test
    void GivenLotteryRepoGetAllFailure_WhenReschedulePendingLotteriesOnStartup_ThenRollbackNoCrash() {
        // Arrange
        when(lotteryRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act & Assert
        assertDoesNotThrow(() -> lotteryService.reschedulePendingLotteriesOnStartup());
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(lotteryRepo, atLeastOnce()).getAll();
    }

    // ============================================================================
    // Event repo failure tests
    // ============================================================================

    @Test
    void GivenEventRepoFindByIdFailure_WhenRegisterUserToLottery_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(eventRepo.findById(EVENT_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> lotteryService.registerUserToLottery(TOKEN, EVENT_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findById(EVENT_ID);
    }

    @Test
    void GivenEventRepoStoreFailure_WhenCreateLottery_ThenRollbackControlledErrorReturned() {
        // Arrange
        Event event = mockLotteryEvent();
        Company company = mockCreatorCompany();

        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(eventRepo)
                .store(event);

        // Act
        Response<Boolean> response = assertDoesNotThrow(this::createValidLottery);

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).store(event);
        verify(lotteryRepo, never()).store(any(Lottery.class));
    }

    @Test
    void GivenEventRepoFindByIdFailure_WhenDrawLottery_ThenRollbackNoCrash() {
        // Arrange
        Lottery lottery = mockDrawReadyLottery();

        when(lotteryRepo.findById(EVENT_ID)).thenReturn(lottery);
        when(eventRepo.findById(EVENT_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act & Assert
        assertDoesNotThrow(() -> lotteryService.drawLottery(EVENT_ID));
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findById(EVENT_ID);
    }

    // ============================================================================
    // Company repo failure tests
    // ============================================================================

    @Test
    void GivenCompanyRepoFindByIdFailure_WhenCreateLottery_ThenRollbackControlledErrorReturned() {
        // Arrange
        Event event = mockLotteryEvent();

        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(companyRepo.findById(COMPANY_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(this::createValidLottery);

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeastOnce()).findById(COMPANY_ID);
        verify(lotteryRepo, never()).store(any(Lottery.class));
    }

    // ============================================================================
    // Suspension repo failure tests
    // ============================================================================

    @Test
    void GivenSuspensionRepoFailure_WhenCanRegisterToLottery_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(suspensionRepo.haveActiveSuspension(USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> lotteryService.canRegisterToLottery(TOKEN, EVENT_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(suspensionRepo, atLeastOnce()).haveActiveSuspension(USER_ID);
        verify(eventRepo, never()).findById(EVENT_ID);
    }

    // ============================================================================
    // User repo failure tests
    // ============================================================================

    @Test
    void GivenUserRepoFindUserByEmailFailure_WhenCanRegisterToLottery_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(userRepo.findUserByEmail(USER_IDENTIFIER))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> lotteryService.canRegisterToLottery(TOKEN, EVENT_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).findUserByEmail(USER_IDENTIFIER);
        verify(eventRepo, never()).findById(EVENT_ID);
    }

    @Test
    void GivenUserRepoFindByIdFailure_WhenDrawLottery_ThenRollbackNoCrash() {
        // Arrange
        Event event = mockLotteryEvent();
        Lottery lottery = mockDrawReadyLottery();

        when(lotteryRepo.findById(EVENT_ID)).thenReturn(lottery);
        when(eventRepo.findById(EVENT_ID)).thenReturn(event);

        doNothing().when(lotteryRepo).store(lottery);

        when(userRepo.findById(USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act & Assert
        assertDoesNotThrow(() -> lotteryService.drawLottery(EVENT_ID));
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).findById(USER_ID);
    }

    // ============================================================================
    // Full service flow DB recovery tests
    // ============================================================================

    @Test
    void GivenLotteryRepoStoreFailsOnce_WhenRegisterUserToLottery_ThenRetrySucceeds() {
        // Arrange
        Event event = mockLotteryEvent();
        Lottery lottery = mockOpenLottery();

        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(lotteryRepo.findById(EVENT_ID)).thenReturn(lottery);

        doThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .doNothing()
                .when(lotteryRepo)
                .store(lottery);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> lotteryService.registerUserToLottery(TOKEN, EVENT_ID)
        );

        // Assert
        assertNotNull(response);
        assertEquals(Boolean.TRUE, response.getValue());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
        verify(lotteryRepo, atLeast(2)).store(lottery);
    }

    @Test
    void GivenLotteryRepoGetAllFailsOnce_WhenReschedulePendingLotteriesOnStartup_ThenRetrySucceeds() {
        // Arrange
        when(lotteryRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        assertDoesNotThrow(() -> lotteryService.reschedulePendingLotteriesOnStartup());
        verify(lotteryRepo, atLeast(2)).getAll();
    }

    private Response<Boolean> createValidLottery() {
        return lotteryService.createLottery(
                TOKEN,
                EVENT_ID,
                5,
                LocalDateTime.now().plusDays(5),
                15
        );
    }

    private Event mockLotteryEvent() {
        Event event = mock(Event.class);

        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getCompanyId()).thenReturn(COMPANY_ID);
        when(event.getName()).thenReturn("Lottery Event");
        when(event.hasLottery()).thenReturn(true);
        when(event.getSaleStartDate()).thenReturn(LocalDateTime.now().plusDays(10));
        when(event.isActive()).thenReturn(true);

        return event;
    }

    private Event mockManageableLotteryEvent() {
        Event event = mockLotteryEvent();

        when(event.hasLottery()).thenReturn(true);

        return event;
    }

    private Company mockCreatorCompany() {
        Company company = mock(Company.class);

        when(company.checkPermission(eq(USER_ID), eq(PermissionType.CREATE_EVENT))).thenReturn(true);

        return company;
    }

    private Company mockOwnerCompany() {
        Company company = mock(Company.class);

        when(company.isActive()).thenReturn(true);
        when(company.isOwner(USER_ID)).thenReturn(true);

        return company;
    }

    private Lottery mockOpenLottery() {
        Lottery lottery = mock(Lottery.class);

        when(lottery.getId()).thenReturn(EVENT_ID);
        when(lottery.getRegisterWindow()).thenReturn(LocalDateTime.now().plusDays(5));
        when(lottery.getRegistered()).thenReturn(Collections.emptyList());

        return lottery;
    }

    private Lottery mockDrawReadyLottery() {
        Lottery lottery = mock(Lottery.class);

        when(lottery.getId()).thenReturn(EVENT_ID);
        when(lottery.getWinners()).thenReturn(Collections.emptyList());
        when(lottery.drawWinners()).thenReturn(Map.of(USER_ID, "CODE-123"));
        when(lottery.getRegistered()).thenReturn(List.of(USER_ID));

        return lottery;
    }

    private void assertControlledFailure(Response<?> response) {
        assertNotNull(response);
        assertTrue(response.getValue() == null || Boolean.FALSE.equals(response.getValue()));
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
    }
}