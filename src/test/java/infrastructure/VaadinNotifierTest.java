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
        vaadinNotifier = new VaadinNotifier();
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


}