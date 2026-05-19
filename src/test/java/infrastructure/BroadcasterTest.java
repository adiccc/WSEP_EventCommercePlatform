package infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import DTO.NotifyDTO;
import com.vaadin.flow.shared.Registration;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BroadcasterTest {

    private static final long AWAIT_TIMEOUT_MS = 2000L;
    private static final long NEGATIVE_WAIT_MS = 200L;

    private String uniqueUserId() {
        return "GUEST_" + UUID.randomUUID();
    }

    private String uniqueTabId() {
        return "tab-" + UUID.randomUUID();
    }

    @Test
    void givenRegisteredUser_whenBroadcastToUser_thenListenerReceivesNotification() throws InterruptedException {
        String userIdentifier = uniqueUserId();
        NotifyDTO notification = mock(NotifyDTO.class);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> received = new AtomicReference<>();

        Registration registration = Broadcaster.registerUser(userIdentifier, dto -> {
            received.set(dto);
            latch.countDown();
        });

        try {
            assertNotNull(registration);
            boolean dispatched = Broadcaster.broadcastToUser(userIdentifier, notification);

            assertTrue(dispatched);
            assertTrue(
                    latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "Listener never received the broadcast within the timeout");
            assertSame(notification, received.get());
        } finally {
            registration.remove();
        }
    }

    @Test
    void givenMultipleListenersRegisteredForSameUser_whenBroadcastToUser_thenAllListenersReceiveNotification()
            throws InterruptedException {
        String userIdentifier = uniqueUserId();
        NotifyDTO notification = mock(NotifyDTO.class);
        CountDownLatch latch = new CountDownLatch(2);

        Registration first = Broadcaster.registerUser(userIdentifier, dto -> latch.countDown());
        Registration second = Broadcaster.registerUser(userIdentifier, dto -> latch.countDown());

        try {
            boolean dispatched = Broadcaster.broadcastToUser(userIdentifier, notification);

            assertTrue(dispatched);
            assertTrue(
                    latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "Not all listeners received the broadcast");
        } finally {
            first.remove();
            second.remove();
        }
    }

    @Test
    void givenUnregisteredUserListener_whenBroadcastToUser_thenListenerIsNotInvokedAndReturnsFalse()
            throws InterruptedException {
        String userIdentifier = uniqueUserId();
        CountDownLatch shouldNotFire = new CountDownLatch(1);
        Consumer<NotifyDTO> listener = dto -> shouldNotFire.countDown();

        Registration registration = Broadcaster.registerUser(userIdentifier, listener);
        registration.remove(); //simulates log out

        boolean dispatched = Broadcaster.broadcastToUser(userIdentifier, mock(NotifyDTO.class));

        assertFalse(dispatched, "Broadcast should report no recipients once the listener is unregistered");
        assertFalse(
                shouldNotFire.await(NEGATIVE_WAIT_MS, TimeUnit.MILLISECONDS),
                "Unregistered listener must not be invoked");
    }

    @Test
    void givenRegisteredUser_whenBroadcastToUser_thenListenerIsInvokedOnExecutorThreadNotCaller()
            throws InterruptedException {
        String userIdentifier = uniqueUserId();
        NotifyDTO notification = mock(NotifyDTO.class);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Thread> deliveringThread = new AtomicReference<>();

        Registration registration = Broadcaster.registerUser(userIdentifier, dto -> {
            deliveringThread.set(Thread.currentThread());
            latch.countDown();
        });

        try {
            Thread callerThread = Thread.currentThread();
            boolean dispatched = Broadcaster.broadcastToUser(userIdentifier, notification);

            assertTrue(dispatched);
            assertTrue(
                    latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "Listener never received the broadcast within the timeout");
            assertNotNull(deliveringThread.get());
            assertFalse(
                    callerThread.equals(deliveringThread.get()),
                    "Listener should be invoked on the Broadcaster's executor, not the caller thread");
        } finally {
            registration.remove();
        }
    }

    @Test
    void givenUnknownUser_whenBroadcastToUser_thenReturnsFalse() {
        boolean dispatched = Broadcaster.broadcastToUser(uniqueUserId(), mock(NotifyDTO.class));

        assertFalse(dispatched);
    }

    @Test
    void givenRegisteredTab_whenBroadcastToTab_thenListenerReceivesNotification() throws InterruptedException {
        String tabId = uniqueTabId();
        NotifyDTO notification = mock(NotifyDTO.class);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> received = new AtomicReference<>();

        Registration registration = Broadcaster.registerTab(tabId, dto -> {
            received.set(dto);
            latch.countDown();
        });

        try {
            boolean dispatched = Broadcaster.broadcastToTab(tabId, notification);

            assertTrue(dispatched);
            assertTrue(
                    latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "Tab listener never received the broadcast within the timeout");
            assertSame(notification, received.get());
        } finally {
            registration.remove();
        }
    }

    @Test
    void givenUnregisteredTabListener_whenBroadcastToTab_thenListenerIsNotInvokedAndReturnsFalse()
            throws InterruptedException {
        String tabId = uniqueTabId();
        CountDownLatch shouldNotFire = new CountDownLatch(1);

        Registration registration = Broadcaster.registerTab(tabId, dto -> shouldNotFire.countDown());
        registration.remove();

        boolean dispatched = Broadcaster.broadcastToTab(tabId, mock(NotifyDTO.class));

        assertFalse(dispatched);
        assertFalse(
                shouldNotFire.await(NEGATIVE_WAIT_MS, TimeUnit.MILLISECONDS),
                "Unregistered tab listener must not be invoked");
    }
}
