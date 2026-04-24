package application;

import domain.dto.UserDTO;
import domain.user.IUserRepo;
import domain.user.Member;
import infrastructure.Auth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private UserService userService;
    private TokenService realTokenService;
    private IUserRepo fakeRepo;
    private List<Member> savedMembers;

    @BeforeEach
    void setUp() {
        savedMembers = new ArrayList<>();
        fakeRepo = new IUserRepo() {
            private final Map<String, Member> db = new HashMap<>();

            @Override public boolean existsUser(String email) { return db.containsKey(email); }
            @Override public Member findUserByEmail(String email) { return db.get(email); }
            @Override public void store(Member mem) {
                db.put(mem.getIdentifier(), mem);
                savedMembers.add(mem);
            }
            @Override public Member findById(Integer userId) {return null;}
            @Override public List<Member> getAll() { return null; }
            @Override public void delete(Integer userId) {}
        };
        realTokenService = new TokenService();
        IPasswordEncoder encoderMock = new IPasswordEncoder() {
            @Override public String encodePassword(String rawPassword) { return rawPassword + "_encrypted"; }
            @Override public boolean matches(String rawPassword, String encodedPassword) { return false; }
        };

        IAuth realAuth = new Auth(realTokenService, fakeRepo, encoderMock);
        userService = new UserService(realTokenService, realAuth, fakeRepo, encoderMock);
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
        assertEquals(1, savedMembers.size());
        assertEquals("yarin@bgu.ac.il", savedMembers.get(0).getIdentifier());
        assertEquals("Password123!_encrypted", savedMembers.get(0).getPassword());
    }
    @Test
    void GivenValidJwtToken_WhenRegisterUser_ThenErrorMustBeGuest() {
        Member activeMember = new Member("active_user@bgu.ac.il", "Pass", "Active", "User", "050-123-4567", LocalDate.of(2000, 1, 1), "City");
        fakeRepo.store(activeMember);
        String realJwtToken = realTokenService.generateToken(activeMember.getIdentifier());
        UserDTO dto = createValidDTO();
        Response<Boolean> response = userService.registerUser(realJwtToken, dto);
        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("must be a guest"));
        assertEquals(1, savedMembers.size());
    }
    @Test
    void GivenExistingEmail_WhenRegisterUser_ThenErrorUserExists() {
        Member existingUser = new Member("yarin@bgu.ac.il", "Pass", "Yarin", "Levi", "050-123-4567", LocalDate.of(2000, 1, 1), "City");
        fakeRepo.store(existingUser); // שומרים אותו!
        UserDTO dto = createValidDTO();
        Response<Boolean> response = userService.registerUser(null, dto);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("already exists"));
        assertEquals(1, savedMembers.size());
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
}