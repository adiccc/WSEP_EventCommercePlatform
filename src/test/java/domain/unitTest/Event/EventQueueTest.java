package domain.unitTest.Event;

import org.junit.jupiter.api.Test;
import domain.event.EventQueue;

import static org.junit.jupiter.api.Assertions.*;

class EventQueueTest {

    @Test
    void GivenEmptyQueue_WhenEnqueueUser_ThenUserIsAdded() {
        EventQueue queue = new EventQueue();

        queue.enqueue("token-1");

        assertFalse(queue.isEmpty());
        assertEquals(1, queue.size());
        assertTrue(queue.contains("token-1"));
    }

    @Test
    void GivenQueueWithUsers_WhenEnqueueAnotherUser_ThenUserAddedToEnd() {
        EventQueue queue = new EventQueue();

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
        EventQueue queue = new EventQueue();

        queue.enqueue("token-1");
        queue.enqueue("token-1");

        assertEquals(1, queue.size());
        assertTrue(queue.contains("token-1"));
    }

    @Test
    void GivenQueueWithUsers_WhenDequeue_ThenFirstUserRemoved() {
        EventQueue queue = new EventQueue();

        queue.enqueue("token-1");
        queue.enqueue("token-2");

        queue.dequeue();

        assertEquals(1, queue.size());
        assertFalse(queue.contains("token-1"));
        assertTrue(queue.contains("token-2"));
        assertTrue(queue.isFirst("token-2"));
    }

    @Test
    void GivenSingleUserQueue_WhenDequeue_ThenQueueBecomesEmpty() {
        EventQueue queue = new EventQueue();

        queue.enqueue("token-1");

        queue.dequeue();

        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertFalse(queue.contains("token-1"));
    }

    @Test
    void GivenEmptyQueue_WhenDequeue_ThenNothingHappens() {
        EventQueue queue = new EventQueue();

        queue.dequeue();

        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }
}