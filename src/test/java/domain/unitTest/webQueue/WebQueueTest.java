package domain.unitTest.webQueue;

import DTO.QueueEntryResultDTO;
import application.AdmissionCallback;
import domain.webQueue.WebQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
    void GivenCapacityAvailable_WhenUserEnters_ThenAdmitted() {
        WebQueue queue = WebQueue.getInstance(5);
        QueueEntryResultDTO result = queue.tryEnter(uuid -> {});

        assertTrue(result.isAdmitted());
        assertEquals(-1, result.getPosition());
        assertEquals(1, queue.getActiveCount());
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void GivenQueueFull_WhenUserEnters_ThenPlacedInQueue() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fills the slot

        QueueEntryResultDTO result = queue.tryEnter(uuid -> {});

        assertFalse(result.isAdmitted());
        assertEquals(1, result.getPosition());
        assertEquals(1, queue.getActiveCount());
        assertEquals(1, queue.getWaitingCount());
    }

    @Test
    void GivenQueueFull_WhenMultipleUsersEnter_ThenPositionsAssignedInOrder() {
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
    void GivenNoOneWaiting_WhenUserLeaves_ThenActiveCountDecrements() {
        WebQueue queue = WebQueue.getInstance(3);
        queue.tryEnter(uuid -> {});
        queue.tryEnter(uuid -> {});

        queue.notifyUserLeft();

        assertEquals(1, queue.getActiveCount());
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void GivenSomeoneWaiting_WhenUserLeaves_ThenWaitingUserAdmitted() {
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
    void GivenUserWaiting_WhenUserLeaves_ThenCallbackFiredWithCorrectToken() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fills the slot

        List<String> admittedUuids = new ArrayList<>();
        QueueEntryResultDTO waiting = queue.tryEnter(admittedUuids::add);

        queue.notifyUserLeft();

        assertEquals(1, admittedUuids.size());
        assertEquals(waiting.getToken(), admittedUuids.get(0));
    }

    @Test
    void GivenMultipleUsersWaiting_WhenUsersLeave_ThenAdmittedInFifoOrder() {
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
    void GivenAdmittedUser_WhenGetStatus_ThenReturnsAdmitted() {
        WebQueue queue = WebQueue.getInstance(5);
        QueueEntryResultDTO result = queue.tryEnter(uuid -> {});

        QueueEntryResultDTO status = queue.getStatus(result.getToken());

        assertTrue(status.isAdmitted());
    }

    @Test
    void GivenWaitingUser_WhenGetStatus_ThenReturnsCorrectPosition() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fills the slot
        QueueEntryResultDTO waiting = queue.tryEnter(uuid -> {});

        QueueEntryResultDTO status = queue.getStatus(waiting.getToken());

        assertFalse(status.isAdmitted());
        assertEquals(1, status.getPosition());
    }

    @Test
    void GivenWaitingUser_WhenOthersAreAdmitted_ThenPositionDecreases() {
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
    void GivenValidValue_WhenSetMaxCapacity_ThenCapacityUpdated() {
        WebQueue queue = WebQueue.getInstance(5);
        queue.setMaxCapacity(10);
        assertEquals(10, queue.getMaxCapacity());
    }

    @Test
    void GivenZeroValue_WhenSetMaxCapacity_ThenThrowsIllegalArgument() {
        WebQueue queue = WebQueue.getInstance(5);
        assertThrows(IllegalArgumentException.class, () -> queue.setMaxCapacity(0));
    }

    @Test
    void GivenNegativeValue_WhenSetMaxCapacity_ThenThrowsIllegalArgument() {
        WebQueue queue = WebQueue.getInstance(5);
        assertThrows(IllegalArgumentException.class, () -> queue.setMaxCapacity(-3));
    }

    // --- getInstance singleton ---

    @Test
    void GivenNotInitialized_WhenGetInstance_ThenThrowsIllegalState() {
        assertThrows(IllegalStateException.class, WebQueue::getInstance);
    }

    @Test
    void GivenAlreadyInitialized_WhenGetInstanceCalledAgainWithDifferentCapacity_ThenReturnsSameInstance() {
        WebQueue first = WebQueue.getInstance(5);
        WebQueue second = WebQueue.getInstance(99); // ignored — already initialized
        assertSame(first, second);
        assertEquals(5, second.getMaxCapacity()); // capacity from first call
    }

    // --- concurrency ---

    @Test
    void GivenMaxCapacitySlots_WhenUsersEnterConcurrently_ThenExactlyMaxCapacityAdmitted() throws InterruptedException {
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
    void GivenConcurrentEntriesAndExits_WhenMultipleThreadsEnterAndLeave_ThenCountsRemainConsistent() throws InterruptedException {
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

    // --- removeFromQueue: basic correctness ---

    @Test
    void GivenUserWaiting_WhenRemoveFromQueue_ThenReturnsTrueAndCountsUpdated() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fill the active slot
        QueueEntryResultDTO waiting = queue.tryEnter(uuid -> {});

        boolean removed = queue.removeFromQueue(waiting.getToken());

        assertTrue(removed);
        assertEquals(1, queue.getActiveCount());
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void GivenAdmittedUser_WhenRemoveFromQueue_ThenReturnsFalse() {
        WebQueue queue = WebQueue.getInstance(5);
        QueueEntryResultDTO admitted = queue.tryEnter(uuid -> {});

        assertFalse(queue.removeFromQueue(admitted.getToken()));
        assertEquals(1, queue.getActiveCount()); // unchanged
    }

    @Test
    void GivenUnknownToken_WhenRemoveFromQueue_ThenReturnsFalse() {
        WebQueue queue = WebQueue.getInstance(5);

        assertFalse(queue.removeFromQueue("non-existent-token"));
        assertEquals(0, queue.getActiveCount());
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void GivenRemovedWaitingUser_WhenActiveUserLeaves_ThenCallbackNeverFired() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fill slot
        AtomicInteger callbacks = new AtomicInteger(0);
        QueueEntryResultDTO waiting = queue.tryEnter(uuid -> callbacks.incrementAndGet());

        queue.removeFromQueue(waiting.getToken());
        queue.notifyUserLeft(); // active user leaves — no one left to admit

        assertEquals(0, callbacks.get());
        assertEquals(0, queue.getActiveCount());
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void GivenFirstWaitingUserRemoved_WhenActiveUserLeaves_ThenSecondUserIsAdmitted() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fill slot
        AtomicInteger firstCallbacks  = new AtomicInteger(0);
        AtomicInteger secondCallbacks = new AtomicInteger(0);
        QueueEntryResultDTO first  = queue.tryEnter(uuid -> firstCallbacks.incrementAndGet());
        QueueEntryResultDTO second = queue.tryEnter(uuid -> secondCallbacks.incrementAndGet());

        queue.removeFromQueue(first.getToken());
        queue.notifyUserLeft(); // must skip removed first and admit second

        assertEquals(0, firstCallbacks.get());
        assertEquals(1, secondCallbacks.get());
        assertEquals(1, queue.getActiveCount());
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void GivenMiddleUserRemovedFromQueue_WhenSlotsOpen_ThenFifoOrderPreserved() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fill slot
        List<String> admissionOrder = Collections.synchronizedList(new ArrayList<>());
        QueueEntryResultDTO first  = queue.tryEnter(admissionOrder::add);
        QueueEntryResultDTO middle = queue.tryEnter(uuid -> {});
        QueueEntryResultDTO last   = queue.tryEnter(admissionOrder::add);

        queue.removeFromQueue(middle.getToken());
        queue.notifyUserLeft(); // admits first
        queue.notifyUserLeft(); // skips removed middle, admits last

        assertEquals(List.of(first.getToken(), last.getToken()), admissionOrder);
        assertEquals(1, queue.getActiveCount());
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void GivenAllWaitingUsersRemoved_WhenActiveUserLeaves_ThenActiveCountDecrements() {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fill slot
        QueueEntryResultDTO w1 = queue.tryEnter(uuid -> {});
        QueueEntryResultDTO w2 = queue.tryEnter(uuid -> {});
        queue.removeFromQueue(w1.getToken());
        queue.removeFromQueue(w2.getToken());

        queue.notifyUserLeft(); // no one left to admit — must decrement active

        assertEquals(0, queue.getActiveCount());
        assertEquals(0, queue.getWaitingCount());
    }

    // --- removeFromQueue: concurrency ---

    @Test
    void GivenSameToken_WhenTwoThreadsRemoveConcurrently_ThenExactlyOneSucceeds() throws InterruptedException {
        WebQueue queue = WebQueue.getInstance(1);
        queue.tryEnter(uuid -> {}); // fill slot
        QueueEntryResultDTO waiting = queue.tryEnter(uuid -> {});

        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                if (queue.removeFromQueue(waiting.getToken())) successCount.incrementAndGet();
                done.countDown();
            });
        }

        ready.await();
        start.countDown(); // release both threads at the same instant
        done.await();
        executor.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(0, queue.getWaitingCount());
    }

    @Test
    void GivenConcurrentRemoveAndNotify_WhenRaced_ThenConservationHoldsAndEachCallbackFiredAtMostOnce() throws InterruptedException {
        // n active users, n waiting users, n notifyUserLeft threads, n removeFromQueue threads.
        // notifyUserLeft is called exactly once per admitted user — the only valid scenario.
        int n = 20;
        WebQueue queue = WebQueue.getInstance(n);
        for (int i = 0; i < n; i++) queue.tryEnter(uuid -> {}); // fill all active slots

        ConcurrentHashMap<String, AtomicInteger> callbackCounts = new ConcurrentHashMap<>();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            QueueEntryResultDTO r = queue.tryEnter(uuid -> callbackCounts.get(uuid).incrementAndGet());
            tokens.add(r.getToken());
            callbackCounts.put(r.getToken(), new AtomicInteger(0));
        }

        AtomicInteger removedCount = new AtomicInteger(0);
        int threads = n * 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                if (idx < n) {
                    // one removeFromQueue per waiting user
                    if (queue.removeFromQueue(tokens.get(idx))) removedCount.incrementAndGet();
                } else {
                    // one notifyUserLeft per admitted active user — perfectly balanced
                    queue.notifyUserLeft();
                }
                done.countDown();
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        int admittedCount    = callbackCounts.values().stream().mapToInt(AtomicInteger::get).sum();
        int remainingWaiting = queue.getWaitingCount();

        // every waiting user must be accounted for: admitted XOR removed XOR still waiting
        assertEquals(n, admittedCount + removedCount.get() + remainingWaiting,
            "Conservation failed: users were lost or double-counted");

        // no user was both admitted and removed — callback fires at most once
        for (String token : tokens) {
            assertTrue(callbackCounts.get(token).get() <= 1,
                "Callback fired more than once for token: " + token);
        }

        assertTrue(queue.getActiveCount() >= 0,  "activeCount went negative");
        assertTrue(queue.getWaitingCount() >= 0,  "waitingCount went negative");
        assertTrue(queue.getActiveCount() <= n,   "activeCount exceeded maxCapacity");
    }

    @Test
    void GivenHighConcurrency_WhenEnterRemoveAndNotifyMixed_ThenInvariantsAlwaysHold() throws InterruptedException {
        // 10 active users, 30 waiting users.
        // notifyUserLeft is called exactly 10 times — once per admitted active user.
        // removeFromQueue is called once per waiting user (30 threads).
        // tryEnter is called by 30 more threads (new arrivals competing for freed slots).
        int capacity   = 10;
        int preWaiting = 30;
        WebQueue queue = WebQueue.getInstance(capacity);
        for (int i = 0; i < capacity; i++) queue.tryEnter(uuid -> {});

        ConcurrentHashMap<String, AtomicInteger> callbackCounts = new ConcurrentHashMap<>();
        List<String> waitingTokens = new ArrayList<>();
        for (int i = 0; i < preWaiting; i++) {
            QueueEntryResultDTO w = queue.tryEnter(uuid -> callbackCounts.get(uuid).incrementAndGet());
            waitingTokens.add(w.getToken());
            callbackCounts.put(w.getToken(), new AtomicInteger(0));
        }

        AtomicInteger removedCount = new AtomicInteger(0);
        int threads = capacity + preWaiting + preWaiting; // 10 + 30 + 30
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        // notifyUserLeft threads — one per active user
        for (int i = 0; i < capacity; i++) {
            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                queue.notifyUserLeft();
                done.countDown();
            });
        }
        // removeFromQueue threads — one per pre-populated waiting user
        for (int i = 0; i < preWaiting; i++) {
            final int idx = i;
            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                if (queue.removeFromQueue(waitingTokens.get(idx))) removedCount.incrementAndGet();
                done.countDown();
            });
        }
        // tryEnter threads — new arrivals
        for (int i = 0; i < preWaiting; i++) {
            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                queue.tryEnter(uuid -> {});
                done.countDown();
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        int admittedFromPreWaiting = callbackCounts.values().stream().mapToInt(AtomicInteger::get).sum();

        // admitted + removed must never exceed the original waiting population
        assertTrue(admittedFromPreWaiting + removedCount.get() <= preWaiting,
            "More pre-waiting users admitted/removed than existed");

        // no pre-waiting user was both admitted and removed
        for (String token : waitingTokens) {
            assertTrue(callbackCounts.get(token).get() <= 1,
                "Callback fired more than once for token: " + token);
        }

        assertTrue(queue.getActiveCount() >= 0,         "activeCount went negative");
        assertTrue(queue.getWaitingCount() >= 0,         "waitingCount went negative");
        assertTrue(queue.getActiveCount() <= capacity,   "activeCount exceeded maxCapacity");
    }
}
