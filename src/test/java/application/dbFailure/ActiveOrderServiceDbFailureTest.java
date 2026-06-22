package application.dbFailure;

import DTO.EnterPurchaseDTO;
import app.config.ActiveOrderProperties;
import app.config.SystemProperties;
import application.*;
import domain.Suspension.ISuspensionRepo;
import domain.activeOrder.ActiveOrder;
import domain.activeOrder.IActiveOrderRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import DTO.ActiveOrderDTO;
import DTO.SeatingTicketDTO;
import domain.event.Event;
import domain.event.EventQueueManager;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActiveOrderServiceDbFailureTest {

    private static final String TOKEN = "token";
    private static final String USER_IDENTIFIER = "user@test.com";
    private static final String NEXT_TOKEN = "next-token";
    private static final String NEXT_USER_IDENTIFIER = "next-user@test.com";
    private static final int USER_ID = 1;
    private static final int COMPANY_ID = 10;
    private static final int EVENT_ID = 20;
    private static final int ACTIVE_ORDER_ID = 30;

    private IAuth auth;
    private IActiveOrderRepo activeOrderRepo;
    private IEventRepo eventRepo;
    private ICompanyRepo companyRepo;
    private ILotteryRepo lotteryRepo;
    private IUserRepo userRepo;
    private ISuspensionRepo suspensionRepo;
    private TransactionTemplate transactionTemplate;
    private TransactionStatus transactionStatus;
    private EventQueueManager eventQueueManager;
    private ActiveOrderService activeOrderService;

    @BeforeAll
    static void disableLogs() {
        Logger.getLogger("").setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() {
        auth = mock(IAuth.class);
        activeOrderRepo = mock(IActiveOrderRepo.class);
        eventRepo = mock(IEventRepo.class);
        companyRepo = mock(ICompanyRepo.class);
        lotteryRepo = mock(ILotteryRepo.class);
        userRepo = mock(IUserRepo.class);
        suspensionRepo = mock(ISuspensionRepo.class);

        IPaymentSystem paymentSystem = mock(IPaymentSystem.class);
        ITicketSupply ticketSupply = mock(ITicketSupply.class);
        INotifier notifier = mock(INotifier.class);
        PreExpirationNotificationScheduler preExpirationScheduler =
                mock(PreExpirationNotificationScheduler.class);

        transactionTemplate = mock(TransactionTemplate.class);
        transactionStatus = mock(TransactionStatus.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });

        eventQueueManager = mock(EventQueueManager.class);

        ActiveOrderProperties activeOrderProperties = mock(ActiveOrderProperties.class);
        when(activeOrderProperties.getCapacity()).thenReturn(1);
        when(activeOrderProperties.getSelectingTimeoutMinutes()).thenReturn(10);
        when(activeOrderProperties.getCheckoutTimeoutMinutes()).thenReturn(10);
        when(activeOrderProperties.getWarningBeforeExpiryMinutes()).thenReturn(2);

        mockValidToken(TOKEN, USER_IDENTIFIER);
        mockValidToken(NEXT_TOKEN, NEXT_USER_IDENTIFIER);
        when(suspensionRepo.haveActiveSuspension(anyInt())).thenReturn(false);

        activeOrderService = new ActiveOrderService(
                auth,
                activeOrderRepo,
                eventRepo,
                companyRepo,
                lotteryRepo,
                paymentSystem,
                ticketSupply,
                suspensionRepo,
                notifier,
                preExpirationScheduler,
                userRepo,
                transactionTemplate,
                activeOrderProperties,
                eventQueueManager
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
        if (activeOrderService != null) {
            activeOrderService.shutdown();
        }
    }

    // ============================================================================
    // Coverage note
    // ============================================================================
    //
    // This file injects DB failures through ActiveOrderService use cases.
    // Domain entities are used only as mocked setup objects needed to drive the
    // service flow to the relevant repository call.
    //
    // Covered IActiveOrderRepo calls:
    // - findExpired: cleanupExpiredOrders
    // - getAll: onStartupRecovery
    // - findOrderByUserId: returnToEditSelection / enterEventPurchase
    // - findById: getCompanyIdByActiveOrder / cleanupExpiredOrders
    // - findActiveOrderByUserAndEvent: userSelectTickets
    // - countActiveOrdersForEvent: enterEventPurchase
    // - store: enterEventPurchase
    // - delete: cleanupExpiredOrders
    // - alreadyHasActiveOrder: cleanupExpiredOrders -> queue promotion

    // ============================================================================
    // Active order repo failure tests
    // ============================================================================

    @Test
    void GivenActiveOrderRepoFindOrderByUserIdFailure_WhenReturnToEditSelection_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(activeOrderRepo.findOrderByUserId(USER_IDENTIFIER))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<ActiveOrderDTO> response = assertDoesNotThrow(
                () -> activeOrderService.returnToEditSelection(TOKEN)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).findOrderByUserId(USER_IDENTIFIER);
    }

    @Test
    void GivenActiveOrderRepoFindByIdFailure_WhenGetCompanyIdByActiveOrder_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(activeOrderRepo.findById(ACTIVE_ORDER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Integer> response = assertDoesNotThrow(
                () -> activeOrderService.getCompanyIdByActiveOrder(TOKEN, ACTIVE_ORDER_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).findById(ACTIVE_ORDER_ID);
    }

    @Test
    void GivenActiveOrderRepoFindActiveOrderByUserEventFailure_WhenUserSelectTickets_ThenRollbackControlledErrorReturned() {
        // Arrange
        Event event = mockSellableEvent();
        Company company = mockCompany();

        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);

        when(activeOrderRepo.findActiveOrderByUserAndEvent(USER_IDENTIFIER, EVENT_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Integer> response = assertDoesNotThrow(
                () -> activeOrderService.userSelectTickets(
                        TOKEN,
                        EVENT_ID,
                        Collections.<String, List<SeatingTicketDTO>>emptyMap(),
                        Collections.emptyMap()
                )
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce())
                .findActiveOrderByUserAndEvent(USER_IDENTIFIER, EVENT_ID);
    }

    @Test
    void GivenActiveOrderRepoCountFailure_WhenEnterEventPurchase_ThenRollbackControlledErrorReturned() {
        // Arrange
        Event event = mockSellableEvent();
        Company company = mockCompany();

        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);
        when(activeOrderRepo.findOrderByUserId(USER_IDENTIFIER))
                .thenThrow(new NoSuchElementException("No active order"));

        when(activeOrderRepo.countActiveOrdersForEvent(EVENT_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<EnterPurchaseDTO> response = assertDoesNotThrow(
                () -> activeOrderService.enterEventPurchase(TOKEN, COMPANY_ID, EVENT_ID, null)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).countActiveOrdersForEvent(EVENT_ID);
    }

    @Test
    void GivenActiveOrderRepoStoreFailure_WhenEnterEventPurchase_ThenRollbackControlledErrorReturned() {
        // Arrange
        Event event = mockSellableEvent();
        Company company = mockCompany();

        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);
        when(activeOrderRepo.findOrderByUserId(USER_IDENTIFIER))
                .thenThrow(new NoSuchElementException("No active order"));
        when(activeOrderRepo.countActiveOrdersForEvent(EVENT_ID)).thenReturn(0);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(activeOrderRepo)
                .store(any());

        // Act
        Response<EnterPurchaseDTO> response = assertDoesNotThrow(
                () -> activeOrderService.enterEventPurchase(TOKEN, COMPANY_ID, EVENT_ID, null)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).store(any());
    }

    @Test
    void GivenActiveOrderRepoGetAllFailure_WhenStartupRecovery_ThenRollbackNoCrash() {
        // Arrange
        when(activeOrderRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("DB is down"));
        when(activeOrderRepo.findExpired(any(LocalDateTime.class), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        assertDoesNotThrow(() -> activeOrderService.onStartupRecovery());
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).getAll();
    }

    @Test
    void GivenActiveOrderRepoFindExpiredFailure_WhenCleanupExpiredOrders_ThenNoCrash() {
        // Arrange
        when(activeOrderRepo.findExpired(any(LocalDateTime.class), anyInt(), anyInt()))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act & Assert
        assertDoesNotThrow(() -> activeOrderService.cleanupExpiredOrders());
        verify(activeOrderRepo, atLeastOnce())
                .findExpired(any(LocalDateTime.class), anyInt(), anyInt());
    }

    @Test
    void GivenActiveOrderRepoDeleteFailure_WhenCleanupExpiredOrders_ThenRollbackNoCrash() {
        // Arrange
        ActiveOrder expiredOrder = mockExpiredOrder();
        Event event = mock(Event.class);

        when(activeOrderRepo.findExpired(any(LocalDateTime.class), anyInt(), anyInt()))
                .thenReturn(List.of(expiredOrder));
        when(activeOrderRepo.findById(ACTIVE_ORDER_ID)).thenReturn(expiredOrder);
        when(eventRepo.findById(EVENT_ID)).thenReturn(event);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(activeOrderRepo)
                .delete(ACTIVE_ORDER_ID);

        // Act & Assert
        assertDoesNotThrow(() -> activeOrderService.cleanupExpiredOrders());
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).delete(ACTIVE_ORDER_ID);
    }

    @Test
    void GivenActiveOrderRepoAlreadyHasActiveOrderFailure_WhenCleanupPromotesQueue_ThenRollbackNoCrash() {
        // Arrange
        ActiveOrder expiredOrder = mockExpiredOrder();
        Event event = mock(Event.class, RETURNS_DEEP_STUBS);

        when(event.getId()).thenReturn(EVENT_ID);

        when(activeOrderRepo.findExpired(any(LocalDateTime.class), anyInt(), anyInt()))
                .thenReturn(List.of(expiredOrder));
        when(activeOrderRepo.findById(ACTIVE_ORDER_ID)).thenReturn(expiredOrder);
        when(eventRepo.findById(EVENT_ID)).thenReturn(event);

        when(eventQueueManager.isEmpty(EVENT_ID)).thenReturn(false);
        when(eventQueueManager.dequeue(EVENT_ID)).thenReturn(NEXT_TOKEN);
        when(activeOrderRepo.countActiveOrdersForEvent(EVENT_ID)).thenReturn(0);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(activeOrderRepo)
                .alreadyHasActiveOrder(NEXT_USER_IDENTIFIER, EVENT_ID);

        // Act & Assert
        assertDoesNotThrow(() -> activeOrderService.cleanupExpiredOrders());
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce())
                .alreadyHasActiveOrder(NEXT_USER_IDENTIFIER, EVENT_ID);
    }

    // ============================================================================
    // Full service flow DB recovery tests
    // ============================================================================

    @Test
    void GivenActiveOrderRepoGetAllFailsOnce_WhenStartupRecovery_ThenRetrySucceeds() {
        // Arrange
        when(activeOrderRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .thenReturn(Collections.emptyList());

        when(activeOrderRepo.findExpired(any(LocalDateTime.class), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        assertDoesNotThrow(() -> activeOrderService.onStartupRecovery());
        verify(activeOrderRepo, atLeast(2)).getAll();
    }

    @Test
    void GivenActiveOrderRepoFindExpiredFailsOnce_WhenCleanupExpiredOrders_ThenRetrySucceeds() {
        // Arrange
        when(activeOrderRepo.findExpired(any(LocalDateTime.class), anyInt(), anyInt()))
                .thenThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        assertDoesNotThrow(() -> activeOrderService.cleanupExpiredOrders());
        verify(activeOrderRepo, atLeast(2))
                .findExpired(any(LocalDateTime.class), anyInt(), anyInt());
    }

    private void mockValidToken(String token, String userIdentifier) {
        Member member = mock(Member.class);

        when(member.getUserId()).thenReturn(USER_ID);

        when(auth.getRole(token)).thenReturn(new Response<>("MEMBER", "Role found"));
        when(auth.getUserEmail(token)).thenReturn(new Response<>(userIdentifier, "Email found"));
        when(auth.getUserIdentifier(token)).thenReturn(new Response<>(userIdentifier, "Identifier found"));
        when(userRepo.findUserByEmail(userIdentifier)).thenReturn(member);
    }

    private Event mockSellableEvent() {
        Event event = mock(Event.class, RETURNS_DEEP_STUBS);

        when(event.getCompanyId()).thenReturn(COMPANY_ID);
        when(event.isActive()).thenReturn(true);
        when(event.getSaleStartDate()).thenReturn(LocalDateTime.now().minusDays(1));
        when(event.hasLottery()).thenReturn(false);
        when(event.getPurchasePolicy().describe()).thenReturn("No event purchase policy");

        return event;
    }

    private Company mockCompany() {
        Company company = mock(Company.class, RETURNS_DEEP_STUBS);

        when(company.getPurchasePolicy().describe()).thenReturn("No company purchase policy");

        return company;
    }

    private ActiveOrder mockExpiredOrder() {
        ActiveOrder order = mock(ActiveOrder.class);

        when(order.getId()).thenReturn(ACTIVE_ORDER_ID);
        when(order.getEventId()).thenReturn(EVENT_ID);
        when(order.getTickets()).thenReturn(Collections.emptyList());

        return order;
    }

    private void assertControlledFailure(Response<?> response) {
        assertNotNull(response);
        assertNull(response.getValue());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
    }
}