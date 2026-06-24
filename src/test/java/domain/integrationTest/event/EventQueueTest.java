package domain.integrationTest.event;

import domain.eventQueue.EventQueue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventQueueTest {

    @Test
    void GivenEmptyQueue_WhenEnqueueUser_ThenUserIsAdded() {
        EventQueue queue = new EventQueue(1);

        queue.enqueue("token-1");

        assertFalse(queue.isEmpty());
        assertEquals(1, queue.size());
        assertTrue(queue.contains("token-1"));
    }

    @Test
    void GivenQueueWithUsers_WhenEnqueueAnotherUser_ThenUserAddedToEnd() {
        EventQueue queue = new EventQueue(1);

        queue.enqueue("token-1");
        queue.enqueue("token-2");

        assertEquals(2, queue.size());
        assertTrue(queue.contains("token-1"));
        assertTrue(queue.contains("token-2"));
        assertTrue(queue.isFirst("token-1"));
        assertFalse(queue.isFirst("token-2"));
    }

    @Test
    void GivenSameToken_WhenEnqueueTwice_ThenTokenAddedOnlyOnce() {
        EventQueue queue = new EventQueue(1);

        queue.enqueue("token-1");
        queue.enqueue("token-1");

        assertEquals(1, queue.size());
        assertTrue(queue.contains("token-1"));
    }

    @Test
    void GivenQueueWithUsers_WhenDequeue_ThenFirstUserRemoved() {
        EventQueue queue = new EventQueue(1);

        queue.enqueue("token-1");
        queue.enqueue("token-2");

        String removed = queue.dequeue();

        assertEquals("token-1", removed);
        assertEquals(1, queue.size());
        assertFalse(queue.contains("token-1"));
        assertTrue(queue.contains("token-2"));
        assertTrue(queue.isFirst("token-2"));
    }

    @Test
    void GivenSingleUserQueue_WhenDequeue_ThenQueueBecomesEmpty() {
        EventQueue queue = new EventQueue(1);

        queue.enqueue("token-1");

        String removed = queue.dequeue();

        assertEquals("token-1", removed);
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertFalse(queue.contains("token-1"));
    }

    @Test
    void GivenEmptyQueue_WhenDequeue_ThenReturnsNullAndQueueStaysEmpty() {
        EventQueue queue = new EventQueue(1);

        String removed = queue.dequeue();

        assertNull(removed);
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void GivenQueueWithUsers_WhenCheckPosition_ThenCorrectPositionReturned() {
        EventQueue queue = new EventQueue(1);

        queue.enqueue("token-1");
        queue.enqueue("token-2");
        queue.enqueue("token-3");

        assertEquals(1, queue.position("token-1"));
        assertEquals(2, queue.position("token-2"));
        assertEquals(3, queue.position("token-3"));
    }

    @Test
    void GivenQueueWithoutToken_WhenCheckPosition_ThenMinusOneReturned() {
        EventQueue queue = new EventQueue(1);

        queue.enqueue("token-1");

        assertEquals(-1, queue.position("token-2"));
    }

    @Test
    void GivenQueueWithUser_WhenRemoveUser_ThenUserRemoved() {
        EventQueue queue = new EventQueue(1);

        queue.enqueue("token-1");
        queue.enqueue("token-2");

        boolean removed = queue.remove("token-1");

        assertTrue(removed);
        assertFalse(queue.contains("token-1"));
        assertEquals(1, queue.size());
        assertTrue(queue.contains("token-2"));
    }

    @Test
    void GivenQueueWithoutUser_WhenRemoveUser_ThenReturnsFalse() {
        EventQueue queue = new EventQueue(1);

        queue.enqueue("token-1");

        boolean removed = queue.remove("token-2");

        assertFalse(removed);
        assertEquals(1, queue.size());
        assertTrue(queue.contains("token-1"));
    }

    @Test
    void GivenQueue_WhenCopyConstructorUsed_ThenCopyHasSameState() {
        EventQueue original = new EventQueue(1);
        original.enqueue("token-1");
        original.enqueue("token-2");
        original.setVersion(3);

        EventQueue copy = new EventQueue(original);

        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getVersion(), copy.getVersion());
        assertEquals(original.size(), copy.size());
        assertTrue(copy.contains("token-1"));
        assertTrue(copy.contains("token-2"));
    }
}