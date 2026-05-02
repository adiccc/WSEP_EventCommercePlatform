package infrastructure;

import application.IPasswordEncoder;
import application.Response;
import application.TokenService;
import domain.user.IUserRepo;
import domain.user.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthTest {

    private Auth auth;
    private TokenService tokenServiceMock;
    private IUserRepo userRepoMock;
    private IPasswordEncoder passwordEncoderMock;

    @BeforeEach
    void setUp() {
        tokenServiceMock = Mockito.mock(TokenService.class);
        userRepoMock = Mockito.mock(IUserRepo.class);
        passwordEncoderMock = Mockito.mock(IPasswordEncoder.class);
        auth = new Auth(tokenServiceMock, userRepoMock, passwordEncoderMock);
    }

    @Test
    void givenValidCredentials_whenLogin_thenReturnToken() {
        // Arrange
        String email = "test@example.com";
        String password = "password123";
        String encodedPassword = "encodedPassword123";
        String expectedToken = "validToken123";
        Member mockMember = new Member(email, encodedPassword, "Test", "User", "050-1234567", null, "Test Address");

        when(userRepoMock.findUserByEmail(email)).thenReturn(mockMember);
        when(passwordEncoderMock.matches(password, encodedPassword)).thenReturn(true);
        when(tokenServiceMock.generateToken(email)).thenReturn(expectedToken);

        // Act
        Response<String> response = auth.login(email, password);

        // Assert
        assertEquals("validToken123", response.getValue());
        assertEquals(expectedToken, response.getValue());
        assertEquals("Login successful", response.getMessage());

        // Verify that the mock methods were called
        verify(userRepoMock, times(1)).findUserByEmail(email);
        verify(passwordEncoderMock, times(1)).matches(password, encodedPassword);
        verify(tokenServiceMock, times(1)).generateToken(email);
    }

    @Test
    void givenNonExistentEmail_whenLogin_thenReturnError() {
        // Arrange
        String email = "wrong@example.com";
        String password = "password123";

        // User not found in repo
        when(userRepoMock.findUserByEmail(email)).thenReturn(null);

        // Act
        Response<String> response = auth.login(email, password);

        // Assert
        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("Invalid email or password", response.getMessage());

        // Verify token was NOT generated
        verify(tokenServiceMock, never()).generateToken(anyString());
    }

    @Test
    void givenWrongPassword_whenLogin_thenReturnError() {
        // Arrange
        String email = "test@example.com";
        String password = "wrongPassword";
        String encodedPassword = "encodedPassword123";
        Member mockMember = new Member(email, encodedPassword, "Test", "User", "050-1234567", null, "Test Address");

        // User found, but password doesn't match
        when(userRepoMock.findUserByEmail(email)).thenReturn(mockMember);
        when(passwordEncoderMock.matches(password, encodedPassword)).thenReturn(false);

        // Act
        Response<String> response = auth.login(email, password);

        // Assert
        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("Invalid email or password", response.getMessage());

        // Verify token was NOT generated
        verify(tokenServiceMock, never()).generateToken(anyString());
    }

    @Test
    void givenRepositoryException_whenLogin_thenReturnServerError() {
        // Arrange
        String email = "test@example.com";
        String password = "password123";

        // Simulate a database error
        when(userRepoMock.findUserByEmail(email)).thenThrow(new RuntimeException("Database down"));

        // Act
        Response<String> response = auth.login(email, password);

        // Assert
        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("Login failed due to server error", response.getMessage());
    }

    @Test
    void givenValidToken_whenLogout_thenReturnSuccess() {
        // Arrange
        String token = "valid.jwt.token";
        String email = "test@example.com";
        Member mockMember = Mockito.mock(Member.class);
        Date futureDate = new Date(System.currentTimeMillis() + 100000);

        when(mockMember.getIdentifier()).thenReturn(email);
        when(mockMember.getUserId()).thenReturn(1);
        when(tokenServiceMock.validateToken(token)).thenReturn(true);
        when(userRepoMock.findUserByEmail(email)).thenReturn(mockMember);
        when(tokenServiceMock.extractExpirationDate(token)).thenReturn(futureDate);
        // Act
        Response<Boolean> response = auth.logout(token);
        // Assert
        assertTrue(response.getValue());
        assertEquals("Logout successful", response.getMessage());
        assertFalse(auth.isLoggedIn(token).getValue()); //making sure that the user is actually logged out!
        verify(tokenServiceMock, times(1)).extractExpirationDate(token); //making sure that we took the expiration date in order to insert to the HASHMAP
    }
    @Test
    void givenNullOrBlankToken_whenLogout_thenReturnError() {
        Response<Boolean> responseNull = auth.logout(null);
        Response<Boolean> responseBlank = auth.logout("   ");
        // Assert
        assertFalse(responseNull.getValue());
        assertEquals("Token is missing or empty", responseNull.getMessage());

        assertFalse(responseBlank.getValue());
        assertEquals("Token is missing or empty", responseBlank.getMessage());

        verify(tokenServiceMock, never()).extractExpirationDate(anyString());
    }

    @Test
    void givenAlreadyLoggedOutToken_whenLogout_thenReturnAlreadyLoggedOutError() {
        // Arrange
        String token = "valid.jwt.token";
        String email = "test@example.com";
        Member mockMember = Mockito.mock(Member.class);
        Date futureDate = new Date(System.currentTimeMillis() + 100000);

        when(mockMember.getIdentifier()).thenReturn(email);
        when(mockMember.getUserId()).thenReturn(1);

        when(tokenServiceMock.validateToken(token)).thenReturn(true);
        when(tokenServiceMock.extractUsername(token)).thenReturn(email);
        when(userRepoMock.findUserByEmail(email)).thenReturn(mockMember);
        when(tokenServiceMock.extractExpirationDate(token)).thenReturn(futureDate);

        // Act
        auth.logout(token);
        Response<Boolean> secondLogoutResponse = auth.logout(token);
        // Assert
        assertFalse(secondLogoutResponse.getValue());
        assertEquals("Cannot log out, user is Already logged out", secondLogoutResponse.getMessage());
        verify(tokenServiceMock, times(2)).extractExpirationDate(token); // <--- התיקון!
    }
    @Test
    void givenTokenServiceException_whenLogout_thenReturnServerError() {
        // Arrange
        String token = "corrupted.jwt.token";
        String email = "test@example.com";
        Member mockMember = new Member(email, "encrypted", "Test", "User", "050", null, "Address");

        when(tokenServiceMock.validateToken(token)).thenReturn(true);
        when(tokenServiceMock.extractUsername(token)).thenReturn(email);
        when(userRepoMock.findUserByEmail(email)).thenReturn(mockMember);
        when(tokenServiceMock.extractExpirationDate(token)).thenThrow(new RuntimeException("Malformed JWT string"));
        // Act
        Response<Boolean> response = auth.logout(token);
        // Assert
        assertFalse(response.getValue());
        assertEquals("Logout failed due to server error", response.getMessage());
    }
    @Test
    void givenValidToken_whenGetRole_thenReturnRole() {
        // Arrange
        String token = "valid.jwt.token";
        String expectedRole = "MEMBER";
        when(tokenServiceMock.validateToken(token)).thenReturn(true);
        when(tokenServiceMock.extractRole(token)).thenReturn(expectedRole);

        // Act
        Response<String> response = auth.getRole(token);

        // Assert
        assertEquals("retrieved role", response.getMessage());
        assertEquals(expectedRole, response.getValue());
        verify(tokenServiceMock, times(1)).extractRole(token);
    }

    @Test
    void givenNullOrBlankToken_whenGetRole_thenReturnError() {
        // Act
        Response<String> responseNull = auth.getRole(null);
        Response<String> responseBlank = auth.getRole("   ");

        // Assert
        assertNull(responseNull.getValue());
        assertEquals("Token is missing or empty", responseNull.getMessage());

        assertNull(responseBlank.getValue());
        assertEquals("Token is missing or empty", responseBlank.getMessage());

    }

    @Test
    void givenGuestToken_whenGetUserId_thenReturnMinusOne() {
        // Arrange
        String guestToken = "guest.jwt.token";
        when(tokenServiceMock.extractRole(guestToken)).thenReturn("GUEST");

        // Act
        Response<Integer> response = auth.getUserId(guestToken);

        // Assert
        assertEquals(-1, response.getValue());
        assertEquals("Guest token recognized", response.getMessage());
        // Verify that it didn't try to query the database
        verify(userRepoMock, never()).findUserByEmail(anyString());
    }

    @Test
    void givenValidMemberToken_whenGetUserId_thenReturnUserId() {
        // Arrange
        String memberToken = "member.jwt.token";
        String email = "test@example.com";
        Member mockMember = Mockito.mock(Member.class);

        when(tokenServiceMock.extractRole(memberToken)).thenReturn("MEMBER");
        // Mocking the isLoggedIn logic
        when(tokenServiceMock.validateToken(memberToken)).thenReturn(true);
        when(tokenServiceMock.extractUsername(memberToken)).thenReturn(email);
        when(userRepoMock.findUserByEmail(email)).thenReturn(mockMember);
        when(mockMember.getUserId()).thenReturn(42);

        // Act
        Response<Integer> response = auth.getUserId(memberToken);

        // Assert
        assertEquals(42, response.getValue());
        assertEquals("Retrieved member", response.getMessage());
    }

}