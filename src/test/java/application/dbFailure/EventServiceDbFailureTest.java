package application.dbFailure;

import DTO.EventDTO;
import DTO.EventDetailsDTO;
import app.config.SystemProperties;
import application.*;
import domain.dataType.EventSearchFilter;
import domain.event.Event;
import domain.event.IEventRepo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.*;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventServiceDbFailureTest {

    private static final String TOKEN = "token";
    private static final int COMPANY_ID = 10;
    private static final int EVENT_ID = 20;

    private IAuth auth;
    private IEventRepo eventRepo;
    private TransactionStatus transactionStatus;
    private EventService eventService;

    @BeforeAll
    static void disableLogs() {
        Logger.getLogger("").setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() {
        auth = mock(IAuth.class);
        eventRepo = mock(IEventRepo.class);
        INotifier notifier = mock(INotifier.class);

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        transactionStatus = mock(TransactionStatus.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });

        when(auth.getRole(TOKEN)).thenReturn(new Response<>("MEMBER", "Role found"));

        eventService = new EventService(
                auth,
                eventRepo,
                notifier,
                transactionTemplate
        );
    }

    @BeforeEach
    void resetRetryHelperConfig() {
        SystemProperties systemProperties = new SystemProperties();

        // Keep this low for tests, otherwise every persistent DB failure test
        // performs many retry attempts and becomes slow.
        systemProperties.setRetryCount(2);
        systemProperties.setRetryJitterMaxMs(0);

        new RetryHelper(systemProperties);
    }

    // ============================================================================
    // Event repo persistent failure tests
    // ============================================================================

    @Test
    void GivenEventRepoFindByIdFailure_WhenViewEventDetails_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(eventRepo.findById(EVENT_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<EventDetailsDTO> response = assertDoesNotThrow(
                () -> eventService.ViewEventDetails(TOKEN, COMPANY_ID, EVENT_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findById(EVENT_ID);
    }

    @Test
    void GivenEventRepoGetAllFailure_WhenSearchEvents_ThenRollbackControlledErrorReturned() {
        // Arrange
        EventSearchFilter filter = mock(EventSearchFilter.class);

        when(eventRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<List<EventDTO>> response = assertDoesNotThrow(
                () -> eventService.searchEvents(TOKEN, filter)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).getAll();
    }

    @Test
    void GivenEventRepoFindByCompanyFailure_WhenSearchCompanyEvents_ThenRollbackControlledErrorReturned() {
        // Arrange
        EventSearchFilter filter = mock(EventSearchFilter.class);

        when(eventRepo.findByCompany(COMPANY_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<List<EventDTO>> response = assertDoesNotThrow(
                () -> eventService.searchCompanyEvents(TOKEN, COMPANY_ID, filter)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findByCompany(COMPANY_ID);
    }

    // ============================================================================
    // Full service flow DB recovery tests
    // ============================================================================

    @Test
    void GivenEventRepoFindByIdFailsOnce_WhenViewEventDetails_ThenRetryIsPerformed() {
        // Arrange
        Event event = mockActiveEvent();

        when(eventRepo.findById(EVENT_ID))
                .thenThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .thenReturn(event);

        // Act
        assertDoesNotThrow(
                () -> eventService.ViewEventDetails(TOKEN, COMPANY_ID, EVENT_ID)
        );

        // Assert
        verify(eventRepo, atLeast(2)).findById(EVENT_ID);
    }

    @Test
    void GivenEventRepoGetAllFailsOnce_WhenSearchEvents_ThenRetryIsPerformed() {
        // Arrange
        EventSearchFilter filter = mock(EventSearchFilter.class);
        Event event = mockMatchingEvent(filter);

        when(eventRepo.getAll())
                .thenThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .thenReturn(List.of(event));

        // Act
        assertDoesNotThrow(
                () -> eventService.searchEvents(TOKEN, filter)
        );

        // Assert
        verify(eventRepo, atLeast(2)).getAll();
    }

    @Test
    void GivenEventRepoFindByCompanyFailsOnce_WhenSearchCompanyEvents_ThenRetryIsPerformed() {
        // Arrange
        EventSearchFilter filter = mock(EventSearchFilter.class);
        Event event = mockMatchingEvent(filter);

        when(eventRepo.findByCompany(COMPANY_ID))
                .thenThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .thenReturn(List.of(event));

        // Act
        assertDoesNotThrow(
                () -> eventService.searchCompanyEvents(TOKEN, COMPANY_ID, filter)
        );

        // Assert
        verify(eventRepo, atLeast(2)).findByCompany(COMPANY_ID);
    }

    // ============================================================================
    // ViewEventDetails
    // ============================================================================

    @ParameterizedTest
    @MethodSource("retryableDbExceptions")
    void GivenRetryableDbException_WhenViewEventDetails_ThenRollbackControlledFailureReturned(
            Supplier<RuntimeException> exceptionSupplier
    ) {
        // Arrange
        when(eventRepo.findById(EVENT_ID))
                .thenThrow(exceptionSupplier.get());

        // Act
        Response<EventDetailsDTO> response = assertDoesNotThrow(
                () -> eventService.ViewEventDetails(TOKEN, COMPANY_ID, EVENT_ID)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findById(EVENT_ID);
    }

    @Test
    void GivenNonTransientDbException_WhenViewEventDetails_ThenDatabaseErrorResponseReturned() {
        // Arrange
        when(eventRepo.findById(EVENT_ID))
                .thenThrow(new DataIntegrityViolationException("Permanent DB failure"));

        // Act
        Response<EventDetailsDTO> response = assertDoesNotThrow(
                () -> eventService.ViewEventDetails(TOKEN, COMPANY_ID, EVENT_ID)
        );

        // Assert
        assertNotNull(response);
        assertNull(response.getValue());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().startsWith("Database error:"));
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
    }

    @Test
    void GivenUnexpectedException_WhenViewEventDetails_ThenSystemErrorResponseReturned() {
        // Arrange
        when(eventRepo.findById(EVENT_ID))
                .thenThrow(new RuntimeException("Unexpected failure"));

        // Act
        Response<EventDetailsDTO> response = assertDoesNotThrow(
                () -> eventService.ViewEventDetails(TOKEN, COMPANY_ID, EVENT_ID)
        );

        // Assert
        assertNotNull(response);
        assertNull(response.getValue());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().startsWith("System error:"));
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
    }

    // ============================================================================
    // searchEvents
    // ============================================================================

    @ParameterizedTest
    @MethodSource("retryableDbExceptions")
    void GivenRetryableDbException_WhenSearchEvents_ThenRollbackControlledFailureReturned(
            Supplier<RuntimeException> exceptionSupplier
    ) {
        // Arrange
        EventSearchFilter filter = mock(EventSearchFilter.class);

        when(eventRepo.getAll())
                .thenThrow(exceptionSupplier.get());

        // Act
        Response<List<EventDTO>> response = assertDoesNotThrow(
                () -> eventService.searchEvents(TOKEN, filter)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).getAll();
    }

    @Test
    void GivenNonTransientDbException_WhenSearchEvents_ThenDatabaseErrorResponseReturned() {
        // Arrange
        EventSearchFilter filter = mock(EventSearchFilter.class);

        when(eventRepo.getAll())
                .thenThrow(new DataIntegrityViolationException("Permanent DB failure"));

        // Act
        Response<List<EventDTO>> response = assertDoesNotThrow(
                () -> eventService.searchEvents(TOKEN, filter)
        );

        // Assert
        assertNotNull(response);
        assertNull(response.getValue());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().startsWith("Database error:"));
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
    }

    @Test
    void GivenUnexpectedException_WhenSearchEvents_ThenSystemErrorResponseReturned() {
        // Arrange
        EventSearchFilter filter = mock(EventSearchFilter.class);

        when(eventRepo.getAll())
                .thenThrow(new RuntimeException("Unexpected failure"));

        // Act
        Response<List<EventDTO>> response = assertDoesNotThrow(
                () -> eventService.searchEvents(TOKEN, filter)
        );

        // Assert
        assertNotNull(response);
        assertNull(response.getValue());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().startsWith("System error:"));
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
    }

    // ============================================================================
    // searchCompanyEvents
    // ============================================================================

    @ParameterizedTest
    @MethodSource("retryableDbExceptions")
    void GivenRetryableDbException_WhenSearchCompanyEvents_ThenRollbackControlledFailureReturned(
            Supplier<RuntimeException> exceptionSupplier
    ) {
        // Arrange
        EventSearchFilter filter = mock(EventSearchFilter.class);

        when(eventRepo.findByCompany(COMPANY_ID))
                .thenThrow(exceptionSupplier.get());

        // Act
        Response<List<EventDTO>> response = assertDoesNotThrow(
                () -> eventService.searchCompanyEvents(TOKEN, COMPANY_ID, filter)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(eventRepo, atLeastOnce()).findByCompany(COMPANY_ID);
    }

    @Test
    void GivenNonTransientDbException_WhenSearchCompanyEvents_ThenDatabaseErrorResponseReturned() {
        // Arrange
        EventSearchFilter filter = mock(EventSearchFilter.class);

        when(eventRepo.findByCompany(COMPANY_ID))
                .thenThrow(new DataIntegrityViolationException("Permanent DB failure"));

        // Act
        Response<List<EventDTO>> response = assertDoesNotThrow(
                () -> eventService.searchCompanyEvents(TOKEN, COMPANY_ID, filter)
        );

        // Assert
        assertNotNull(response);
        assertNull(response.getValue());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().startsWith("Database error:"));
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
    }

    @Test
    void GivenUnexpectedException_WhenSearchCompanyEvents_ThenSystemErrorResponseReturned() {
        // Arrange
        EventSearchFilter filter = mock(EventSearchFilter.class);

        when(eventRepo.findByCompany(COMPANY_ID))
                .thenThrow(new RuntimeException("Unexpected failure"));

        // Act
        Response<List<EventDTO>> response = assertDoesNotThrow(
                () -> eventService.searchCompanyEvents(TOKEN, COMPANY_ID, filter)
        );

        // Assert
        assertNotNull(response);
        assertNull(response.getValue());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().startsWith("System error:"));
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
    }

    // ============================================================================
    // Test data
    // ============================================================================

    private static Stream<Supplier<RuntimeException>> retryableDbExceptions() {
        return Stream.of(
                () -> new CannotCreateTransactionException("Could not create transaction"),
                () -> new QueryTimeoutException("Query timed out"),
                () -> new DataAccessResourceFailureException("Database resource failure"),
                () -> new TransientDataAccessResourceException("Transient DB failure"),
                () -> new TransactionTimedOutException("Transaction timed out"),
                () -> new TransactionSystemException("Transaction infrastructure failure"),
                () -> new RecoverableDataAccessException("Recoverable DB failure"),
                () -> new DataAccessException("Generic data access failure") {}
        );
    }

    private Event mockActiveEvent() {
        Event event = mock(Event.class);

        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getCompanyId()).thenReturn(COMPANY_ID);
        when(event.isActive()).thenReturn(true);

        return event;
    }

    private Event mockMatchingEvent(EventSearchFilter filter) {
        Event event = mock(Event.class);

        when(event.matches(filter)).thenReturn(true);

        return event;
    }

    private void assertControlledFailure(Response<?> response) {
        assertNotNull(response);
        assertTrue(response.getValue() == null || Boolean.FALSE.equals(response.getValue()));
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
    }
}