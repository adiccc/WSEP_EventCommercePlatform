package infrastructure;

import application.IPasswordEncoder;
import application.Response;
import application.TokenService;
import domain.user.IUserRepo;
import domain.user.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
}