package application.dbFailure;

import DTO.PurchaseHistoryDTO;
import application.*;
import domain.Suspension.ISuspensionRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dataType.PermissionType;
import domain.dto.*;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventCompanyManageServiceDbFailureTest {

    private static final String TOKEN = "token";
    private static final String USER_IDENTIFIER = "owner@test.com";
    private static final int USER_ID = 1;
    private static final int COMPANY_ID = 10;
    private static final int EVENT_ID = 20;

    private ICompanyRepo companyRepo;
    private IEventRepo eventRepo;
    private IUserRepo userRepo;
    private ISuspensionRepo suspensionRepo;
    private TransactionStatus transactionStatus;
    private EventCompanyManageService service;

    @BeforeAll
    static void disableLogs() {
        Logger.getLogger("").setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() {
        companyRepo = mock(ICompanyRepo.class);
        eventRepo = mock(IEventRepo.class);
        IAuth auth = mock(IAuth.class);
        IPaymentSystem paymentSystem = mock(IPaymentSystem.class);
        suspensionRepo = mock(ISuspensionRepo.class);
        INotifier notifier = mock(INotifier.class);
        userRepo = mock(IUserRepo.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        transactionStatus = mock(TransactionStatus.class);
        ITicketSupply ticketSupply = mock(ITicketSupply.class);

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

        service = new EventCompanyManageService(
                companyRepo,
                eventRepo,
                auth,
                paymentSystem,
                suspensionRepo,
                notifier,
                userRepo,
                transactionTemplate,
                ticketSupply
        );
    }

    // ============================================================================
    // Coverage note
    // ============================================================================
    //
    // This file injects DB failures through EventCompanyManageService use cases.
    // Domain entities are used only as mocked setup objects needed to drive the
    // service flow to the relevant repository call.
    //
    // Covered repository calls:
    // IEventRepo:
    // - findById: getEventMapForManagement
    // - store: createEvent
    // - findByCompany: getOrdersByCompany
    // - getAll: getPurchaseHistoryByUser
    // - getAllEventPurchasers: UpdateEventDate
    //
    // ICompanyRepo:
    // - findById: createEvent
    //
    // IUserRepo:
    // - findUserByEmail: UpdateEventDate notification flow
    //
    // ISuspensionRepo:
    // - haveActiveSuspension: createEvent
    //
    // Recovery coverage:
    // - eventRepo.store fails once and succeeds on retry.

    // ============================================================================
    // Event repo failure tests
    // ============================================================================

    @Test
    void GivenEventRepoFindByIdFailure_WhenGetEventMapForManagement_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(eventRepo.findById(EVENT_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<EventMapDTO> response = assertDoesNotThrow(
                () -> service.getEventMapForManagement(TOKEN, EVENT_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findById(EVENT_ID);
    }

    @Test
    void GivenEventRepoStoreFailure_WhenCreateEvent_ThenRollbackControlledErrorReturned() {
        // Arrange
        mockValidCompany();

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(eventRepo)
                .store(any(Event.class));

        // Act
        Response<Integer> response = assertDoesNotThrow(this::createValidEvent);

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).store(any(Event.class));
    }

    @Test
    void GivenEventRepoFindByCompanyFailure_WhenGetOrdersByCompany_ThenRollbackControlledErrorReturned() {
        // Arrange
        mockValidCompany();

        when(eventRepo.findByCompany(COMPANY_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<List<OrderDTO>> response = assertDoesNotThrow(
                () -> service.getOrdersByCompany(TOKEN, COMPANY_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findByCompany(COMPANY_ID);
    }

    @Test
    void GivenEventRepoGetAllFailure_WhenGetPurchaseHistoryByUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(eventRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<List<PurchaseHistoryDTO>> response = assertDoesNotThrow(
                () -> service.getPurchaseHistoryByUser(TOKEN)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).getAll();
    }

    @Test
    void GivenEventRepoGetAllEventPurchasersFailure_WhenUpdateEventDate_ThenRollbackControlledErrorReturned() {
        // Arrange
        Event event = mockManageableEvent();
        Company company = mockValidCompany();

        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);

        doNothing().when(eventRepo).store(event);

        when(eventRepo.getAllEventPurchasers(EVENT_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> service.UpdateEventDate(TOKEN, EVENT_ID, LocalDateTime.now().plusDays(40))
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).getAllEventPurchasers(EVENT_ID);
    }

    // ============================================================================
    // Company repo failure tests
    // ============================================================================

    @Test
    void GivenCompanyRepoFailure_WhenCreateEvent_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(companyRepo.findById(COMPANY_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Integer> response = assertDoesNotThrow(this::createValidEvent);

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(companyRepo, atLeastOnce()).findById(COMPANY_ID);
        verify(eventRepo, never()).store(any(Event.class));
    }

    // ============================================================================
    // Suspension repo failure tests
    // ============================================================================

    @Test
    void GivenSuspensionRepoFailure_WhenCreateEvent_ThenControlledErrorReturned() {
        // Arrange
        when(suspensionRepo.haveActiveSuspension(USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Integer> response = assertDoesNotThrow(this::createValidEvent);

        // Assert
        assertControlledFailure(response);
        verify(suspensionRepo, atLeastOnce()).haveActiveSuspension(USER_ID);
        verify(eventRepo, never()).store(any(Event.class));
    }

    // ============================================================================
    // User repo failure tests
    // ============================================================================

    @Test
    void GivenUserRepoFindUserByEmailFailure_WhenUpdateEventDate_ThenControlledErrorReturned() {
        // Arrange
        Event event = mockManageableEvent();
        Company company = mockValidCompany();

        Member member = mock(Member.class);
        when(member.getUserId()).thenReturn(USER_ID);
        when(member.getIdentifier()).thenReturn(USER_IDENTIFIER);

        when(userRepo.findUserByEmail(USER_IDENTIFIER))
                .thenReturn(member)
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        when(eventRepo.findById(EVENT_ID)).thenReturn(event);
        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);
        doNothing().when(eventRepo).store(event);
        when(eventRepo.getAllEventPurchasers(EVENT_ID)).thenReturn(List.of(USER_IDENTIFIER));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> service.UpdateEventDate(TOKEN, EVENT_ID, LocalDateTime.now().plusDays(40))
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeast(2)).findUserByEmail(USER_IDENTIFIER);
    }


    // ============================================================================
    // Full service flow DB recovery tests
    // ============================================================================

    @Test
    void GivenEventRepoStoreFailsOnce_WhenCreateEvent_ThenRetrySucceeds() {
        // Arrange
        mockValidCompany();

        doThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .doAnswer(invocation -> {
                    Event event = invocation.getArgument(0);
                    event.setId(EVENT_ID);
                    return null;
                })
                .when(eventRepo)
                .store(any(Event.class));

        // Act
        Response<Integer> response = assertDoesNotThrow(this::createValidEvent);

        // Assert
        assertNotNull(response);
        assertEquals(EVENT_ID, response.getValue());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
        verify(eventRepo, atLeast(2)).store(any(Event.class));
    }

    private Response<Integer> createValidEvent() {
        return service.createEvent(
                TOKEN,
                COMPANY_ID,
                LocalDateTime.now().plusDays(30),
                "DB Failure Event",
                LocalDateTime.now().plusDays(10),
                false,
                GeographicalArea.CENTER,
                CategoryEvent.FESTIVAL
        );
    }

    private Company mockValidCompany() {
        Company company = mock(Company.class, RETURNS_DEEP_STUBS);

        when(company.isActive()).thenReturn(true);
        when(company.checkPermission(eq(USER_ID), any(PermissionType.class))).thenReturn(true);
        when(company.getCompanyPermission().checkPermission(eq(USER_ID), any(PermissionType.class))).thenReturn(true);

        when(companyRepo.findById(COMPANY_ID)).thenReturn(company);

        return company;
    }

    private Event mockManageableEvent() {
        Event event = mock(Event.class, RETURNS_DEEP_STUBS);

        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getCompanyId()).thenReturn(COMPANY_ID);
        when(event.getCreatorId()).thenReturn(USER_ID);
        when(event.getName()).thenReturn("Managed Event");
        when(event.getDate()).thenReturn(LocalDateTime.now().plusDays(30));
        when(event.getSaleStartDate()).thenReturn(LocalDateTime.now().minusDays(1));
        when(event.isActive()).thenReturn(true);
        when(event.hasLottery()).thenReturn(false);
        when(event.getOrders()).thenReturn(Collections.emptyList());

        return event;
    }

    private Member mockMember() {
        Member member = mock(Member.class);

        when(member.getUserId()).thenReturn(USER_ID);
        when(member.getIdentifier()).thenReturn(USER_IDENTIFIER);

        return member;
    }

    private void assertControlledFailure(Response<?> response) {
        assertNotNull(response);
        assertNull(response.getValue());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
    }
}