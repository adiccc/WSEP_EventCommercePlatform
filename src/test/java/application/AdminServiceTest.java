package application;

import domain.dto.UserDTO;
import domain.user.IUserRepo;
import domain.webQueue.WebQueue;
import infrastructure.Auth;
import infrastructure.PasswordEncoderUtil;
import infrastructure.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdminServiceTest {

    private AdminService adminService;
    private UserService userService;
    private String adminToken;
    private String nonAdminToken;

    private static final String ADMIN_EMAIL = "admin@bgu.ac.il";
    private static final String USER_EMAIL = "user@bgu.ac.il";
    private static final String PASSWORD = "Pass123!";

    @BeforeEach
    void setUp() {
        WebQueue.resetForTesting();
        WebQueue.getInstance(100);

        TokenService tokenService = new TokenService();
        IUserRepo userRepo = new UserRepo();
        IPasswordEncoder passwordEncoder = new PasswordEncoderUtil();
        IAuth auth = new Auth(tokenService, userRepo, passwordEncoder, Set.of(ADMIN_EMAIL));

        userService = new UserService(tokenService, auth, userRepo, passwordEncoder);
        adminService = new AdminService(auth);

        UserDTO adminDTO = new UserDTO(ADMIN_EMAIL, "Admin", "User", PASSWORD, 1, 1, 1990, "City", "050-000-0000");
        UserDTO userDTO = new UserDTO(USER_EMAIL, "Regular", "User", PASSWORD, 1, 1, 1990, "City", "050-111-1111");
        userService.registerUser(null, adminDTO);
        userService.registerUser(null, userDTO);

        adminToken = userService.login(ADMIN_EMAIL, PASSWORD).getValue();
        nonAdminToken = userService.login(USER_EMAIL, PASSWORD).getValue();
    }

    // --- setMaxCapacity ---

    @Test
    void GivenAdminToken_WhenSetMaxCapacity_ThenSuccess() {
        Response<Boolean> response = adminService.setMaxCapacity(adminToken, 200);

        assertFalse(response.isError());
        assertTrue(response.getValue());
        assertEquals(200, WebQueue.getInstance().getMaxCapacity());
    }

    @Test
    void GivenNonAdminToken_WhenSetMaxCapacity_ThenUnauthorized() {
        Response<Boolean> response = adminService.setMaxCapacity(nonAdminToken, 200);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
        assertEquals(100, WebQueue.getInstance().getMaxCapacity());
    }

    @Test
    void GivenAdminToken_WhenSetMaxCapacityZero_ThenError() {
        Response<Boolean> response = adminService.setMaxCapacity(adminToken, 0);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("greater than 0"));
    }

    @Test
    void GivenAdminToken_WhenSetMaxCapacityNegative_ThenError() {
        Response<Boolean> response = adminService.setMaxCapacity(adminToken, -5);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("greater than 0"));
    }

    // --- getMaxCapacity ---

    @Test
    void GivenAdminToken_WhenGetMaxCapacity_ThenReturnsCapacity() {
        Response<Integer> response = adminService.getMaxCapacity(adminToken);

        assertFalse(response.isError());
        assertEquals(100, response.getValue());
    }

    @Test
    void GivenNonAdminToken_WhenGetMaxCapacity_ThenUnauthorized() {
        Response<Integer> response = adminService.getMaxCapacity(nonAdminToken);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
    }

    // --- getActiveCount ---

    @Test
    void GivenAdminToken_WhenGetActiveCount_ThenReturnsCount() {
        WebQueue.getInstance().tryEnter(uuid -> {});
        WebQueue.getInstance().tryEnter(uuid -> {});

        Response<Integer> response = adminService.getActiveCount(adminToken);

        assertFalse(response.isError());
        assertEquals(2, response.getValue());
    }

    @Test
    void GivenNonAdminToken_WhenGetActiveCount_ThenUnauthorized() {
        Response<Integer> response = adminService.getActiveCount(nonAdminToken);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
    }

    // --- getWaitingCount ---

    @Test
    void GivenAdminToken_WhenGetWaitingCount_ThenReturnsCount() {
        WebQueue.resetForTesting();
        WebQueue.getInstance(1);
        WebQueue.getInstance().tryEnter(uuid -> {}); // fills the slot
        WebQueue.getInstance().tryEnter(uuid -> {}); // goes to queue
        WebQueue.getInstance().tryEnter(uuid -> {}); // goes to queue

        Response<Integer> response = adminService.getWaitingCount(adminToken);

        assertFalse(response.isError());
        assertEquals(2, response.getValue());
    }

    @Test
    void GivenNonAdminToken_WhenGetWaitingCount_ThenUnauthorized() {
        Response<Integer> response = adminService.getWaitingCount(nonAdminToken);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
    }

    // --- invalidToken ---

    @Test
    void GivenInvalidToken_WhenAnyAdminAction_ThenUnauthorized() {
        Response<Boolean> setCapacity = adminService.setMaxCapacity("invalid-token", 50);
        Response<Integer> getCapacity = adminService.getMaxCapacity("invalid-token");
        Response<Integer> getActive = adminService.getActiveCount("invalid-token");
        Response<Integer> getWaiting = adminService.getWaitingCount("invalid-token");

        assertTrue(setCapacity.isError());
        assertTrue(getCapacity.isError());
        assertTrue(getActive.isError());
        assertTrue(getWaiting.isError());
    }
}
