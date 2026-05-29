package infrastructure;

import application.Response;
import application.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthTest {

    private Auth auth;
    private TokenService tokenServiceMock;
    private final String ADMIN_EMAIL = "admin@admin.com";

    @BeforeEach
    void setUp() {
        tokenServiceMock = Mockito.mock(TokenService.class);
        auth = new Auth(tokenServiceMock, Set.of(ADMIN_EMAIL));
    }

    @Test
    void getRole_ValidToken_ReturnsRole() {
        String token = "valid.token";
        when(tokenServiceMock.validateToken(token)).thenReturn(true);
        when(tokenServiceMock.extractRole(token)).thenReturn("MEMBER");

        Response<String> response = auth.getRole(token);

        assertEquals("MEMBER", response.getValue());
        verify(tokenServiceMock).extractRole(token);
    }

    @Test
    void getRole_InvalidToken_ReturnsError() {
        String token = "expired.token";
        when(tokenServiceMock.validateToken(token)).thenReturn(false);

        Response<String> response = auth.getRole(token);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void getRole_LoggedOutToken_ReturnsError() {
        String token = "logged.out.token";
        when(tokenServiceMock.validateToken(token)).thenReturn(true);
        when(tokenServiceMock.extractRole(token)).thenReturn("MEMBER");
        when(tokenServiceMock.extractExpirationDate(token)).thenReturn(new java.util.Date(System.currentTimeMillis() + 100000));
        auth.logout(token);

        Response<String> response = auth.getRole(token);

        assertTrue(response.isError());
        assertEquals("Token is logged out", response.getMessage());
    }

    @Test
    void isAdmin_AdminToken_ReturnsTrue() {
        String token = "admin.token";
        when(tokenServiceMock.validateToken(token)).thenReturn(true);
        when(tokenServiceMock.extractUsername(token)).thenReturn(ADMIN_EMAIL);
        when(tokenServiceMock.extractRole(token)).thenReturn("MEMBER");
        Response<Boolean> response = auth.isAdmin(token);

        assertTrue(response.getValue());
    }

    @Test
    void isAdmin_RegularMemberToken_ReturnsFalse() {
        String token = "member.token";
        when(tokenServiceMock.validateToken(token)).thenReturn(true);
        when(tokenServiceMock.extractUsername(token)).thenReturn("user@mail.com");

        Response<Boolean> response = auth.isAdmin(token);

        assertFalse(response.getValue());
    }

    @Test
    void logout_ValidToken_BecomesLoggedOut() {
        String token = "valid.token";
        when(tokenServiceMock.validateToken(token)).thenReturn(true);
        when(tokenServiceMock.extractExpirationDate(token)).thenReturn(new java.util.Date(System.currentTimeMillis() + 100000));

        Response<Boolean> response = auth.logout(token);

        assertTrue(response.getValue());
        assertTrue(auth.getRole(token).isError());
    }
}