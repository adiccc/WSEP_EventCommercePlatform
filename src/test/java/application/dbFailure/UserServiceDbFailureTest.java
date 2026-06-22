package application.dbFailure;

import DTO.NotifyDTO;
import app.config.SystemProperties;
import application.*;
import DTO.UserDTO;
import domain.user.IUserRepo;
import domain.user.Member;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceDbFailureTest {

    private static final String TOKEN = "token";
    private static final String EMAIL = "user@test.com";
    private static final String PASSWORD = "Password123";
    private static final String ENCODED_PASSWORD = "encoded-password";
    private static final int USER_ID = 1;

    private TokenService tokenService;
    private IAuth auth;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private INotifier notifier;
    private TransactionStatus transactionStatus;
    private UserService userService;

    @BeforeAll
    static void disableLogs() {
        Logger.getLogger("").setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() {
        tokenService = mock(TokenService.class);
        auth = mock(IAuth.class);
        userRepo = mock(IUserRepo.class);
        passwordEncoder = mock(IPasswordEncoder.class);
        notifier = mock(INotifier.class);

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        transactionStatus = mock(TransactionStatus.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });

        when(passwordEncoder.encodePassword(PASSWORD)).thenReturn(ENCODED_PASSWORD);

        userService = new UserService(
                tokenService,
                auth,
                userRepo,
                passwordEncoder,
                notifier,
                transactionTemplate
        );
    }

    @BeforeEach
    void resetRetryHelperConfig() {
        SystemProperties systemProperties = new SystemProperties();
        systemProperties.setRetryCount(50);
        new RetryHelper(systemProperties);
    }

    // ============================================================================
    // Coverage note
    // ============================================================================
    //
    // This file injects DB failures through UserService use cases.
    // Domain entities are used only as mocked setup objects needed to drive the
    // service flow to the relevant repository call.
    //
    // Covered IUserRepo calls:
    // - existsUser: registerUser
    // - store: registerUser / getDelayedNotifications / cleanDelayedNotifications
    // - findUserByEmail: login / getDelayedNotifications / cleanDelayedNotifications / getUserId
    // - findById: getUserDisplayName
    //
    // Recovery coverage:
    // - userRepo.store fails once and succeeds on retry during registerUser.
    // - userRepo.findUserByEmail fails once and succeeds on retry during getDelayedNotifications.

    // ============================================================================
    // User repo failure tests
    // ============================================================================

    @Test
    void GivenUserRepoExistsUserFailure_WhenRegisterUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        UserDTO dto = mockValidUserDTO();

        when(userRepo.existsUser(EMAIL))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> userService.registerUser(null, dto)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).existsUser(EMAIL);
        verify(userRepo, never()).store(any(Member.class));
    }

    @Test
    void GivenUserRepoStoreFailure_WhenRegisterUser_ThenRollbackControlledErrorReturned() {
        // Arrange
        UserDTO dto = mockValidUserDTO();

        when(userRepo.existsUser(EMAIL)).thenReturn(false);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(userRepo)
                .store(any(Member.class));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> userService.registerUser(null, dto)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).store(any(Member.class));
    }

    @Test
    void GivenUserRepoFindUserByEmailFailure_WhenGetDelayedNotifications_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(userRepo.findUserByEmail(EMAIL))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<List<NotifyDTO>> response = assertDoesNotThrow(
                () -> userService.getDelayedNotifications(EMAIL)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).findUserByEmail(EMAIL);
    }

    @Test
    void GivenUserRepoStoreFailure_WhenGetDelayedNotifications_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member member = mock(Member.class);
        NotifyDTO notifyDTO = mock(NotifyDTO.class);

        when(member.fetchAndMarkPendingNotifications()).thenReturn(List.of(notifyDTO));
        when(userRepo.findUserByEmail(EMAIL)).thenReturn(member);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(userRepo)
                .store(member);

        // Act
        Response<List<NotifyDTO>> response = assertDoesNotThrow(
                () -> userService.getDelayedNotifications(EMAIL)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).store(member);
    }

    @Test
    void GivenUserRepoFindUserByEmailFailure_WhenCleanDelayedNotifications_ThenRollbackControlledErrorReturned() {
        // Arrange
        when(userRepo.findUserByEmail(EMAIL))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> userService.cleanDelayedNotifications(EMAIL)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).findUserByEmail(EMAIL);
    }

    @Test
    void GivenUserRepoStoreFailure_WhenCleanDelayedNotifications_ThenRollbackControlledErrorReturned() {
        // Arrange
        Member member = mock(Member.class);

        when(userRepo.findUserByEmail(EMAIL)).thenReturn(member);

        doThrow(new TransientDataAccessResourceException("DB is down"))
                .when(userRepo)
                .store(member);

        // Act
        Response<Boolean> response = assertDoesNotThrow(
                () -> userService.cleanDelayedNotifications(EMAIL)
        );

        // Assert
        assertControlledFailure(response);
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeastOnce()).store(member);
    }

    @Test
    void GivenUserRepoFindUserByEmailFailure_WhenLogin_ThenControlledErrorReturned() {
        // Arrange
        when(userRepo.findUserByEmail(EMAIL))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<String> response = assertDoesNotThrow(
                () -> userService.login(EMAIL, PASSWORD)
        );

        // Assert
        assertControlledFailure(response);
        verify(userRepo, atLeastOnce()).findUserByEmail(EMAIL);
    }

    @Test
    void GivenUserRepoFindUserByEmailFailure_WhenGetUserId_ThenRetryIsPerformed() {
        // Arrange
        when(auth.getRole(TOKEN)).thenReturn(new Response<>("MEMBER", "Role found"));
        when(auth.isLoggedIn(TOKEN)).thenReturn(new Response<>(true, "Logged in"));
        when(auth.getUserEmail(TOKEN)).thenReturn(new Response<>(EMAIL, "Email found"));

        when(userRepo.findUserByEmail(EMAIL))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        Response<Integer> response = assertDoesNotThrow(
                () -> userService.getUserId(TOKEN)
        );

        // Assert
        verify(userRepo, atLeast(2)).findUserByEmail(EMAIL);
    }

    @Test
    void GivenUserRepoFindByIdFailure_WhenGetUserDisplayName_ThenFallbackNameReturned() {
        // Arrange
        when(userRepo.findById(USER_ID))
                .thenThrow(new TransientDataAccessResourceException("DB is down"));

        // Act
        String result = assertDoesNotThrow(
                () -> userService.getUserDisplayName(USER_ID)
        );

        // Assert
        assertNotNull(result);
        assertEquals("User #" + USER_ID, result);
        verify(userRepo, atLeastOnce()).findById(USER_ID);
    }

    // ============================================================================
    // Full service flow DB recovery tests
    // ============================================================================


    @Test
    void GivenUserRepoStoreFailsOnce_WhenRegisterUser_ThenRetryIsPerformed() {
        // Arrange
        UserDTO dto = mockValidUserDTO();

        when(userRepo.existsUser(EMAIL))
                .thenReturn(false)
                .thenReturn(false);

        doThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .doNothing()
                .when(userRepo)
                .store(any(Member.class));

        // Act
        assertDoesNotThrow(() -> userService.registerUser(null, dto));

        // Assert
        verify(transactionStatus, atLeastOnce()).setRollbackOnly();
        verify(userRepo, atLeast(2)).existsUser(EMAIL);
        verify(userRepo, atLeast(2)).store(any(Member.class));
    }

    @Test
    void GivenUserRepoFindUserByEmailFailsOnce_WhenGetDelayedNotifications_ThenRetrySucceeds() {
        // Arrange
        Member member = mock(Member.class);

        when(member.fetchAndMarkPendingNotifications()).thenReturn(List.of());

        when(userRepo.findUserByEmail(EMAIL))
                .thenThrow(new TransientDataAccessResourceException("Temporary DB failure"))
                .thenReturn(member);

        // Act
        Response<List<NotifyDTO>> response = assertDoesNotThrow(
                () -> userService.getDelayedNotifications(EMAIL)
        );

        // Assert
        assertNotNull(response);
        assertNotNull(response.getValue());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
        verify(userRepo, atLeast(2)).findUserByEmail(EMAIL);
    }

    private UserDTO mockValidUserDTO() {
        UserDTO dto = mock(UserDTO.class);

        LocalDate birthDate = LocalDate.now().minusYears(20);

        when(dto.getEmail()).thenReturn(EMAIL);
        when(dto.getPassword()).thenReturn(PASSWORD);
        when(dto.getFirstName()).thenReturn("Test");
        when(dto.getLastName()).thenReturn("User");
        when(dto.getPhone()).thenReturn("050-123-4567");
        when(dto.getAddress()).thenReturn("Test Address");
        when(dto.getYear()).thenReturn(birthDate.getYear());
        when(dto.getMonth()).thenReturn(birthDate.getMonthValue());
        when(dto.getDay()).thenReturn(birthDate.getDayOfMonth());

        return dto;
    }

    private void assertControlledFailure(Response<?> response) {
        assertNotNull(response);
        assertTrue(response.getValue() == null || Boolean.FALSE.equals(response.getValue()));
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
    }
}