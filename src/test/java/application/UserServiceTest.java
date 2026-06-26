package application;

import DTO.NotifyDTO;
import DTO.NotifyType;
import DTO.QueueEntryResultDTO;
import Log.LoggerSetup;
import app.config.SystemProperties;
import domain.Suspension.ISuspensionRepo;
import domain.company.ICompanyRepo;
import domain.dataType.PermissionType;
import DTO.UserDTO;
import domain.event.IEventRepo;
import domain.lottery.AccessCodeGenerator;
import domain.user.IUserRepo;
import domain.user.Member;
import infrastructure.Auth;
import infrastructure.Broadcaster;
import infrastructure.PasswordEncoderUtil;
import infrastructure.VaadinNotifier;
import infrastructure.inMemory.CompanyRepoImpl;
import infrastructure.inMemory.EventRepoImpl;
import infrastructure.inMemory.SuspensionRepoImpl;
import infrastructure.inMemory.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserService userService;
    private TokenService realTokenService;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private IAuth auth;
    private ISuspensionRepo suspensionRepo;
    private AdminService adminService;
    private CompanyService companyService;
    private ICompanyRepo companyRepo;
    private IEventRepo eventRepo;
    private String ADMIN_TOKEN;
    private INotifier notifier;
    private TransactionTemplate transactionTemplate;
    private ITicketSupply ticketSupply;

    @BeforeEach
    void setUp() {
        SystemProperties systemProperties = createTestSystemProperties();
        realTokenService = new TokenService(systemProperties);
        new RetryHelper(systemProperties);

        AccessCodeGenerator.configure(
                "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                6
        );
        LoggerSetup.setup();
        WebQueue.resetForTesting();
        WebQueue.getInstance(100);
        suspensionRepo = new SuspensionRepoImpl();
        realTokenService = new TokenService(createTestSystemProperties());
        userRepo = new UserRepo();
        passwordEncoder = new PasswordEncoderUtil();
        ticketSupply = mock(ITicketSupply.class);
        String adminEmail = "admin@admin.com";
        auth = new Auth(realTokenService, Set.of(adminEmail));
        notifier = new VaadinNotifier();
        transactionTemplate = mock(TransactionTemplate.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new org.springframework.transaction.support.SimpleTransactionStatus());
        });
        userService = new UserService(realTokenService, auth, userRepo, passwordEncoder,notifier,transactionTemplate);
        companyRepo = new CompanyRepoImpl();
        IPaymentSystem paymentSystem = Mockito.mock(IPaymentSystem.class);
        eventRepo = new EventRepoImpl();
        adminService = new AdminService(auth,userRepo, companyRepo,eventRepo,paymentSystem, suspensionRepo,notifier,transactionTemplate,ticketSupply);
        userService.registerUser(null, new UserDTO(adminEmail, "Admin", "System", "Pass123!", 1, 1, 2000, "Israel", "050-000-0000"));
        ADMIN_TOKEN = userService.login(adminEmail, "Pass123!").getValue();
        companyService = new CompanyService(auth,companyRepo,userRepo,suspensionRepo,notifier,transactionTemplate);
    }
    private SystemProperties createTestSystemProperties() {
        SystemProperties systemProperties = new SystemProperties();
        systemProperties.setMaxConcurrentUsers(50);
        systemProperties.setInitStateFile("classpath:init-state.json");
        systemProperties.setAccessCodeChars("ABCDEFGHJKMNPQRSTUVWXYZ23456789");
        systemProperties.setAccessCodeLength(6);
        systemProperties.setTokenExpirationHours(24);
        systemProperties.setRetryCount(50);
        systemProperties.setRetryJitterMaxMs(50);

        return systemProperties;
    }

    private UserDTO createValidDTO() {
        return new UserDTO(
                "yarin@bgu.ac.il", "Yarin", "Levi", "Password123!",
                15, 5, 2000, "Beer Sheva", "050-123-4567"
        );
    }

    private UserDTO createValidDTO(String email) {
        return new UserDTO(
                email, "Test", "User", "Password123!",
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
        String uuid1 = guest1.getValue().getToken();

        assertEquals(2, WebQueue.getInstance().getWaitingCount());
        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<NotifyDTO> receivedNotif = new java.util.concurrent.atomic.AtomicReference<>();

        com.vaadin.flow.shared.Registration registration = Broadcaster.registerTab(uuid1, notification -> {
            receivedNotif.set(notification);
            latch.countDown();
        });

        // Act
        userService.logout(activeToken);

        // Assert
        assertEquals(1, WebQueue.getInstance().getActiveCount(), "System should be full again");
        assertEquals(1, WebQueue.getInstance().getWaitingCount(), "Only one guest should remain in the queue");

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Tab should have received the broadcast within 2 seconds");
        assertNotNull(receivedNotif.get());
        assertEquals(NotifyType.QUEUE_WEB_TURN_ARRIVED, receivedNotif.get().getType());

        registration.remove();
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
    @Test
    void GivenNullOrBlankEmail_WhenGetOrCleanDelayedNotifications_ThenErrorInvalidEmail() {
        // GET Tests
        Response<List<DTO.NotifyDTO>> getNull = userService.getDelayedNotifications(null);
        Response<List<DTO.NotifyDTO>> getBlank = userService.getDelayedNotifications("   ");
        assertTrue(getNull.isError());
        assertEquals("Invalid email address", getNull.getMessage());
        assertTrue(getBlank.isError());

        // CLEAN Tests
        Response<Boolean> cleanNull = userService.cleanDelayedNotifications(null);
        Response<Boolean> cleanBlank = userService.cleanDelayedNotifications("   ");
        assertTrue(cleanNull.isError());
        assertEquals("Invalid email address", cleanNull.getMessage());
        assertTrue(cleanBlank.isError());
    }

    @Test
    void GivenValidEmailWithRealDelayedNotifications_WhenGetAndClean_ThenFlowSucceeds() {
        // Arrange
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        String email = dto.getEmail();
        int targetUserId = userRepo.findUserByEmail(email).getUserId();

        // Arrange
        String ownerEmail = "owner_trigger1@test.com";
        userService.registerUser(null, new UserDTO(ownerEmail, "O", "W", "pass", 1, 1, 2000, "City", "050-000-0000"));
        String ownerToken = userService.login(ownerEmail, "pass").getValue();
        int compId = 1500;
        companyService.createProductionCompany(ownerToken, compId, "Trigger Co 1", "trig1@co.com", "050-111-1111", "bank");

        companyService.requestAppointManager(ownerToken, compId, targetUserId, EnumSet.of(PermissionType.CREATE_EVENT));
        companyService.requestAppointOwner(ownerToken, compId, targetUserId);

        assertEquals(2, userRepo.findUserByEmail(email).getPendingNotifications().size());

        Response<List<NotifyDTO>> getResponse = userService.getDelayedNotifications(email);

        // Assert 1
        assertNotNull(getResponse.getValue());
        assertEquals(2, getResponse.getValue().size(), "UI should receive 2 notifications");
        assertFalse(userRepo.findUserByEmail(email).getPendingNotifications().isEmpty());

        Response<Boolean> cleanResponse = userService.cleanDelayedNotifications(email);

        // Assert 2
        assertTrue(cleanResponse.getValue());
        assertTrue(userRepo.findUserByEmail(email).getPendingNotifications().isEmpty(), "DB should be empty after clean");
    }

    @Test
    void GivenOfflineUser_WhenNotifiedAndLogsIn_ThenNotificationsFlowWorks() {
        // Arrange
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        String email = dto.getEmail();
        int targetUserId = userRepo.findUserByEmail(email).getUserId();

        String ownerEmail = "owner_trigger2@test.com";
        userService.registerUser(null, new UserDTO(ownerEmail, "O", "W", "pass", 1, 1, 2000, "City", "050-000-0000"));
        String ownerToken = userService.login(ownerEmail, "pass").getValue();
        int compId = 1501;
        companyService.createProductionCompany(ownerToken, compId, "Trigger Co 2", "trig2@co.com", "050-111-1111", "bank");

        companyService.requestAppointOwner(ownerToken, compId, targetUserId);

        // Act & Assert
        Response<String> loginResponse = userService.login(email, "Password123!");
        assertNotNull(loginResponse.getValue(), "Login should succeed");

        Response<List<NotifyDTO>> getResponse = userService.getDelayedNotifications(email);
        assertEquals(1, getResponse.getValue().size());

        Response<Boolean> cleanResponse = userService.cleanDelayedNotifications(email);
        assertTrue(cleanResponse.getValue());

        assertTrue(userRepo.findUserByEmail(email).getPendingNotifications().isEmpty());
    }

    @Test
    void GivenSameUser_WhenConcurrentCleanNotifications_ThenHandledSafelyWithoutCrashing() throws Exception {
        // Arrange
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        String email = dto.getEmail();

        notifier.notifyUser(email, new DTO.NotifyDTO(
                DTO.NotifyType.GENERAL_POPUP,
                new DTO.NotifyPayload("Spam message")
        ));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        // Act
        Future<Response<Boolean>> future1 = executor.submit(() -> {
            start.await();
            return userService.cleanDelayedNotifications(email);
        });
        Future<Response<Boolean>> future2 = executor.submit(() -> {
            start.await();
            return userService.cleanDelayedNotifications(email);
        });

        start.countDown();
        Response<Boolean> res1 = future1.get();
        Response<Boolean> res2 = future2.get();
        executor.shutdown();

        // Assert
        boolean success1 = res1.getValue() != null && res1.getValue();
        boolean success2 = res2.getValue() != null && res2.getValue();

        assertTrue(success1 || success2, "At least one thread should successfully process the cleaning");
        assertTrue(userRepo.findUserByEmail(email).getPendingNotifications().isEmpty());
    }

    @Test
    void GivenUserLoggingIn_WhenConcurrentNotificationSent_ThenNoDataLost() throws Exception {
        // Arrange
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        String email = dto.getEmail();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        // Act
        Future<Response<Boolean>> cleanFuture = executor.submit(() -> {
            start.await();
            return userService.cleanDelayedNotifications(email);
        });

        Future<Void> notifyFuture = executor.submit(() -> {
            start.await();
            DTO.NotifyDTO concurrentNotification = new DTO.NotifyDTO(
                    DTO.NotifyType.GENERAL_POPUP,
                    new DTO.NotifyPayload("System update!")
            );
            notifier.notifyUser(email, concurrentNotification);
            return null;
        });

        start.countDown();

        Response<Boolean> cleanRes = cleanFuture.get();
        notifyFuture.get();
        executor.shutdown();

        // Assert
        assertNotNull(cleanRes, "Service should return a structured response, not throw an unhandled exception");
        Member memberAfterChaos = userRepo.findUserByEmail(email);
        assertNotNull(memberAfterChaos);
        assertNotNull(memberAfterChaos.getPendingNotifications());
    }

    @Test
    void GivenUserWithDelayedNotifications_WhenAdminRemovesUser_ThenLoginFailsAndDataInaccessible() {
        // Arrange
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        String email = dto.getEmail();
        int targetUserId = userRepo.findUserByEmail(email).getUserId();

        String ownerEmail = "owner_trigger3@test.com";
        userService.registerUser(null, new UserDTO(ownerEmail, "O", "W", "pass", 1, 1, 2000, "City", "050-000-0000"));
        String ownerToken = userService.login(ownerEmail, "pass").getValue();
        int compId = 1502;
        companyService.createProductionCompany(ownerToken, compId, "Trigger Co 3", "trig3@co.com", "050-111-1111", "bank");

        companyService.requestAppointOwner(ownerToken, compId, targetUserId);
        assertFalse(userRepo.findUserByEmail(email).getPendingNotifications().isEmpty());

        // Act
        Response<Boolean> adminRes = adminService.removeUser(ADMIN_TOKEN, targetUserId);
        assertTrue(adminRes.getValue(), "Admin should successfully remove the user");

        // Assert
        Response<String> loginResponse = userService.login(email, "Password123!");
        assertTrue(loginResponse.isError());
        assertNull(loginResponse.getValue(), "Blocked user should not get a token");
    }

    @Test
    void GivenUsersInQueue_WhenGuestLeavesStore_ThenNextInLineNotifiedViaTab() throws Exception {
        // Arrange
        WebQueue.resetForTesting();
        WebQueue.getInstance(1);
        userService.enter();
        String guestToken1 = userService.continueAsGuest().getValue();

        Response<QueueEntryResultDTO> guest2 = userService.enter();
        String uuid2 = guest2.getValue().getToken();

        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<NotifyDTO> receivedNotif = new java.util.concurrent.atomic.AtomicReference<>();
        com.vaadin.flow.shared.Registration registration = Broadcaster.registerTab(uuid2, notification -> {
            receivedNotif.set(notification);
            latch.countDown();
        });

        // Act
        userService.leaveStore(guestToken1);

        // Assert
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Tab 2 should have received the broadcast");
        assertNotNull(receivedNotif.get());
        assertEquals(NotifyType.QUEUE_WEB_TURN_ARRIVED, receivedNotif.get().getType());
        assertEquals(1, WebQueue.getInstance().getActiveCount());

        registration.remove();
    }

    @Test
    void GivenMultipleQueuedUsers_WhenActiveUsersLeave_ThenNotifiedInFIFOOrder() throws Exception {
        // Arrange
        WebQueue.resetForTesting();
        WebQueue.getInstance(2);
        userService.enter();
        String token1 = userService.continueAsGuest().getValue();
        userService.enter();
        String token2 = userService.continueAsGuest().getValue();

        String uuid3 = userService.enter().getValue().getToken();
        String uuid4 = userService.enter().getValue().getToken();

        CountDownLatch latch3 = new CountDownLatch(1);
        CountDownLatch latch4 = new CountDownLatch(1);
        com.vaadin.flow.shared.Registration reg3 = Broadcaster.registerTab(uuid3, n -> latch3.countDown());
        com.vaadin.flow.shared.Registration reg4 = Broadcaster.registerTab(uuid4, n -> latch4.countDown());

        // Act 1
        userService.leaveStore(token1);

        // Assert 1
        assertTrue(latch3.await(2, TimeUnit.SECONDS), "Tab 3 must be notified first");
        assertEquals(1, latch4.getCount(), "Tab 4 must NOT be notified yet");

        // Act 2
        userService.leaveStore(token2);

        // Assert 2
        assertTrue(latch4.await(2, TimeUnit.SECONDS), "Tab 4 must be notified second");

        reg3.remove();
        reg4.remove();
    }
    @Test
    void GivenWaitingUser_WhenPollingQueueStatus_ThenStatusReturnedButNoNotificationSent() throws Exception {
        // Arrange
        WebQueue.resetForTesting();
        WebQueue.getInstance(1);
        userService.enter();

        Response<QueueEntryResultDTO> queuedUser = userService.enter();
        String uuid = queuedUser.getValue().getToken();
        CountDownLatch latch = new CountDownLatch(1);
        com.vaadin.flow.shared.Registration registration = Broadcaster.registerTab(uuid, n -> latch.countDown());

        // Act
        Response<QueueEntryResultDTO> status = userService.getQueueStatus(uuid);

        // Assert
        assertFalse(status.getValue().isAdmitted(), "User should still be waiting");

        boolean notificationFired = latch.await(500, TimeUnit.MILLISECONDS);
        assertFalse(notificationFired, "Polling getQueueStatus must NOT trigger a WebSocket notification");

        registration.remove();
    }
    @Test
    void GivenEmptySystem_WhenUserEntersImmediately_ThenNoTurnArrivedNotificationSent() throws Exception {
        // Arrange
        WebQueue.resetForTesting();
        WebQueue.getInstance(10);

        String prospectiveUuid = UUID.randomUUID().toString(); // effective id for testing
        CountDownLatch latch = new CountDownLatch(1);
        com.vaadin.flow.shared.Registration registration = Broadcaster.registerTab(prospectiveUuid, n -> latch.countDown());

        // Act
        Response<QueueEntryResultDTO> response = userService.enter();

        // Assert
        assertTrue(response.getValue().isAdmitted(), "User should be admitted immediately");

        boolean notificationFired = latch.await(500, TimeUnit.MILLISECONDS);
        assertFalse(notificationFired, "Immediately admitted users should NOT receive a 'Turn Arrived' push notification");

        registration.remove();
    }

    @Test
    void GivenNotifierThrowsException_WhenUserAdmittedFromQueue_ThenExceptionCaughtAndUserRemainsAdmitted() {

        // Arrange
        WebQueue.resetForTesting();
        WebQueue.getInstance(1);

        INotifier mockNotifier = Mockito.mock(INotifier.class);
        UserService trickyService = new UserService(realTokenService, auth, userRepo, passwordEncoder, mockNotifier, transactionTemplate);

        trickyService.enter();
        String activeToken = trickyService.continueAsGuest().getValue();

        Response<QueueEntryResultDTO> queuedUserResponse = trickyService.enter();
        assertFalse(queuedUserResponse.getValue().isAdmitted(), "User should be in queue");
        String waitingUuid = queuedUserResponse.getValue().getToken();

        Mockito.doThrow(new RuntimeException("Simulated Tab Notification Crash"))
                .when(mockNotifier).notifyTab(Mockito.eq(waitingUuid), Mockito.any(NotifyDTO.class));

        // Act
        Response<Boolean> response = trickyService.leaveStore(activeToken);

        // Assert
        assertTrue(response.getValue(), "leaveStore should succeed even if the notification to the next user fails");

        // Assert
        assertEquals(0, WebQueue.getInstance().getWaitingCount(), "The queue should be empty because the user was promoted");
        assertEquals(1, WebQueue.getInstance().getActiveCount(), "The promoted user should take the active spot");
    }

    @Test
    void GivenExpiredToken_WhenGetUserId_ThenTokenExpiredNotificationSent() {
        // המטרה: לוודא שברגע שיש ניסיון שימוש בטוקן פג תוקף בפונקציית getUserId, נשלחת התראת TOKEN_EXPIRED ל-TAB של המשתמש.

        // Arrange
        String email = "expired_getid@test.com";
        userService.registerUser(null, new UserDTO(email, "F", "L", "pass", 1, 1, 2000, "Israel", "050-123-4567"));

        INotifier mockNotifier = Mockito.mock(INotifier.class);
        UserService mockService = new UserService(realTokenService, auth, userRepo, passwordEncoder, mockNotifier, transactionTemplate);

        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.expired_token_signature_mock";

        // Act
        Response<Integer> response = mockService.getUserId(expiredToken);

        // Assert
        assertEquals(-1, response.getValue());

        // Assert
        Mockito.verify(mockNotifier, Mockito.times(1)).notifyTab(
                Mockito.eq(expiredToken),
                Mockito.argThat(notification -> notification.getType() == NotifyType.TOKEN_EXPIRED)
        );
    }

    @Test
    void GivenNotifierThrowsException_WhenSendingTokenExpiredNotification_ThenExceptionCaughtAndSystemContinues() {

        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.expired_token_signature_mock2";

        INotifier mockNotifier = Mockito.mock(INotifier.class);
        UserService mockService = new UserService(realTokenService, auth, userRepo, passwordEncoder, mockNotifier, transactionTemplate);

        Mockito.doThrow(new RuntimeException("Crash during TOKEN_EXPIRED notification"))
                .when(mockNotifier).notifyTab(Mockito.anyString(), Mockito.any(NotifyDTO.class));

        // Act
        Response<Integer> response = mockService.getUserId(expiredToken);

        // Assert
        assertEquals(-1, response.getValue());
    }
    @Test
    void GivenSystemFull_WhenActiveUserLogouts_ThenNextInLineNotifiedViaMockNotifier() {
        WebQueue.resetForTesting();
        WebQueue.getInstance(1);

        INotifier mockNotifier = Mockito.mock(INotifier.class);
        UserService mockService = new UserService(realTokenService, auth, userRepo, passwordEncoder, mockNotifier, transactionTemplate);

        String email = "active_queue@mail.com";
        mockService.registerUser(null, new UserDTO(email, "F", "L", "Pass!", 1, 1, 2000, "A", "050-000-0000"));
        mockService.enter();
        String activeToken = mockService.login(email, "Pass!").getValue();

        Response<QueueEntryResultDTO> queuedUser = mockService.enter();
        String waitingUuid = queuedUser.getValue().getToken();

        mockService.logout(activeToken);

        org.mockito.ArgumentCaptor<NotifyDTO> captor = org.mockito.ArgumentCaptor.forClass(NotifyDTO.class);
        Mockito.verify(mockNotifier, Mockito.times(1)).notifyTab(Mockito.eq(waitingUuid), captor.capture());
        assertEquals(NotifyType.QUEUE_WEB_TURN_ARRIVED, captor.getValue().getType());
    }

    @Test
    void GivenSystemFull_WhenGuestLeavesStore_ThenNextInLineNotifiedViaMockNotifier() {
        WebQueue.resetForTesting();
        WebQueue.getInstance(1);

        INotifier mockNotifier = Mockito.mock(INotifier.class);
        UserService mockService = new UserService(realTokenService, auth, userRepo, passwordEncoder, mockNotifier, transactionTemplate);

        mockService.enter();
        String guestToken = mockService.continueAsGuest().getValue();

        Response<QueueEntryResultDTO> queuedUser = mockService.enter();
        String waitingUuid = queuedUser.getValue().getToken();

        mockService.leaveStore(guestToken);

        org.mockito.ArgumentCaptor<NotifyDTO> captor = org.mockito.ArgumentCaptor.forClass(NotifyDTO.class);
        Mockito.verify(mockNotifier, Mockito.times(1)).notifyTab(Mockito.eq(waitingUuid), captor.capture());
        assertEquals(NotifyType.QUEUE_WEB_TURN_ARRIVED, captor.getValue().getType());
    }

    // --- getUserIdentifier ---

    @Test
    void GivenNullOrBlankToken_WhenGetUserIdentifier_ThenInvalidToken() {
        Response<String> responseNull = userService.getUserIdentifier(null);
        Response<String> responseBlank = userService.getUserIdentifier("   ");

        assertNull(responseNull.getValue());
        assertEquals("Invalid token", responseNull.getMessage());
        assertNull(responseBlank.getValue());
        assertEquals("Invalid token", responseBlank.getMessage());
    }

    @Test
    void GivenLoggedInMember_WhenGetUserIdentifier_ThenReturnsEmail() {
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        String token = userService.login(dto.getEmail(), dto.getPassword()).getValue();

        Response<String> response = userService.getUserIdentifier(token);

        assertEquals(dto.getEmail(), response.getValue());
    }

    @Test
    void GivenMalformedToken_WhenGetUserIdentifier_ThenNoIdentifierReturned() {
        // A non-blank but unparseable token: passes the null/blank guard, but the
        // identifier cannot be extracted -> error response with null value.
        Response<String> response = userService.getUserIdentifier("malformed.jwt.token");

        assertNull(response.getValue());
        assertTrue(response.isError());
    }

    // --- getUserDisplayName ---

    @Test
    void GivenExistingUser_WhenGetUserDisplayName_ThenReturnsFullName() {
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        int userId = userRepo.findUserByEmail(dto.getEmail()).getUserId();

        String displayName = userService.getUserDisplayName(userId);

        assertEquals("Yarin Levi", displayName);
    }

    @Test
    void GivenNonExistingUser_WhenGetUserDisplayName_ThenReturnsFallback() {
        String displayName = userService.getUserDisplayName(99999);

        assertEquals("User #99999", displayName);
    }

    // --- exitQueue ---

    @Test
    void GivenNullOrBlankToken_WhenExitQueue_ThenInvalidToken() {
        Response<Boolean> responseNull = userService.exitQueue(null);
        Response<Boolean> responseBlank = userService.exitQueue("   ");

        assertFalse(responseNull.getValue());
        assertEquals("Invalid token", responseNull.getMessage());
        assertFalse(responseBlank.getValue());
        assertEquals("Invalid token", responseBlank.getMessage());
    }

    @Test
    void GivenWaitingUser_WhenExitQueue_ThenRemoved() {
        WebQueue.resetForTesting();
        WebQueue.getInstance(1);
        userService.enter(); // fills the only slot
        String waitingUuid = userService.enter().getValue().getToken(); // queued

        Response<Boolean> response = userService.exitQueue(waitingUuid);

        assertTrue(response.getValue());
        assertEquals("User removed from queue", response.getMessage());
    }

    @Test
    void GivenTokenNotInQueue_WhenExitQueue_ThenNotFound() {
        Response<Boolean> response = userService.exitQueue("not-a-queued-token");

        assertFalse(response.getValue());
        assertEquals("Token not found in waiting queue", response.getMessage());
    }

    // --- getUserId ---

    @Test
    void GivenNullToken_WhenGetUserId_ThenMissing() {
        Response<Integer> response = userService.getUserId(null);

        assertEquals(-1, response.getValue());
        assertEquals("Token is missing", response.getMessage());
    }

    @Test
    void GivenGuestToken_WhenGetUserId_ThenGuestRecognized() {
        String guestToken = userService.continueAsGuest().getValue();

        Response<Integer> response = userService.getUserId(guestToken);

        assertEquals(-1, response.getValue());
        assertEquals("Guest token recognized", response.getMessage());
    }

    @Test
    void GivenLoggedInMember_WhenGetUserId_ThenReturnsMemberId() {
        UserDTO dto = createValidDTO();
        userService.registerUser(null, dto);
        String token = userService.login(dto.getEmail(), dto.getPassword()).getValue();
        int expectedId = userRepo.findUserByEmail(dto.getEmail()).getUserId();

        Response<Integer> response = userService.getUserId(token);

        assertEquals(expectedId, response.getValue());
        assertEquals("Retrieved member ID", response.getMessage());
    }

    // --- delayed notifications: user not found ---

    @Test
    void GivenUnknownEmail_WhenGetDelayedNotifications_ThenUserNotFound() {
        Response<List<NotifyDTO>> response = userService.getDelayedNotifications("ghost@nowhere.com");

        assertNull(response.getValue());
        assertEquals("User not found", response.getMessage());
    }

    @Test
    void GivenUnknownEmail_WhenCleanDelayedNotifications_ThenUserNotFound() {
        Response<Boolean> response = userService.cleanDelayedNotifications("ghost@nowhere.com");

        assertFalse(response.getValue());
        assertEquals("User not found", response.getMessage());
    }

    // --- login edge cases ---

    @Test
    void GivenDeactivatedMember_WhenLogin_ThenBlockedByAdminError() {
        UserDTO dto = createValidDTO("blocked@mail.com");
        userService.registerUser(null, dto);
        Member m = userRepo.findUserByEmail("blocked@mail.com");
        m.deactivate();
        userRepo.store(m);

        Response<String> response = userService.login("blocked@mail.com", dto.getPassword());

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("blocked by Admin"));
    }

    // --- getUserId: member not in database ---

    @Test
    void GivenLoggedOutToken_WhenGetUserId_ThenNotLoggedIn() {
        UserDTO dto = createValidDTO("getuid_loggedout@mail.com");
        userService.registerUser(null, dto);
        String token = userService.login(dto.getEmail(), dto.getPassword()).getValue();
        userService.logout(token);

        Response<Integer> response = userService.getUserId(token);

        assertEquals(-1, response.getValue());
        assertNotNull(response.getMessage());
    }
}
