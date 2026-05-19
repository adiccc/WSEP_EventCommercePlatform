package infrastructure;

import DTO.NotifyDTO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class VaadinNotifierTest {

    @Test
    void givenUserIdAndNotification_whenNotifyUser_thenDelegatesToBroadcastToUserWithSameArguments() {
        String userIdentifier = "user@example.com";
        NotifyDTO notification = mock(NotifyDTO.class);
        VaadinNotifier notifier = new VaadinNotifier();

        try (MockedStatic<Broadcaster> mockedBroadcaster = Mockito.mockStatic(Broadcaster.class)) {
            mockedBroadcaster
                    .when(() -> Broadcaster.broadcastToUser(userIdentifier, notification))
                    .thenReturn(true);

            boolean result = notifier.notifyUser(userIdentifier, notification);

            assertTrue(result);
            mockedBroadcaster.verify(() -> Broadcaster.broadcastToUser(userIdentifier, notification));
            mockedBroadcaster.verifyNoMoreInteractions();
        }
    }

    @Test
    void givenBroadcasterReportsNoListeners_whenNotifyUser_thenReturnsFalse() {
        String userIdentifier = "ghost-user";
        NotifyDTO notification = mock(NotifyDTO.class);
        VaadinNotifier notifier = new VaadinNotifier();

        try (MockedStatic<Broadcaster> mockedBroadcaster = Mockito.mockStatic(Broadcaster.class)) {
            mockedBroadcaster
                    .when(() -> Broadcaster.broadcastToUser(userIdentifier, notification))
                    .thenReturn(false);

            boolean result = notifier.notifyUser(userIdentifier, notification);

            assertFalse(result);
            mockedBroadcaster.verify(() -> Broadcaster.broadcastToUser(userIdentifier, notification));
        }
    }

    @Test
    void givenTabIdAndNotification_whenNotifyTab_thenDelegatesToBroadcastToTabWithSameArguments() {
        String tabId = "tab-42";
        NotifyDTO notification = mock(NotifyDTO.class);
        VaadinNotifier notifier = new VaadinNotifier();

        try (MockedStatic<Broadcaster> mockedBroadcaster = Mockito.mockStatic(Broadcaster.class)) {
            mockedBroadcaster
                    .when(() -> Broadcaster.broadcastToTab(tabId, notification))
                    .thenReturn(true);

            boolean result = notifier.notifyTab(tabId, notification);

            assertTrue(result);
            mockedBroadcaster.verify(() -> Broadcaster.broadcastToTab(tabId, notification));
            mockedBroadcaster.verifyNoMoreInteractions();
        }
    }
}
