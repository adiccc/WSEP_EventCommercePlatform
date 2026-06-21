package domain.unitTest.Event;

import domain.event.EventQueueManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventQueueManagerTest {

    private EventQueueManager eventQueueManager;

    @BeforeEach
    void setUp() {
        eventQueueManager = new EventQueueManager();
    }

    @Test
    void GivenEmptyQueue_WhenCheckingIsEmpty_ThenReturnsTrue() {
        assertTrue(eventQueueManager.isEmpty(1));
    }

    @Test
    void GivenTokenEnqueued_WhenCheckingContains_ThenReturnsTrue() {
        int eventId = 1;
        String token = "token-1";

        eventQueueManager.enqueue(eventId, token);

        assertTrue(eventQueueManager.contains(eventId, token));
        assertFalse(eventQueueManager.isEmpty(eventId));
    }

    @Test
    void GivenMultipleTokens_WhenCheckingPosition_ThenReturnsCorrectPositions() {
        int eventId = 1;

        eventQueueManager.enqueue(eventId, "token-1");
        eventQueueManager.enqueue(eventId, "token-2");
        eventQueueManager.enqueue(eventId, "token-3");

        assertEquals(1, eventQueueManager.position(eventId, "token-1"));
        assertEquals(2, eventQueueManager.position(eventId, "token-2"));
        assertEquals(3, eventQueueManager.position(eventId, "token-3"));
    }

    @Test
    void GivenSameTokenEnqueuedTwice_WhenCheckingPosition_ThenTokenAppearsOnlyOnce() {
        int eventId = 1;
        String token = "token-1";

        eventQueueManager.enqueue(eventId, token);
        eventQueueManager.enqueue(eventId, token);

        assertEquals(1, eventQueueManager.position(eventId, token));

        String dequeued = eventQueueManager.dequeue(eventId);
        assertEquals(token, dequeued);

        assertFalse(eventQueueManager.contains(eventId, token));
        assertTrue(eventQueueManager.isEmpty(eventId));
    }

    @Test
    void GivenMultipleEvents_WhenEnqueueTokens_ThenQueuesAreSeparatedByEventId() {
        int eventId1 = 1;
        int eventId2 = 2;

        eventQueueManager.enqueue(eventId1, "token-event-1");
        eventQueueManager.enqueue(eventId2, "token-event-2");

        assertTrue(eventQueueManager.contains(eventId1, "token-event-1"));
        assertFalse(eventQueueManager.contains(eventId1, "token-event-2"));

        assertTrue(eventQueueManager.contains(eventId2, "token-event-2"));
        assertFalse(eventQueueManager.contains(eventId2, "token-event-1"));

        assertEquals(1, eventQueueManager.position(eventId1, "token-event-1"));
        assertEquals(1, eventQueueManager.position(eventId2, "token-event-2"));
    }

    @Test
    void GivenQueueWithTokens_WhenDequeue_ThenReturnsFirstTokenAndAdvancesQueue() {
        int eventId = 1;

        eventQueueManager.enqueue(eventId, "token-1");
        eventQueueManager.enqueue(eventId, "token-2");

        String dequeued = eventQueueManager.dequeue(eventId);

        assertEquals("token-1", dequeued);
        assertFalse(eventQueueManager.contains(eventId, "token-1"));
        assertTrue(eventQueueManager.contains(eventId, "token-2"));
        assertEquals(1, eventQueueManager.position(eventId, "token-2"));
    }

    @Test
    void GivenEmptyQueue_WhenDequeue_ThenReturnsNull() {
        assertNull(eventQueueManager.dequeue(1));
    }

    @Test
    void GivenTokenInQueue_WhenRemove_ThenTokenRemoved() {
        int eventId = 1;
        String token = "token-1";

        eventQueueManager.enqueue(eventId, token);

        boolean removed = eventQueueManager.remove(eventId, token);

        assertTrue(removed);
        assertFalse(eventQueueManager.contains(eventId, token));
        assertTrue(eventQueueManager.isEmpty(eventId));
    }

    @Test
    void GivenTokenNotInQueue_WhenRemove_ThenReturnsFalse() {
        boolean removed = eventQueueManager.remove(1, "missing-token");

        assertFalse(removed);
    }

    @Test
    void GivenQueueWithTokens_WhenCheckingIsFirst_ThenOnlyFirstTokenReturnsTrue() {
        int eventId = 1;

        eventQueueManager.enqueue(eventId, "token-1");
        eventQueueManager.enqueue(eventId, "token-2");

        assertTrue(eventQueueManager.isFirst(eventId, "token-1"));
        assertFalse(eventQueueManager.isFirst(eventId, "token-2"));
    }

    @Test
    void GivenMissingToken_WhenCheckingPosition_ThenReturnsMinusOne() {
        int eventId = 1;

        eventQueueManager.enqueue(eventId, "token-1");

        assertEquals(-1, eventQueueManager.position(eventId, "missing-token"));
    }
}