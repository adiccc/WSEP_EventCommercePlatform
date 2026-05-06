package application;

import DTO.QueueEntryResultDTO;
import Log.LoggerSetup;
import domain.company.ICompanyRepo;
import domain.dto.UserDTO;
import domain.event.IEventRepo;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.webQueue.WebQueue;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private UserService userService;
    private TokenService realTokenService;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private IAuth auth;
    private AdminService adminService;
    private ICompanyRepo companyRepo;
    private IEventRepo eventRepo;
    private String ADMIN_TOKEN;

    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
        WebQueue.resetForTesting();
        WebQueue.getInstance(100);

        realTokenService = new TokenService();
        userRepo = new UserRepo();
        passwordEncoder = new PasswordEncoderUtil();
        String adminEmail = "admin@admin.com";
        auth = new Auth(realTokenService, userRepo, passwordEncoder, Set.of(adminEmail));
        userService = new UserService(realTokenService, auth, userRepo, passwordEncoder);
        companyRepo = new CompanyRepoImpl();
        IPaymentSystem paymentSystem = Mockito.mock(IPaymentSystem.class);
        eventRepo = new EventRepoImpl();
        adminService = new AdminService(auth,userRepo, companyRepo,eventRepo,paymentSystem);
        userService.registerUser(null, new UserDTO(adminEmail, "Admin", "System", "Pass123!", 1, 1, 2000, "Israel", "050-000-0000"));
        ADMIN_TOKEN = userService.login(adminEmail, "Pass123!").getValue();
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

    @Test
    void GivenActiveGuestToken_WhenRegisterUser_ThenSuccessAndEncrypted() {
        //Arrange
        String guestToken = userService.continueAsGuest().getValue();

        UserDTO dto = createValidDTO();

        Response<Boolean> response = userService.registerUser(guestToken, dto);

        // Assert
        assertFalse(response.isError());
        assertTrue(response.getValue());

        Member savedUser = userRepo.findUserByEmail("yarin@bgu.ac.il");
        assertNotNull(savedUser);
        assertEquals("yarin@bgu.ac.il", savedUser.getIdentifier());
        assertTrue(passwordEncoder.matches("Password123!", savedUser.getPassword()));
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
        //Arrange
        String guestToken = userService.continueAsGuest().getValue();
        //Act
        Response<Boolean> response = userService.logout(guestToken);
        //Assert
        assertTrue(response.isError());
        assertFalse(response.getValue());
        assertEquals("User is in guest state", response.getMessage());
    }

    @Test
    void GivenNullOrBlankToken_WhenLogout_ThenErrorTokenEmpty() {
        Response<Boolean> responseNull = userService.logout(null);
        Response<Boolean> responseBlank = userService.logout("   ");

        assertTrue(responseNull.isError());
        assertEquals("token is empty or invalid", responseNull.getMessage());
        assertTrue(responseBlank.isError());
        assertEquals("token is empty or invalid", responseBlank.getMessage());
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
     //continue as guest
     @Test
     void GivenAdmittedUser_WhenContinueAsGuest_ThenReturnGuestToken() {
         Response<String> response = userService.continueAsGuest();
         assertNotNull(response.getValue());
         assertEquals("Guest session created successfully.", response.getMessage());
         assertEquals("GUEST", auth.getRole(response.getValue()).getValue());
     }

    // leaveStore
    @Test
    void GivenGuest_WhenLeaveStore_ThenSuccessAndQueueSlotFreed() {
        int initialActive = WebQueue.getInstance().getActiveCount();
        userService.enter();
        assertEquals(initialActive + 1, WebQueue.getInstance().getActiveCount());

        String guestToken = userService.continueAsGuest().getValue();

        Response<Boolean> response = userService.leaveStore(guestToken);

        assertNotNull(response.getValue());
        assertEquals("Logout successful", response.getMessage());
        assertEquals(initialActive, WebQueue.getInstance().getActiveCount());
    }

    @Test
    void GivenMember_WhenLeaveStore_ThenErrorMustUseLogout() {
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        String memberToken = userService.login(dto.getEmail(), dto.getPassword()).getValue();

        Response<Boolean> response = userService.leaveStore(memberToken);

        assertTrue(response.isError());
        assertFalse(response.getValue());
        assertEquals("Members should use logout, not leaveStore", response.getMessage());
    }

    @Test
    void GivenNullOrBlankToken_WhenLeaveStore_ThenErrorInvalidToken() {
        Response<Boolean> responseNull = userService.leaveStore(null);
        Response<Boolean> responseBlank = userService.leaveStore("   ");

        assertTrue(responseNull.isError());
        assertEquals("Invalid token", responseNull.getMessage());
        assertTrue(responseBlank.isError());
        assertEquals("Invalid token", responseBlank.getMessage());
    }
    @Test
    void GivenSameEmail_WhenConcurrentRegister_ThenOnlyOneSucceeds() throws Exception {
        String email = "race_register@mail.com";
        UserDTO dto1 = new UserDTO(email, "FirstA", "LastA", "Pass123!", 1, 1, 2000, "Address", "050-111-1111");
        UserDTO dto2 = new UserDTO(email, "FirstB", "LastB", "Pass123!", 1, 1, 2000, "Address", "050-222-2222");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Response<Boolean>> future1 = executor.submit(() -> {
            start.await();
            return userService.registerUser(null, dto1);
        });
        Future<Response<Boolean>> future2 = executor.submit(() -> {
            start.await();
            return userService.registerUser(null, dto2);
        });
        start.countDown();
        Response<Boolean> res1 = future1.get();
        Response<Boolean> res2 = future2.get();
        executor.shutdown();

        int success = 0;
        int failed = 0;
        if (!res1.isError() && res1.getValue()) success++; else failed++;
        if (!res2.isError() && res2.getValue()) success++; else failed++;

        assertEquals(1, success, "Exactly one registration should succeed for the same email");
        assertEquals(1, failed, "The other registration must fail due to DB unique constraints");
    }

    @Test
    void GivenMassiveTraffic_WhenConcurrentRegister_ThenAllSucceedWithoutBottleneck() throws Exception {
        int usersCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<Boolean>>> futures = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                start.await();
                UserDTO dto = new UserDTO("mass" + index + "yarin@mail.com", "Yarin", "Levi", "Pass123!", 1, 1, 2000, "Add", "050-000-0000");
                return userService.registerUser(null, dto);
            }));
        }

        start.countDown();

        int success = 0;
        for (Future<Response<Boolean>> future : futures) {
            if (!future.get().isError() && future.get().getValue()) success++;
        }
        executor.shutdown();

        assertEquals(usersCount, success, "All independent registrations should succeed perfectly in parallel");
    }
    @Test
    void GivenUsersInQueue_WhenActiveUserLogouts_ThenQueueAdvancesCorrectly() throws Exception {
        WebQueue.resetForTesting();
        WebQueue.getInstance(1);

        String email = "active@m.com";
        userService.registerUser(null, new UserDTO(email, "F", "L", "Pass!", 1, 1, 2000, "A", "050-000-0000"));
        userService.enter();
        String activeToken = userService.login(email, "Pass!").getValue();

        Response<QueueEntryResultDTO> guest1 = userService.enter();
        Response<QueueEntryResultDTO> guest2 = userService.enter();
        assertFalse(guest1.getValue().isAdmitted(), "Guest 1 should be waiting");
        assertFalse(guest2.getValue().isAdmitted(), "Guest 2 should be waiting");

        assertEquals(2, WebQueue.getInstance().getWaitingCount());

        userService.logout(activeToken);

        assertEquals(1, WebQueue.getInstance().getActiveCount(), "System should be full again");
        assertEquals(1, WebQueue.getInstance().getWaitingCount(), "Only one guest should remain in the queue");
    }

    @Test
    void GivenOneSpotLeft_WhenConcurrentEnter_ThenOneAdmittedOneQueued() throws Exception {
        WebQueue.resetForTesting();
        WebQueue.getInstance(100);
        int capacity = WebQueue.getInstance().getMaxCapacity();

        for (int i = 0; i < capacity - 1; i++) {
            userService.enter();
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<QueueEntryResultDTO>> future1 = executor.submit(() -> { start.await(); return userService.enter(); });
        Future<Response<QueueEntryResultDTO>> future2 = executor.submit(() -> { start.await(); return userService.enter(); });

        start.countDown();

        Response<QueueEntryResultDTO> r1 = future1.get();
        Response<QueueEntryResultDTO> r2 = future2.get();
        executor.shutdown();

        int admitted = 0;
        int queued = 0;

        if (r1.getValue() != null && r1.getValue().isAdmitted()) admitted++; else queued++;
        if (r2.getValue() != null && r2.getValue().isAdmitted()) admitted++; else queued++;

        assertEquals(1, admitted, "Only one user should get the last active spot");
        assertEquals(1, queued, "The other user must be sent to the waiting queue");
    }

    @Test
    void GivenConcurrentAdminRemove_WhenUserLogins_ThenUserBlocked() throws Exception {
        String email = "removed_racer@mail.com";
        userService.registerUser(null, new UserDTO(email, "F", "L", "Pass123!", 1, 1, 2000, "A", "050-000-0000"));
        int userId = userRepo.findUserByEmail(email).getUserId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> adminFuture = executor.submit(() -> {
            start.await();
            return adminService.removeUser(ADMIN_TOKEN, userId);
        });
        Future<Response<String>> loginFuture = executor.submit(() -> {
            start.await();
            return userService.login(email, "Pass123!");
        });

        start.countDown();
        adminFuture.get();
        Response<String> loginRes = loginFuture.get();
        executor.shutdown();

        if (loginRes.getValue() != null) {
            assertFalse(userRepo.findById(userId).isActive(), "User managed to login but must be inactive immediately");
        } else {
            assertTrue(loginRes.isError(), "Login correctly blocked the removed user");
        }
    }
    @Test
    void GivenSameUser_WhenDoubleLoginSimultaneously_ThenBothShouldSucceedOrGracefullyFail() throws Exception {
        String email = "double_login@mail.com";

        Response<Boolean> regRes = userService.registerUser(null, new UserDTO(email, "F", "L", "Pass123!", 1, 1, 2000, "A", "050-000-0000"));
        assertTrue(regRes.getValue() != null && regRes.getValue(), "Registration failed before login! Error: " + regRes.getMessage());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<String>> f1 = executor.submit(() -> { start.await(); return userService.login(email, "Pass123!"); });
        Future<Response<String>> f2 = executor.submit(() -> { start.await(); return userService.login(email, "Pass123!"); });

        start.countDown();
        Response<String> res1 = f1.get();
        Response<String> res2 = f2.get();
        executor.shutdown();
        boolean atLeastOneSuccess = (res1.getValue() != null) || (res2.getValue() != null);

        assertTrue(atLeastOneSuccess, "At least one login must succeed! Check the console output for the exact error.");
    }
    @Test
    void GivenSameUser_WhenDoubleLogoutSimultaneously_ThenActiveCountDecreasesOnlyOnce() throws Exception {
        WebQueue.resetForTesting();
        WebQueue.getInstance(10);

        String email = "double_logout@mail.com";
        userService.registerUser(null, new UserDTO(email, "F", "L", "Pass123!", 1, 1, 2000, "A", "050-000-0000"));

        userService.enter();
        String token = userService.login(email, "Pass123!").getValue();

        int activeBefore = WebQueue.getInstance().getActiveCount();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> f1 = executor.submit(() -> { start.await(); return userService.logout(token); });
        Future<Response<Boolean>> f2 = executor.submit(() -> { start.await(); return userService.logout(token); });

        start.countDown();
        Response<Boolean> res1 = f1.get();
        Response<Boolean> res2 = f2.get();
        executor.shutdown();

        int activeAfter = WebQueue.getInstance().getActiveCount();

        assertTrue(res1.getValue() != null && res1.getValue() || res2.getValue() != null && res2.getValue(), "At least one logout must succeed");
        assertEquals(activeBefore - 1, activeAfter, "Active count must decrement exactly once");
    }
    @Test
    void GivenEmptySystem_WhenMassiveGuestEntry_ThenAllAdmittedSafely() throws Exception {
        int guestCount = 100;
        WebQueue.getInstance().setMaxCapacity(150);
        int activeBefore = WebQueue.getInstance().getActiveCount();

        ExecutorService executor = Executors.newFixedThreadPool(guestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<String>>> futures = new ArrayList<>();

        for (int i = 0; i < guestCount; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                return userService.continueAsGuest();
            }));
        }

        start.countDown();

        int success = 0;
        for (Future<Response<String>> f : futures) {
            if (f.get().getValue() != null) success++;
        }
        executor.shutdown();

        assertEquals(guestCount, success, "All guests should receive unique tokens");
    }

}
