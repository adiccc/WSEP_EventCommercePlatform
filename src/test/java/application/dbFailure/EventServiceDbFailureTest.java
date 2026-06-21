package application.dbFailure;

import application.EventService;
import application.IAuth;
import application.INotifier;
import application.Response;
import domain.dataType.EventSearchFilter;
import domain.dto.EventDTO;
import domain.dto.EventDetailsDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    // ============================================================================
    // Coverage note
    // ============================================================================
    //
    // This file injects DB failures through EventService use cases.
    // Domain entities are used only as mocked setup objects needed to drive the
    // service flow to the relevant repository call.
    //
    // Covered IEventRepo calls:
    // - findById: ViewEventDetails
    // - getAll: searchEvents
    // - findByCompany: searchCompanyEvents
    //
    // Recovery coverage:
    // - eventRepo.findById fails once and is retried during ViewEventDetails.
    // - eventRepo.getAll fails once and is retried during searchEvents.
    // - eventRepo.findByCompany fails once and is retried during searchCompanyEvents.

    // ============================================================================
    // Event repo failure tests
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