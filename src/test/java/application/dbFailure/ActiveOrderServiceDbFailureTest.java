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
import domain.eventQueue.EventQueue;
import infrastructure.inMemory.EventQueueRepoImpl;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.user.IUserRepo;
import domain.user.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import Exception.OptimisticLockingFailureException;
import DTO.NotifyDTO;
import DTO.NotifyPayload;
import DTO.NotifyType;
import domain.activeOrder.STAGE;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
    private IPaymentSystem paymentSystem;
    private ITicketSupply ticketSupply;
    private INotifier notifier;
    private PreExpirationNotificationScheduler preExpirationScheduler;
    private TransactionTemplate transactionTemplate;
    private TransactionStatus transactionStatus;
    private EventQueueRepoImpl eventQueueRepoImpl;
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

        paymentSystem = mock(IPaymentSystem.class);
        ticketSupply = mock(ITicketSupply.class);
        notifier = mock(INotifier.class);
        preExpirationScheduler = mock(PreExpirationNotificationScheduler.class);

        transactionTemplate = mock(TransactionTemplate.class);
        transactionStatus = mock(TransactionStatus.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });

        eventQueueRepoImpl = mock(EventQueueRepoImpl.class);

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
                eventQueueRepoImpl
        );
    }

    @BeforeEach
    void resetRetryHelperConfig() {
        SystemProperties systemProperties = new SystemProperties();
        systemProperties.setRetryCount(3);
        systemProperties.setRetryJitterMaxMs(0);

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoFindOrderByUserIdFailure_WhenReturnToEditSelection_ThenRollbackControlledErrorReturned(RuntimeException arm) {
        when(activeOrderRepo.findOrderByUserId(USER_IDENTIFIER)).thenThrow(arm);

        Response<?> response = assertDoesNotThrow(
                () -> activeOrderService.returnToEditSelection(TOKEN));

        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).findOrderByUserId(USER_IDENTIFIER);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoFindByIdFailure_WhenGetCompanyIdByActiveOrder_ThenRollbackControlledErrorReturned(RuntimeException arm) {
        when(activeOrderRepo.findById(ACTIVE_ORDER_ID)).thenThrow(arm);

        Response<?> response = assertDoesNotThrow(
                () -> activeOrderService.getCompanyIdByActiveOrder(TOKEN, ACTIVE_ORDER_ID));

        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).findById(ACTIVE_ORDER_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenEventRepoFindByIdFailure_WhenUserSelectTickets_ThenRollbackControlledErrorReturned(RuntimeException arm) {
        when(eventRepo.findById(EVENT_ID)).thenThrow(arm);

        Response<?> response = assertDoesNotThrow(
                () -> activeOrderService.userSelectTickets(
                        TOKEN,
                        EVENT_ID,
                        Collections.<String, List<SeatingTicketDTO>>emptyMap(),
                        Collections.emptyMap()));

        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findById(EVENT_ID);
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoGetAllFailure_WhenStartupRecovery_ThenRollbackNoCrash(RuntimeException arm) {
        when(activeOrderRepo.getAll()).thenThrow(arm);
        when(activeOrderRepo.findExpired(any(LocalDateTime.class), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> activeOrderService.onStartupRecovery());

        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).getAll();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoFindExpiredFailure_WhenCleanupExpiredOrders_ThenNoCrash(RuntimeException arm) {
        when(activeOrderRepo.findExpired(any(LocalDateTime.class), anyInt(), anyInt())).thenThrow(arm);

        assertDoesNotThrow(() -> activeOrderService.cleanupExpiredOrders());

        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce())
                .findExpired(any(LocalDateTime.class), anyInt(), anyInt());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoDeleteFailure_WhenCleanupExpiredOrders_ThenRollbackNoCrash(RuntimeException arm) {
        ActiveOrder expiredOrder = mockExpiredOrder();
        Event event = mock(Event.class);

        when(activeOrderRepo.findExpired(any(LocalDateTime.class), anyInt(), anyInt()))
                .thenReturn(List.of(expiredOrder));
        when(activeOrderRepo.findById(ACTIVE_ORDER_ID)).thenReturn(expiredOrder);
        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        doThrow(arm).when(activeOrderRepo).delete(ACTIVE_ORDER_ID);

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

        EventQueue queue = mock(EventQueue.class);

        when(eventQueueRepoImpl.findById(EVENT_ID)).thenReturn(queue);
        when(queue.isEmpty()).thenReturn(false);
        when(queue.dequeue()).thenReturn(NEXT_TOKEN);
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

    // ============================================================================
    // Per-arm cascade coverage
    //
    // Every transactional block carries the same DB exception cascade
    // (optimistic-lock -> cannot-create-transaction -> query-timeout ->
    // resource-failure -> transient -> transaction-timeout -> non-transient ->
    // transaction -> data-access -> generic). These parameterized tests fire
    // each arm through every newly protected block and assert the service
    // degrades to a controlled Response (never throws) while flagging the
    // transaction for rollback on the way out.
    // ============================================================================

    private static Stream<Arguments> dbExceptionArms() {
        return Stream.of(
                arguments(named("OptimisticLockingFailureException (retryable)",
                        new OptimisticLockingFailureException("optimistic lock conflict"))),
                arguments(named("CannotCreateTransactionException (retryable)",
                        new CannotCreateTransactionException("cannot create transaction"))),
                arguments(named("QueryTimeoutException (retryable)",
                        new QueryTimeoutException("query timed out"))),
                arguments(named("DataAccessResourceFailureException (retryable)",
                        new DataAccessResourceFailureException("resource failure"))),
                arguments(named("TransientDataAccessException (retryable)",
                        new TransientDataAccessResourceException("transient failure"))),
                arguments(named("TransactionTimedOutException (retryable)",
                        new TransactionTimedOutException("transaction timed out"))),
                arguments(named("NonTransientDataAccessException (non-retryable)",
                        new DataIntegrityViolationException("integrity violation"))),
                arguments(named("TransactionException (retryable)",
                        new TransactionSystemException("transaction system error"))),
                arguments(named("DataAccessException (retryable)",
                        new RecoverableDataAccessException("recoverable access error"))),
                arguments(named("Generic Exception (non-retryable)",
                        new RuntimeException("unexpected failure")))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoFindOrderByUserIdFailure_WhenGetCurrentActiveOrderSelection_ThenRollbackControlledErrorReturned(RuntimeException arm) {
        when(activeOrderRepo.findOrderByUserId(USER_IDENTIFIER)).thenThrow(arm);

        Response<?> response = assertDoesNotThrow(
                () -> activeOrderService.getCurrentActiveOrderSelection(TOKEN));

        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).findOrderByUserId(USER_IDENTIFIER);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoFindOrderByUserIdFailure_WhenMemberProceedAnActiveOrder_ThenRollbackControlledErrorReturned(RuntimeException arm) {
        when(activeOrderRepo.findOrderByUserId(USER_IDENTIFIER)).thenThrow(arm);

        Response<?> response = assertDoesNotThrow(
                () -> activeOrderService.memberProceedAnActiveOrder(TOKEN));

        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).findOrderByUserId(USER_IDENTIFIER);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoFindOrderByUserIdFailure_WhenEditTicketSelection_ThenRollbackControlledErrorReturned(RuntimeException arm) {
        when(activeOrderRepo.findOrderByUserId(USER_IDENTIFIER)).thenThrow(arm);

        Response<?> response = assertDoesNotThrow(
                () -> activeOrderService.editTicketSelection(
                        TOKEN,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap()));

        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).findOrderByUserId(USER_IDENTIFIER);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoFindByIdFailure_WhenPrepareCheckout_ThenRollbackControlledErrorReturned(RuntimeException arm) {
        when(activeOrderRepo.findById(ACTIVE_ORDER_ID)).thenThrow(arm);

        Response<?> response = assertDoesNotThrow(
                () -> activeOrderService.prepareCheckout(TOKEN, ACTIVE_ORDER_ID));

        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).findById(ACTIVE_ORDER_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoFindByIdFailure_WhenCheckoutAndPayment_ThenRollbackControlledErrorReturned(RuntimeException arm) {
        when(activeOrderRepo.findById(ACTIVE_ORDER_ID)).thenThrow(arm);

        Response<?> response = assertDoesNotThrow(
                () -> activeOrderService.checkoutAndPayment(TOKEN, ACTIVE_ORDER_ID, null));

        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).findById(ACTIVE_ORDER_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenEventRepoFindByIdFailureAfterPayment_WhenCheckoutAndPayment_ThenRollbackControlledErrorReturned(RuntimeException arm) {
        ActiveOrder readyOrder = mockCheckoutReadyOrder();
        Event event = mockCheckoutEvent();

        when(activeOrderRepo.findById(ACTIVE_ORDER_ID)).thenReturn(readyOrder);
        when(eventRepo.findById(EVENT_ID)).thenReturn(event).thenThrow(arm);
        when(paymentSystem.pay(anyDouble(), any())).thenReturn("CONFIRMATION-1");

        Response<?> response = assertDoesNotThrow(
                () -> activeOrderService.checkoutAndPayment(TOKEN, ACTIVE_ORDER_ID, null));

        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeast(2)).findById(EVENT_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoFindByIdFailureAfterDeclinedPayment_WhenCheckoutAndPayment_ThenRollbackNoCrash(RuntimeException arm) {
        ActiveOrder readyOrder = mockCheckoutReadyOrder();
        Event event = mockCheckoutEvent();

        when(activeOrderRepo.findById(ACTIVE_ORDER_ID)).thenReturn(readyOrder).thenThrow(arm);
        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(paymentSystem.pay(anyDouble(), any())).thenReturn(null);

        Response<?> response = assertDoesNotThrow(
                () -> activeOrderService.checkoutAndPayment(TOKEN, ACTIVE_ORDER_ID, null));

        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeast(2)).findById(ACTIVE_ORDER_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenActiveOrderRepoStoreFailureForDanglingOrder_WhenStartupRecovery_ThenRollbackNoCrash(RuntimeException arm) {
        ActiveOrder dangling = mock(ActiveOrder.class);
        when(dangling.getId()).thenReturn(ACTIVE_ORDER_ID);
        when(dangling.getStage()).thenReturn(STAGE.PAYMENT_IN_PROGRESS);
        when(dangling.getUserIdentifier()).thenReturn(USER_IDENTIFIER);

        when(activeOrderRepo.getAll()).thenReturn(List.of(dangling));
        when(activeOrderRepo.findById(ACTIVE_ORDER_ID)).thenReturn(dangling);
        when(activeOrderRepo.findExpired(any(LocalDateTime.class), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        doThrow(arm).when(activeOrderRepo).store(dangling);

        assertDoesNotThrow(() -> activeOrderService.onStartupRecovery());

        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(activeOrderRepo, atLeastOnce()).store(dangling);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenUserRepoStoreFailure_WhenSaveDelayedNotificationAsPending_ThenRollbackControlledErrorReturned(RuntimeException arm) {
        NotifyDTO notifyDTO = new NotifyDTO(NotifyType.GENERAL_POPUP, new NotifyPayload("coverage notification"));
        doThrow(arm).when(userRepo).store(any());

        Response<?> response = invokePrivate(
                "saveDelayedNotificationAsPending",
                new Class<?>[]{String.class, NotifyDTO.class},
                USER_IDENTIFIER, notifyDTO);

        assertNotNull(response);
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).store(any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbExceptionArms")
    void GivenUserRepoStoreFailure_WhenMarkNotificationAsDelivered_ThenRollbackControlledErrorReturned(RuntimeException arm) {
        doThrow(arm).when(userRepo).store(any());

        Response<?> response = invokePrivate(
                "markNotificationAsDelivered",
                new Class<?>[]{String.class, Long.class},
                USER_IDENTIFIER, 1L);

        assertNotNull(response);
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).store(any());
    }

    private ActiveOrder mockCheckoutReadyOrder() {
        ActiveOrder order = mock(ActiveOrder.class);
        when(order.getUserIdentifier()).thenReturn(USER_IDENTIFIER);
        when(order.getEventId()).thenReturn(EVENT_ID);
        when(order.hasTickets()).thenReturn(true);
        when(order.isExpired(any(LocalDateTime.class), anyInt(), anyInt())).thenReturn(false);
        when(order.hasApprovedCheckoutPrice()).thenReturn(true);
        when(order.getApprovedCheckoutPrice()).thenReturn(100.0);
        when(order.getTickets()).thenReturn(new ArrayList<>());
        return order;
    }

    private Event mockCheckoutEvent() {
        Event event = mock(Event.class);
        when(event.isActive()).thenReturn(true);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getName()).thenReturn("Coverage Event");
        when(event.getCompanyId()).thenReturn(COMPANY_ID);
        when(event.getPurchasedTicketDetails(any())).thenReturn(new ArrayList<>());
        return event;
    }

    private Response<?> invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) {
        return assertDoesNotThrow(() -> {
            Method method = ActiveOrderService.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return (Response<?>) method.invoke(activeOrderService, args);
        });
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