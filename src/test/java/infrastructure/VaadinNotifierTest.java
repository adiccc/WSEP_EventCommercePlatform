package infrastructure;

import DTO.NotifyDTO;
import domain.user.IUserRepo;
import domain.user.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

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
        // Arrange
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