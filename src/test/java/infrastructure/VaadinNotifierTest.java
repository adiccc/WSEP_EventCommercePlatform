package infrastructure;

import DTO.NotifyDTO;
import domain.user.IUserRepo;
import domain.user.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class VaadinNotifierTest {

    private VaadinNotifier vaadinNotifier;
    private IUserRepo userRepoMock;

    @BeforeEach
    void setUp() {
        userRepoMock = Mockito.mock(IUserRepo.class);
        vaadinNotifier = new VaadinNotifier(userRepoMock);
    }

    @Test
    void givenOnlineUser_whenNotifyUser_thenDoNotSaveToDB() {
        // Arrange
        String email = "online@example.com";
        NotifyDTO mockNotification = Mockito.mock(NotifyDTO.class);

        try (MockedStatic<Broadcaster> broadcasterMock = mockStatic(Broadcaster.class)) {
            broadcasterMock.when(() -> Broadcaster.broadcastToUser(email, mockNotification)).thenReturn(true);
            // Act
            vaadinNotifier.notifyUser(email, mockNotification);
            // Assert
            verify(userRepoMock, never()).findUserByEmail(anyString());
            verify(userRepoMock, never()).store(any(Member.class));
        }
    }
    @Test
    void givenOfflineUserAndMemberExists_whenNotifyUser_thenSaveDelayedNotificationToDB() {
        // Arrange
        String email = "offline@example.com";
        NotifyDTO mockNotification = Mockito.mock(NotifyDTO.class);
        Member mockMember = Mockito.mock(Member.class);

        when(userRepoMock.findUserByEmail(email)).thenReturn(mockMember);

        try (MockedStatic<Broadcaster> broadcasterMock = mockStatic(Broadcaster.class)) {
            broadcasterMock.when(() -> Broadcaster.broadcastToUser(email, mockNotification)).thenReturn(false);

            // Act
            vaadinNotifier.notifyUser(email, mockNotification);

            // Assert
            verify(userRepoMock, times(1)).findUserByEmail(email);
            verify(mockMember, times(1)).addDelayedNotification(mockNotification);
            verify(userRepoMock, times(1)).store(mockMember);
        }
    }

    @Test
    void givenOfflineUserAndMemberNotFound_whenNotifyUser_thenDoNotSaveToDB() {
        // Agrrange
        String email = "ghost@example.com";
        NotifyDTO mockNotification = Mockito.mock(NotifyDTO.class);

        when(userRepoMock.findUserByEmail(email)).thenReturn(null);

        try (MockedStatic<Broadcaster> broadcasterMock = mockStatic(Broadcaster.class)) {
            broadcasterMock.when(() -> Broadcaster.broadcastToUser(email, mockNotification)).thenReturn(false);

            // Act
            vaadinNotifier.notifyUser(email, mockNotification);

            // Assert
            verify(userRepoMock, times(1)).findUserByEmail(email);
            verify(userRepoMock, never()).store(any(Member.class));
        }
    }

    @Test
    void givenTabId_whenNotifyTab_thenReturnBroadcasterResult() {
        // Arrange
        String tabId = "tab-123";
        NotifyDTO mockNotification = Mockito.mock(NotifyDTO.class);

        try (MockedStatic<Broadcaster> broadcasterMock = mockStatic(Broadcaster.class)) {
            broadcasterMock.when(() -> Broadcaster.broadcastToTab(tabId, mockNotification)).thenReturn(true);

            // Act
            boolean result = vaadinNotifier.notifyTab(tabId, mockNotification);

            // Assert
            assertTrue(result);
            broadcasterMock.verify(() -> Broadcaster.broadcastToTab(tabId, mockNotification), times(1));
        }
    }

    @Test
    void givenMemberWithDelayedNotifications_whenDeliver_thenBroadcastAndClearDB() {
        // Arrange
        String email = "returning@example.com";
        Member mockMember = Mockito.mock(Member.class);

        List<NotifyDTO> delayedList = new ArrayList<>();
        NotifyDTO notification1 = Mockito.mock(NotifyDTO.class);
        NotifyDTO notification2 = Mockito.mock(NotifyDTO.class);
        delayedList.add(notification1);
        delayedList.add(notification2);

        when(userRepoMock.findUserByEmail(email)).thenReturn(mockMember);
        when(mockMember.getDelayedNotifications()).thenReturn(delayedList);

        try (MockedStatic<Broadcaster> broadcasterMock = mockStatic(Broadcaster.class)) {
            // Act
            boolean result = vaadinNotifier.deliverDelayedNotifications(email);

            // Assert
            assertTrue(result, "Notifier should return true upon successful delivery");

            broadcasterMock.verify(() -> Broadcaster.broadcastToUser(email, notification1), times(1));
            broadcasterMock.verify(() -> Broadcaster.broadcastToUser(email, notification2), times(1));
            verify(mockMember, times(1)).clearDelayedNotifications();

        }
    }

    @Test
    void givenMemberWithNoDelayedNotifications_whenDeliver_thenDoNothing() {
        // Arrange
        String email = "empty@example.com";
        Member mockMember = Mockito.mock(Member.class);

        when(userRepoMock.findUserByEmail(email)).thenReturn(mockMember);
        when(mockMember.getDelayedNotifications()).thenReturn(new ArrayList<>());

        try (MockedStatic<Broadcaster> broadcasterMock = mockStatic(Broadcaster.class)) {
            // Act
            vaadinNotifier.deliverDelayedNotifications(email);

            // Assert
            broadcasterMock.verify(() -> Broadcaster.broadcastToUser(anyString(), any()), never());
            verify(mockMember, never()).clearDelayedNotifications();
            verify(userRepoMock, never()).store(any(Member.class));
        }
    }
    @Test
    void givenOfflineUser_whenRepoThrowsException_thenCatchAndDoNotThrow() {
        // Arrange
        String email = "db_crash@example.com";
        NotifyDTO mockNotification = Mockito.mock(NotifyDTO.class);
        Member mockMember = Mockito.mock(Member.class);

        when(userRepoMock.findUserByEmail(email)).thenReturn(mockMember);
        doThrow(new RuntimeException("Database Connection Lost")).when(userRepoMock).store(mockMember);

        try (MockedStatic<Broadcaster> broadcasterMock = mockStatic(Broadcaster.class)) {
            broadcasterMock.when(() -> Broadcaster.broadcastToUser(email, mockNotification)).thenReturn(false);

            // Act & Assert
            assertDoesNotThrow(() -> vaadinNotifier.notifyUser(email, mockNotification));
            verify(userRepoMock, times(1)).store(mockMember);
        }
    }
    @Test
    void givenBroadcasterException_whenNotifyUser_thenCatchAndSkipDB() {
        // Arrange
        String email = "websocket_error@example.com";
        NotifyDTO mockNotification = Mockito.mock(NotifyDTO.class);

        try (MockedStatic<Broadcaster> broadcasterMock = mockStatic(Broadcaster.class)) {
            broadcasterMock.when(() -> Broadcaster.broadcastToUser(email, mockNotification))
                    .thenThrow(new RuntimeException("WebSocket Thread Failure"));

            // Act & Assert
            assertDoesNotThrow(() -> vaadinNotifier.notifyUser(email, mockNotification));
            verify(userRepoMock, never()).findUserByEmail(anyString());
            verify(userRepoMock, never()).store(any(Member.class));
        }
    }
}