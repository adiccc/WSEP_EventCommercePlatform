package application;

import DTO.QueueEntryResultDTO;
import Log.LoggerSetup;
import domain.dto.UserDTO;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.webQueue.WebQueue;
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
        LoggerSetup.setup();
        WebQueue.resetForTesting();
        WebQueue.getInstance(100);

        realTokenService = new TokenService();
        userRepo = new UserRepo();
        passwordEncoder = new PasswordEncoderUtil();
        auth = new Auth(realTokenService, userRepo, passwordEncoder, Set.of());
        userService = new UserService(realTokenService, auth, userRepo, passwordEncoder);
    }

    private UserDTO createValidDTO() {
        return new UserDTO(
                "yarin@bgu.ac.il", "Yarin", "Levi", "Password123!",
                15, 5, 2000, "Beer Sheva", "050-123-4567"
        );
    }

    // --- register ---

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
        UserDTO activeUserDTO = new UserDTO("active_user@bgu.ac.il", "Active", "User", "Pass123!", 1, 1, 2000, "City", "050-123-4567");
        userService.registerUser(null, activeUserDTO);
        String realJwtToken = userService.login("active_user@bgu.ac.il", "Pass123!").getValue();

        UserDTO dto = createValidDTO();
        Response<Boolean> response = userService.registerUser(realJwtToken, dto);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("must be a guest"));
        assertNull(userRepo.findUserByEmail("yarin@bgu.ac.il"));
    }

    @Test
    void GivenExistingEmail_WhenRegisterUser_ThenErrorUserExists() {
        UserDTO existingUser = createValidDTO();
        userService.registerUser(null, existingUser);

        Response<Boolean> response = userService.registerUser(null, existingUser);

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

    // --- login ---

    @Test
    void GivenRegisteredUser_WhenLoginWithValidCredentials_ThenReturnToken() {
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);

        Response<String> response = userService.login("yarin@bgu.ac.il", "Password123!");

        assertNotNull(response.getValue());
        assertEquals("Login successful", response.getMessage());
    }

    @Test
    void GivenRegisteredUser_WhenLoginWithWrongPassword_ThenReturnError() {
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);

        Response<String> response = userService.login("yarin@bgu.ac.il", "WrongPass!");

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertEquals("Invalid email or password", response.getMessage());
    }

    @Test
    void GivenUnregisteredUser_WhenLogin_ThenReturnError() {
        Response<String> response = userService.login("ghost@bgu.ac.il", "Pass123!");
        assertNull(response.getValue());
        assertEquals("Invalid email or password", response.getMessage());
    }

    // --- logout ---

    @Test
    void GivenLoggedInUser_WhenLogout_ThenSuccessAndQueueSlotFreed() {
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        String token = userService.login(dto.getEmail(), dto.getPassword()).getValue();
        int activeBefore = WebQueue.getInstance().getActiveCount();

        Response<Boolean> response = userService.logout(token);

        assertTrue(response.getValue());
        assertEquals("Logout successful", response.getMessage());
        assertEquals(activeBefore - 1, WebQueue.getInstance().getActiveCount());
    }

    @Test
    void GivenGuest_WhenLogout_ThenErrorUserInGuestState() {
        Response<Boolean> responseNull = userService.logout(null);
        Response<Boolean> responseBlank = userService.logout("   ");

        assertFalse(responseNull.getValue());
        assertEquals("User is in guest state", responseNull.getMessage());
        assertFalse(responseBlank.getValue());
        assertEquals("User is in guest state", responseBlank.getMessage());
    }

    @Test
    void GivenAlreadyLoggedOutUser_WhenLogoutAgain_ThenError() {
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        String token = userService.login(dto.getEmail(), dto.getPassword()).getValue();
        userService.logout(token);

        Response<Boolean> response = userService.logout(token);

        assertTrue(response.isError());
        assertFalse(response.getValue());
        assertEquals("Logout failed", response.getMessage());
    }

    // --- enter (queue) ---

    @Test
    void GivenCapacityAvailable_WhenEnter_ThenAdmittedImmediately() {
        Response<QueueEntryResultDTO> response = userService.enter();

        assertFalse(response.isError());
        assertTrue(response.getValue().isAdmitted());
        assertNotNull(response.getValue().getToken());
    }

    @Test
    void GivenSystemFull_WhenEnter_ThenPlacedInQueue() {
        WebQueue.resetForTesting();
        WebQueue.getInstance(1);
        userService.enter(); // fills the only slot

        Response<QueueEntryResultDTO> response = userService.enter();

        assertFalse(response.isError());
        assertFalse(response.getValue().isAdmitted());
        assertEquals(1, response.getValue().getPosition());
    }

    // --- getQueueStatus ---

    @Test
    void GivenAdmittedUser_WhenGetQueueStatus_ThenReturnsAdmitted() {
        String uuid = userService.enter().getValue().getToken();

        Response<QueueEntryResultDTO> status = userService.getQueueStatus(uuid);

        assertFalse(status.isError());
        assertTrue(status.getValue().isAdmitted());
    }

    @Test
    void GivenWaitingUser_WhenGetQueueStatus_ThenReturnsPosition() {
        WebQueue.resetForTesting();
        WebQueue.getInstance(1);
        userService.enter(); // fills the slot
        String waitingUuid = userService.enter().getValue().getToken();

        Response<QueueEntryResultDTO> status = userService.getQueueStatus(waitingUuid);

        assertFalse(status.isError());
        assertFalse(status.getValue().isAdmitted());
        assertEquals(1, status.getValue().getPosition());
    }
}
