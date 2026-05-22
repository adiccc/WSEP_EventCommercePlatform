package domain.unitTest.webQueue;

import DTO.QueueEntryResultDTO;
import application.AdmissionCallback;
import domain.webQueue.WebQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WebQueueTest {

    @BeforeEach
    void setUp() {
        WebQueue.resetForTesting();
    }

    // --- tryEnter: basic admission ---

    @Test
    void userEntersWhenCapacityAvailable_isAdmitted() {
        WebQueue queue = WebQueue.getInstance(5);
        QueueEntryResultDTO result = queue.tryEnter(uuid -> {});

        assertTrue(result.isAdmitted());
        assertEquals(-1, result.getPosition());
        assertEquals(1, queue.getActiveCount());
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void userEntersWhenFull_isPlacedInQueue() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fills the slot

        QueueEntryResultDTO result = queue.tryEnter(uuid -> {});

        assertFalse(result.isAdmitted());
        assertEquals(1, result.getPosition());
        assertEquals(1, queue.getActiveCount());
        assertEquals(1, queue.getWaitingCount());
    }

    @Test
    void multipleWaitingUsers_positionsAreCorrect() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fills the slot

        QueueEntryResultDTO second = queue.tryEnter(uuid -> {});
        QueueEntryResultDTO third = queue.tryEnter(uuid -> {});
        QueueEntryResultDTO fourth = queue.tryEnter(uuid -> {});

        assertEquals(1, second.getPosition());
        assertEquals(2, third.getPosition());
        assertEquals(3, fourth.getPosition());
        assertEquals(3, queue.getWaitingCount());
    }

    // --- notifyUserLeft ---

    @Test
    void userLeaves_noOneWaiting_activeCountDecrements() {
        WebQueue queue = WebQueue.getInstance(3);
        queue.tryEnter(uuid -> {});
        queue.tryEnter(uuid -> {});

        queue.notifyUserLeft();

        assertEquals(1, queue.getActiveCount());
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void userLeaves_someoneWaiting_waitingUserAdmitted() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fills the slot
        QueueEntryResultDTO waiting = queue.tryEnter(uuid -> {});

        queue.notifyUserLeft();

        assertEquals(1, queue.getActiveCount());
        assertEquals(0, queue.getWaitingCount());

        QueueEntryResultDTO status = queue.getStatus(waiting.getToken());
        assertTrue(status.isAdmitted());
    }

    @Test
    void userLeaves_callbackFiredWithCorrectUuid() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fills the slot

        List<String> admittedUuids = new ArrayList<>();
        QueueEntryResultDTO waiting = queue.tryEnter(admittedUuids::add);

        queue.notifyUserLeft();

        assertEquals(1, admittedUuids.size());
        assertEquals(waiting.getToken(), admittedUuids.get(0));
    }

    @Test
    void multipleLeave_waitingUsersAdmittedInFifoOrder() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fills the slot

        List<String> admissionOrder = new ArrayList<>();
        QueueEntryResultDTO first = queue.tryEnter(admissionOrder::add);
        QueueEntryResultDTO second = queue.tryEnter(admissionOrder::add);

        queue.notifyUserLeft(); // admits first
        queue.notifyUserLeft(); // admits second

        assertEquals(2, admissionOrder.size());
        assertEquals(first.getToken(), admissionOrder.get(0));
        assertEquals(second.getToken(), admissionOrder.get(1));
    }

    // --- getStatus ---

    @Test
    void getStatus_admittedUser_returnsAdmitted() {
        WebQueue queue = WebQueue.getInstance(5);
        QueueEntryResultDTO result = queue.tryEnter(uuid -> {});

        QueueEntryResultDTO status = queue.getStatus(result.getToken());

        assertTrue(status.isAdmitted());
    }

    @Test
    void getStatus_waitingUser_returnsCorrectPosition() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fills the slot
        QueueEntryResultDTO waiting = queue.tryEnter(uuid -> {});

        QueueEntryResultDTO status = queue.getStatus(waiting.getToken());

        assertFalse(status.isAdmitted());
        assertEquals(1, status.getPosition());
    }

    @Test
    void getStatus_positionDecreasesAsOthersAreAdmitted() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fills the slot
        queue.tryEnter(uuid -> {}); // position 1
        QueueEntryResultDTO target = queue.tryEnter(uuid -> {}); // position 2

        assertEquals(2, queue.getStatus(target.getToken()).getPosition());

        queue.notifyUserLeft(); // admits position 1, target moves to position 1

        assertEquals(1, queue.getStatus(target.getToken()).getPosition());
    }

    // --- setMaxCapacity ---

    @Test
    void setMaxCapacity_validValue_updatesCapacity() {
        WebQueue queue = WebQueue.getInstance(5);
        queue.setMaxCapacity(10);
        assertEquals(10, queue.getMaxCapacity());
    }

    @Test
    void setMaxCapacity_zero_throwsIllegalArgumentException() {
        WebQueue queue = WebQueue.getInstance(5);
        assertThrows(IllegalArgumentException.class, () -> queue.setMaxCapacity(0));
    }

    @Test
    void setMaxCapacity_negative_throwsIllegalArgumentException() {
        WebQueue queue = WebQueue.getInstance(5);
        assertThrows(IllegalArgumentException.class, () -> queue.setMaxCapacity(-3));
    }

    // --- getInstance singleton ---

    @Test
    void getInstance_beforeInit_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, WebQueue::getInstance);
    }

    @Test
    void getInstance_calledTwiceWithDifferentCapacity_returnsSameInstance() {
        WebQueue first = WebQueue.getInstance(5);
        WebQueue second = WebQueue.getInstance(99); // ignored — already initialized
        assertSame(first, second);
        assertEquals(5, second.getMaxCapacity()); // capacity from first call
    }

    // --- concurrency ---

    @Test
    void concurrentEntries_exactlyMaxCapacityAdmitted() throws InterruptedException {
        int capacity = 10;
        int totalUsers = 50;
        WebQueue queue = WebQueue.getInstance(capacity);

        AtomicInteger admitted = new AtomicInteger(0);
        AtomicInteger waiting = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalUsers);
        ExecutorService executor = Executors.newFixedThreadPool(totalUsers);

        for (int i = 0; i < totalUsers; i++) {
            executor.submit(() -> {
                QueueEntryResultDTO result = queue.tryEnter(uuid -> {});
                if (result.isAdmitted()) admitted.incrementAndGet();
                else waiting.incrementAndGet();
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(capacity, admitted.get());
        assertEquals(totalUsers - capacity, waiting.get());
        assertEquals(capacity, queue.getActiveCount());
        assertEquals(totalUsers - capacity, queue.getWaitingCount());
    }

    @Test
    void concurrentEntriesAndExits_countsAlwaysConsistent() throws InterruptedException {
        int capacity = 5;
        WebQueue queue = WebQueue.getInstance(capacity);

        // fill the queue
        for (int i = 0; i < capacity; i++) queue.tryEnter(uuid -> {});

        int exitThreads = 20;
        CountDownLatch latch = new CountDownLatch(exitThreads);
        ExecutorService executor = Executors.newFixedThreadPool(exitThreads);

        for (int i = 0; i < exitThreads; i++) {
            executor.submit(() -> {
                queue.tryEnter(uuid -> {});
                queue.notifyUserLeft();
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        // active + waiting should never exceed capacity + exitThreads
        assertTrue(queue.getActiveCount() <= capacity);
        assertTrue(queue.getWaitingCount() >= 0);
    }
}
