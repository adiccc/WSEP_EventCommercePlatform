package application;

import domain.dto.UserDTO;
import domain.user.IUserRepo;
import domain.user.Member;
import infrastructure.Auth;
import infrastructure.PasswordEncoderUtil;
import infrastructure.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private UserService userService;
    private TokenService realTokenService;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private IAuth auth;

    @BeforeEach
    void setUp() {
        realTokenService = new TokenService();
        userRepo = new UserRepo();
        passwordEncoder = new PasswordEncoderUtil();
        auth = new Auth(realTokenService, userRepo, passwordEncoder);

        userService = new UserService(realTokenService, auth, userRepo, passwordEncoder);
    }
    private UserDTO createValidDTO() {
        return new UserDTO(
                "yarin@bgu.ac.il", "Yarin", "Levi", "Password123!",
                15, 5, 2000, "Beer Sheva", "050-123-4567"
        );
    }
    @Test
    void GivenValidGuestAndData_WhenRegisterUser_ThenSuccessAndEncrypted() {
        UserDTO dto = createValidDTO();

        Response<Boolean> response = userService.registerUser(null, dto);

        assertFalse(response.isError());
        assertTrue(response.getValue());
        Member savedUser = userRepo.findUserByEmail("yarin@bgu.ac.il");
        assertNotNull(savedUser);
        assertEquals("yarin@bgu.ac.il", savedUser.getIdentifier());
        assertTrue(passwordEncoder.matches("Password123!", savedUser.getPassword()));
    }
    @Test
    void GivenValidJwtToken_WhenRegisterUser_ThenErrorMustBeGuest() {
        //Arrange
        UserDTO activeUserDTO = new UserDTO("active_user@bgu.ac.il", "Active", "User", "Pass123!", 1, 1, 2000, "City", "050-123-4567");
        userService.registerUser(null, activeUserDTO);
        String realJwtToken = userService.login("active_user@bgu.ac.il", "Pass123!").getValue();

        // Act
        UserDTO dto = createValidDTO();
        Response<Boolean> response = userService.registerUser(realJwtToken, dto);

        // Assert
        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("must be a guest"));
        assertNull(userRepo.findUserByEmail("yarin@bgu.ac.il"));
    }
    @Test
    void GivenExistingEmail_WhenRegisterUser_ThenErrorUserExists() {
        // Arrange
        UserDTO existingUser = createValidDTO();
        userService.registerUser(null, existingUser);

        // Act
        Response<Boolean> response = userService.registerUser(null, existingUser);

        // Assert
        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("already exists"));
    }

    @Test
    void GivenInvalidDayMonth_WhenRegisterUser_ThenErrorInvalidDateFormat() {
        UserDTO dto = new UserDTO("yarin@bgu.ac.il", "Yarin", "Levi", "Pass", 31, 2, 2000, "City", "050-123-4567");
        Response<Boolean> response = userService.registerUser(null, dto);
        assertTrue(response.isError());
        assertEquals("Invalid format date", response.getMessage());
    }

    @Test
    void GivenFutureBirthDate_WhenRegisterUser_ThenErrorFutureDate() {
        int futureYear = LocalDate.now().getYear() + 5;
        UserDTO dto = new UserDTO("yarin@bgu.ac.il", "Yarin", "Levi", "Pass", 1, 1, futureYear, "City", "050-123-4567");
        Response<Boolean> response = userService.registerUser(null, dto);
        assertTrue(response.isError());
        assertEquals("birth date cannot be after current date", response.getMessage());
    }
    @Test
    void GivenInvalidEmailFormat_WhenRegisterUser_ThenError() {
        UserDTO dto = new UserDTO("yarin.bgu.ac.il", "Yarin", "Levi", "Pass", 1, 1, 2000, "City", "050-123-4567");
        Response<Boolean> response = userService.registerUser(null, dto);
        assertTrue(response.isError());
        assertEquals("Invalid email format", response.getMessage());
    }

    @Test
    void GivenInvalidPhoneFormat_WhenRegisterUser_ThenError() {
        UserDTO dto = new UserDTO("yarin@bgu.ac.il", "Yarin", "Levi", "Pass", 1, 1, 2000, "City", "0501234567");
        Response<Boolean> response = userService.registerUser(null, dto);
        assertTrue(response.isError());
        assertEquals("Invalid phone format", response.getMessage());
    }
    @Test
    void GivenEmptyFirstName_WhenRegisterUser_ThenError() {
        UserDTO dto = new UserDTO("yarin@bgu.ac.il", "   ", "Levi", "Pass", 1, 1, 2000, "City", "050-123-4567");
        Response<Boolean> response = userService.registerUser(null, dto);
        assertTrue(response.isError());
        assertEquals("First name cannot be empty", response.getMessage());
    }

    @Test
    void GivenNullLastName_WhenRegisterUser_ThenError() {
        UserDTO dto = new UserDTO("yarin@bgu.ac.il", "Yarin", null, "Pass", 1, 1, 2000, "City", "050-123-4567");
        Response<Boolean> response = userService.registerUser(null, dto);
        assertTrue(response.isError());
        assertEquals("Last name cannot be empty", response.getMessage());
    }

    @Test
    void GivenBlankPassword_WhenRegisterUser_ThenError() {
        UserDTO dto = new UserDTO("yarin@bgu.ac.il", "Yarin", "Levi", "", 1, 1, 2000, "City", "050-123-4567");
        Response<Boolean> response = userService.registerUser(null, dto);
        assertTrue(response.isError());
        assertEquals("Password cannot be empty", response.getMessage());
    }

    @Test
    void GivenNullAddress_WhenRegisterUser_ThenError() {
        UserDTO dto = new UserDTO("yarin@bgu.ac.il", "Yarin", "Levi", "Pass", 1, 1, 2000, null, "050-123-4567");
        Response<Boolean> response = userService.registerUser(null, dto);
        assertTrue(response.isError());
        assertEquals("Address cannot be null", response.getMessage());
    }

    @Test
    void GivenRegisteredUser_WhenLoginWithValidCredentials_ThenReturnToken() {
        // Arrange
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        // Act
        Response<String> response = userService.login("yarin@bgu.ac.il", "Password123!");
        // Assert
        assertNotNull(response.getValue());
        assertEquals("Login successful", response.getMessage());
    }

    @Test
    void GivenRegisteredUser_WhenLoginWithWrongPassword_ThenReturnError() {
        // Arrange
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        // Act
        Response<String> response = userService.login("yarin@bgu.ac.il", "WrongPass!");
        // Assert
        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("Invalid email or password", response.getMessage());
    }

    @Test
    void GivenUnregisteredUser_WhenLogin_ThenReturnError() {
        //we don't have any set up of registering some user
        Response<String> response = userService.login("ghost@bgu.ac.il", "Pass123!");
        // Assert
        assertNull(response.getValue());
        assertEquals("Invalid email or password", response.getMessage());
    }

    @Test
    void GivenLoggedInUser_WhenLogout_ThenSuccessAndTokenBlacklisted() {
        // Arrange
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        Response<String> loginResponse = userService.login(dto.getEmail(), dto.getPassword());
        String validToken = loginResponse.getValue();
        // Act
        Response<Boolean> logoutResponse = userService.logout(validToken);
        // Assert
        assertTrue(logoutResponse.getValue());
        assertEquals("Logout successful", logoutResponse.getMessage());
    }

    @Test
    void GivenGuest_WhenLogout_ThenErrorUserInGuestState() {
        Response<Boolean> responseNull = userService.logout(null);
        Response<Boolean> responseBlank = userService.logout("   ");
        // Assert
        assertFalse(responseNull.getValue());
        assertEquals("User is in guest state", responseNull.getMessage());

        assertFalse(responseBlank.getValue());
        assertEquals("User is in guest state", responseBlank.getMessage());
    }

    @Test
    void GivenAlreadyLoggedOutUser_WhenLogoutAgain_ThenError() {
        // Arrange
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        String validToken = userService.login(dto.getEmail(), dto.getPassword()).getValue();
        userService.logout(validToken);
        Response<Boolean> response = userService.logout(validToken);
        // Assert
        assertTrue(response.isError());
        assertFalse(response.getValue());
        assertEquals("Logout failed", response.getMessage());
    }
}