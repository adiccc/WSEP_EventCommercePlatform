package application;

import DTO.NotifyDTO;
import Log.LoggerSetup;
import com.vaadin.flow.shared.Registration;
import domain.Suspension.ISuspensionRepo;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dto.UserDTO;
import domain.event.Event;
import domain.lottery.Lottery;
import domain.user.IUserRepo;
import domain.user.Member;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import Exception.OptimisticLockingFailureException;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.mockito.Mockito;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class LotteryServiceTest {

    private final int companyId = 111;
    private String creatorId1;
    private String creatorId2;

    private TokenService tokenService;
    private EventRepoImpl eventRepo;
    private LotteryRepoImpl lotteryRepo;
    private LotteryService lotteryService;

    private LocalDateTime saleStartDate_Y;
    private Integer eventId;

    private String validToken;
    private String validToken2;
    private String validToken3;
    private IAuth auth;
    private IAccessValidator accessValidator;
    private ISuspensionRepo suspensionRepo;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private String invalidToken;
    private String notPermission;
    private EventCompanyManageService eventCompanyManageService;
    private INotifier notifier;

    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
        userRepo = new UserRepo();
        passwordEncoder = new PasswordEncoderUtil();
        tokenService = new TokenService();
        auth = new Auth(tokenService, userRepo, passwordEncoder);
        suspensionRepo= new SuspensionRepoImpl();
        accessValidator=new AccessValidator(suspensionRepo);

        CompanyRepoImpl companyRepo = new CompanyRepoImpl();
        eventRepo = new EventRepoImpl();
        lotteryRepo = new LotteryRepoImpl();

        IPaymentSystem paymentSystem = Mockito.mock(IPaymentSystem.class);
        notifier = new VaadinNotifier(userRepo);

        UserService userService = new UserService(tokenService, auth, userRepo, passwordEncoder,notifier);
        CompanyService companyService = new CompanyService(auth, companyRepo, userRepo,accessValidator);
         eventCompanyManageService =
                new EventCompanyManageService(companyRepo, eventRepo, auth, paymentSystem,accessValidator,notifier);

        lotteryService = new LotteryService(lotteryRepo, eventRepo, auth, companyRepo,accessValidator,notifier);

        // user with permission
        UserDTO user1DTO = new UserDTO(
                "user1@test.com",
                "test1",
                "first",
                "test1",
                15,
                4,
                2002,
                "Omer",
                "050-427-3201"
        );

        userService.registerUser(null, user1DTO);
        validToken=userService.login("user1@test.com", "test1").getValue();
        creatorId1 = validToken;

        // user without permission
        UserDTO user2DTO = new UserDTO(
                "user2@test.com",
                "test2",
                "first",
                "test2",
                15,
                4,
                2002,
                "Omer",
                "050-427-3202"
        );

        userService.registerUser(null, user2DTO);
        notPermission=userService.login("user2@test.com", "test2").getValue();
        creatorId2 = notPermission;

        // logged out / invalid user
        invalidToken = null;

        companyService.createProductionCompany(
                validToken,
                companyId,
                "Test Company",
                "test@test.com",
                "0500000000",
                "bank-1"
        );

        saleStartDate_Y = LocalDateTime.now().plusDays(14);

        Response<Integer> eventResponse = eventCompanyManageService.createEvent(
                validToken,
                companyId,
                LocalDateTime.now().plusDays(30),
                "Lottery Event",
                saleStartDate_Y,
                true,
                GeographicalArea.CENTER,
                CategoryEvent.FESTIVAL
        );

        eventId = eventResponse.getValue();

        // more valid users

        // user with permission
        UserDTO userDTO2 = new UserDTO(
                "user11@test.com",
                "test1",
                "first",
                "test1",
                15,
                4,
                2002,
                "Omer",
                "050-427-3201"
        );

        userService.registerUser(null, userDTO2);
        validToken2=userService.login("user11@test.com", "test1").getValue();

        UserDTO userDTO3 = new UserDTO(
                "user12@test.com",
                "test1",
                "first",
                "test1",
                15,
                4,
                2002,
                "Omer",
                "050-427-3201"
        );

        userService.registerUser(null, userDTO3);
        validToken3=userService.login("user12@test.com", "test1").getValue();

    }

    // ==========================================
    // Tests for createLottery Use Case
    // ==========================================

    @Test
    void GivenValidInputs_WhenCreateLottery_ThenLotteryIsSuccessfullyCreated() {
        // Arrange: Date 'X' - Next week (before sales open)
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

        // Act
        Response<Boolean> response = lotteryService.createLottery(
                validToken, eventId, 50, lotteryDate_X, (long) 24.0
        );

        // Assert
        assertTrue(response.getValue());
        assertEquals("Lottery created successfully", response.getMessage());
        assertEquals(1, lotteryRepo.getAll().size(), "Lottery should be saved in the repository");
    }

    @Test
    void GivenInvalidOrMissingToken_WhenCreateLottery_ThenUserNotLoggedInErrorIsReturned() {
        // Arrange
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

        // Act
        Response<Boolean> response = lotteryService.createLottery(
                invalidToken, eventId, 50, lotteryDate_X, (long) 24.0
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenNonExistingEventId_WhenCreateLottery_ThenEventNotFoundErrorIsReturned() {
        // Arrange
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

        // Act
        Response<Boolean> response = lotteryService.createLottery(
                validToken, -1, 50, lotteryDate_X, (long) 24.0
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("event not found", response.getMessage());
    }

    @Test
    void GivenEventNotSupportingLottery_WhenCreateLottery_ThenLotteryNotSupportedErrorIsReturned() {
        // Arrange: Create an event that DOES NOT support lottery
        Integer eventId=eventCompanyManageService.createEvent(validToken,companyId,LocalDateTime.now().plusDays(30),"test-event-no-lottery",saleStartDate_Y,false,GeographicalArea.CENTER,CategoryEvent.FESTIVAL).getValue();
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

        // Act
        Response<Boolean> response = lotteryService.createLottery(
                validToken, eventId, 50, lotteryDate_X, (long) 24.0
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("This event does not support lottery", response.getMessage());
    }

    @Test
    void GivenLotteryDateInThePast_WhenCreateLottery_ThenInvalidDateErrorIsReturned() {
        // Arrange: Date 'Z' - A week ago
        LocalDateTime pastDate_Z = LocalDateTime.now().minusDays(7);

        // Act
        Response<Boolean> response = lotteryService.createLottery(
                validToken, eventId, 50, pastDate_Z, (long) 24.0
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("Register window must be in the future", response.getMessage());
    }

    @Test
    void GivenLotteryDateAfterSalesOpen_WhenCreateLottery_ThenInvalidDateErrorIsReturned() {
        // Arrange: Date 'T' - After sales open date (Y + 5 days)
        LocalDateTime lateDate_T = saleStartDate_Y.plusDays(5);

        // Act
        Response<Boolean> response = lotteryService.createLottery(
                validToken, eventId, 50, lateDate_T, (long) 24.0
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("Register window must be before sale start date", response.getMessage());
    }

    @Test
    void GivenFewerRegistrationsThanCapacity_WhenDrawLotteryIsTriggered_ThenAllRegisteredWin() throws InterruptedException {
        // Arrange: Create lottery with capacity 10
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
        lotteryService.createLottery(validToken, eventId, 10, lotteryDate_X, (long) 24.0);

        Lottery lottery = lotteryRepo.getAll().get(0);
        lotteryService.registerUserToLottery(validToken, eventId);
        lotteryService.registerUserToLottery(notPermission, eventId);
        lotteryService.registerUserToLottery(validToken2, eventId);

        // Arrange: Extract string identifiers for WebSocket listeners
        String id1 = auth.getUserIdentifier(validToken).getValue();
        String id2 = auth.getUserIdentifier(notPermission).getValue();
        String id3 = auth.getUserIdentifier(validToken2).getValue();

        // Arrange: Setup latches and references to catch the notifications
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);

        AtomicReference<NotifyDTO> notif1 = new AtomicReference<>();
        AtomicReference<NotifyDTO> notif2 = new AtomicReference<>();
        AtomicReference<NotifyDTO> notif3 = new AtomicReference<>();

        Registration reg1 = Broadcaster.registerUser(id1, dto -> { notif1.set(dto); latch1.countDown(); });
        Registration reg2 = Broadcaster.registerUser(id2, dto -> { notif2.set(dto); latch2.countDown(); });
        Registration reg3 = Broadcaster.registerUser(id3, dto -> { notif3.set(dto); latch3.countDown(); });

        try {
            // Act: Manually trigger the draw (simulating the scheduler)
            lotteryService.drawLottery(lottery.getId());

            // Assert: Verify DB state
            Lottery updatedLottery = lotteryRepo.findById(lottery.getId());
            assertEquals(3, updatedLottery.getWinners().size(), "All 3 registered users should win");

            // Assert: Verify all users received notifications within timeout
            assertTrue(latch1.await(2000, TimeUnit.MILLISECONDS), "User 1 did not receive notification");
            assertTrue(latch2.await(2000, TimeUnit.MILLISECONDS), "User 2 did not receive notification");
            assertTrue(latch3.await(2000, TimeUnit.MILLISECONDS), "User 3 did not receive notification");

            // Assert: Verify notification content contains the access code
            assertTrue(notif1.get().getPayload().getMessage().contains("Your code is: "));
            assertTrue(notif2.get().getPayload().getMessage().contains("Your code is: "));
            assertTrue(notif3.get().getPayload().getMessage().contains("Your code is: "));
        } finally {
            // Cleanup
            reg1.remove(); reg2.remove(); reg3.remove();
        }
    }

    @Test
    void GivenMoreRegistrationsThanCapacity_WhenDrawLotteryIsTriggered_ThenWinnersAreSelectedUpToCapacity() throws InterruptedException {
        // Arrange: Create a lottery with a capacity of 2
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
        lotteryService.createLottery(validToken, eventId, 2, lotteryDate_X, (long) 24.0);

        lotteryService.registerUserToLottery(validToken, eventId);
        lotteryService.registerUserToLottery(notPermission, eventId);
        lotteryService.registerUserToLottery(validToken2, eventId);
        lotteryService.registerUserToLottery(validToken3, eventId);

        Lottery lottery = lotteryRepo.getAll().get(0);

        // Arrange: Listen to all 4 users using a shared counter
        String id1 = auth.getUserIdentifier(validToken).getValue();
        String id2 = auth.getUserIdentifier(notPermission).getValue();
        String id3 = auth.getUserIdentifier(validToken2).getValue();
        String id4 = auth.getUserIdentifier(validToken3).getValue();

        AtomicInteger notificationCount = new AtomicInteger(0);

        Registration reg1 = Broadcaster.registerUser(id1, dto -> notificationCount.incrementAndGet());
        Registration reg2 = Broadcaster.registerUser(id2, dto -> notificationCount.incrementAndGet());
        Registration reg3 = Broadcaster.registerUser(id3, dto -> notificationCount.incrementAndGet());
        Registration reg4 = Broadcaster.registerUser(id4, dto -> notificationCount.incrementAndGet());

        try {
            // Act: Trigger the draw
            lotteryService.drawLottery(lottery.getId());

            // Assert: Verify DB
            Lottery updatedLottery = lotteryRepo.findById(lottery.getId());
            assertEquals(2, updatedLottery.getWinners().size(), "Should only have exactly 2 winners");

            // Wait briefly for background threads to deliver notifications
            Thread.sleep(1000);

            // Assert: Ensure exactly 2 notifications were dispatched overall across the 4 users
            assertEquals(2, notificationCount.get(), "Only the 2 winners should have received notifications");

        } finally {
            reg1.remove(); reg2.remove(); reg3.remove(); reg4.remove();
        }
    }

    @Test
    void GivenNonExistentLotteryId_WhenDrawLotteryIsTriggered_ThenItFailsGracefullyWithoutCrashing() {
        // Act & Assert
        // We call the service with a fake ID. If it crashes the test fails.
        // It should just log the error internally as per our implementation.
        assertDoesNotThrow(() -> lotteryService.drawLottery(-1),
                "The service should catch the exception and log it, not crash the system.");
    }

    @Test
    void GivenConcurrentDrawCalls_WhenExecuted_ThenDrawnOnceAndNotificationsSentExactlyOnce() throws Exception {
        // Arrange: Setup lottery with capacity 10 and 2 registered users
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
        lotteryService.createLottery(validToken, eventId, 10, lotteryDate_X, 24L);

        Lottery lottery = lotteryRepo.getAll().get(0);
        lotteryService.registerUserToLottery(validToken, eventId);
        lotteryService.registerUserToLottery(notPermission, eventId);

        String id1 = auth.getUserIdentifier(validToken).getValue();
        String id2 = auth.getUserIdentifier(notPermission).getValue();

        AtomicInteger notificationCount = new AtomicInteger(0);

        Registration reg1 = Broadcaster.registerUser(id1, dto -> notificationCount.incrementAndGet());
        Registration reg2 = Broadcaster.registerUser(id2, dto -> notificationCount.incrementAndGet());

        // Arrange: Prepare concurrent environment with a starting gate
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            // Act: Define the tasks to wait for the gate
            Callable<Void> concurrentTask = () -> {
                startGate.await();
                lotteryService.drawLottery(lottery.getId());
                return null;
            };

            Future<?> future1 = executor.submit(concurrentTask);
            Future<?> future2 = executor.submit(concurrentTask);

            // Both threads attack simultaneously
            startGate.countDown();

            // Wait for both to finish. We catch and ignore OptimisticLockingFailureException
            // because depending on CPU speed, it might or might not happen, and both are valid outcomes.
            try {
                future1.get();
            } catch (java.util.concurrent.ExecutionException e) {
                if (!(e.getCause() instanceof OptimisticLockingFailureException)) {
                    throw e; // Rethrow if it's a real unexpected bug
                }
            }

            try {
                future2.get();
            } catch (java.util.concurrent.ExecutionException e) {
                if (!(e.getCause() instanceof OptimisticLockingFailureException)) {
                    throw e; // Rethrow if it's a real unexpected bug
                }
            }

            // Assert: Verify DB state - Only 2 winners must exist
            Lottery updatedLottery = lotteryRepo.findById(lottery.getId());
            assertEquals(2, updatedLottery.getWinners().size(), "Company should have exactly 2 winners regardless of concurrency");

            // Wait briefly to allow background notification threads to finish
            Thread.sleep(1000);

            // Assert: Verify notification count 2 and not 4 (which would indicate both draws executed fully)
            assertEquals(2, notificationCount.get(), "Notifications should be sent exactly once per winner despite concurrent draw attempts");
        } finally {
            executor.shutdown();
            reg1.remove();
            reg2.remove();
        }
    }

    @Test
    void GivenLoggedInMemberAndLotteryEvent_WhenRegisterUserToLottery_ThenUserRegistered() {
        // Arrange
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

        lotteryService.createLottery(validToken, eventId, 5, lotteryDate_X, 24L);

        int userId = auth.getUserId(validToken).getValue();

        // Act
        Response<Boolean> response =
                lotteryService.registerUserToLottery(validToken, eventId);

        // Assert
        assertTrue(response.getValue());
        assertEquals("User registered to lottery successfully", response.getMessage());

        Lottery lottery = lotteryRepo.findById(eventId);
        assertTrue(lottery.getRegistered().contains(userId));
    }

    @Test
    void GivenNonExistingEvent_WhenRegisterUserToLottery_ThenEventNotFound() {
        // Arrange
        int nonExistingEventId = -1;

        // Act
        Response<Boolean> response =
                lotteryService.registerUserToLottery(validToken, nonExistingEventId);

        // Assert
        assertFalse(response.getValue());
        assertEquals("Event not found", response.getMessage());
    }

    @Test
    void GivenRegularEvent_WhenRegisterUserToLottery_ThenLotteryNotSupported() {
        // Arrange
        Integer noLotteryEventId = eventCompanyManageService.createEvent(
                validToken,
                companyId,
                LocalDateTime.now().plusDays(30),
                "No Lottery Event",
                saleStartDate_Y,
                false,
                GeographicalArea.CENTER,
                CategoryEvent.FESTIVAL
        ).getValue();

        // Act
        Response<Boolean> response =
                lotteryService.registerUserToLottery(validToken, noLotteryEventId);

        // Assert
        assertFalse(response.getValue());
        assertEquals("This event does not support lottery", response.getMessage());
    }

    @Test
    void GivenLoggedOutUser_WhenRegisterUserToLottery_ThenErrorReturned() {
        // Arrange
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
        lotteryService.createLottery(validToken, eventId, 5, lotteryDate_X, 24L);

        // Act
        Response<Boolean> response =
                lotteryService.registerUserToLottery(null, eventId);

        // Assert
        assertFalse(response.getValue());
        assertEquals("User is not logged in", response.getMessage());
    }

    @Test
    void GivenAlreadyRegisteredMember_WhenRegisterUserToLottery_ThenDuplicateRegistrationError() {
        // Arrange
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
        lotteryService.createLottery(validToken, eventId, 5, lotteryDate_X, 24L);

        // first registration
        Response<Boolean> first =
                lotteryService.registerUserToLottery(validToken, eventId);

        assertTrue(first.getValue());

        // Act – second registration trial
        Response<Boolean> second =
                lotteryService.registerUserToLottery(validToken, eventId);

        // Assert
        assertFalse(second.getValue());
        assertEquals("User is already registered to this lottery", second.getMessage());
    }
    @Test
    void GivenExpiredLotteryRegistration_WhenRegisterUserToLottery_ThenRegistrationExpiredError() {
        // Arrange
        LocalDateTime pastWindow = LocalDateTime.now().minusDays(1);

        Lottery expiredLottery = new Lottery(eventId, 5, pastWindow, 24L);
        lotteryRepo.store(expiredLottery);

        // Act
        Response<Boolean> response =
                lotteryService.registerUserToLottery(validToken, eventId);

        // Assert
        assertFalse(response.getValue());
        assertEquals("Lottery registration period has expired", response.getMessage());
    }

    @Test
    void GivenEventWithLotteryPolicyButNoLottery_WhenRegisterUserToLottery_ThenLotteryNotFound() {
        // Arrange

        // Act
        Response<Boolean> response =
                lotteryService.registerUserToLottery(validToken, eventId);

        // Assert
        assertFalse(response.getValue());
        assertEquals("Lottery not found", response.getMessage());
    }

    @Test
    void GivenTenDifferentUsersRegisterConcurrently_WhenRegisterUserToLottery_ThenAllUsersRegistered() throws Exception {
        // Arrange
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

        Response<Boolean> createLotteryResponse =
                lotteryService.createLottery(validToken, eventId, 3, lotteryDate_X, 24L);

        assertTrue(Boolean.TRUE.equals(createLotteryResponse.getValue()),
                "Lottery creation failed: " + createLotteryResponse.getMessage());

        int usersCount = 10;

        UserService userService = new UserService(tokenService, auth, userRepo, passwordEncoder,notifier);
        List<String> tokens = new ArrayList<>();

        for (int i = 0; i < usersCount; i++) {
            String email = "lottery_concurrent_user_" + i + "@test.com";

            Response<Boolean> registerResponse = userService.registerUser(null, new UserDTO(
                    email,
                    "first" + i,
                    "last" + i,
                    "pass",
                    1,
                    1,
                    2000,
                    "city",
                    "050-123-45" + String.format("%02d", i)
            ));

            assertTrue(Boolean.TRUE.equals(registerResponse.getValue()),
                    "User registration failed: " + registerResponse.getMessage());

            String token = userService.login(email, "pass").getValue();

            assertNotNull(token, "Login failed for user " + i);

            tokens.add(token);
        }

        ExecutorService executor = Executors.newFixedThreadPool(usersCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response<Boolean>>> futures = new ArrayList<>();

        // Act
        for (String token : tokens) {
            futures.add(executor.submit(() -> {
                start.await();
                return lotteryService.registerUserToLottery(token, eventId);
            }));
        }

        start.countDown();

        int successfulRegistrations = 0;

        for (Future<Response<Boolean>> future : futures) {
            Response<Boolean> response = future.get();

            if (Boolean.TRUE.equals(response.getValue())) {
                successfulRegistrations++;
            } else {
                fail("Registration failed unexpectedly: " + response.getMessage());
            }
        }

        executor.shutdown();

        // Assert
        Lottery lottery = lotteryRepo.findById(eventId);

        assertEquals(usersCount, successfulRegistrations);
        assertEquals(usersCount, lottery.getRegistered().size());
    }



}