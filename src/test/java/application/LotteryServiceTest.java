package application;

import Log.LoggerSetup;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.mockito.Mockito;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

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
    private String validToken1;
    private String validToken2;
    private String validToken3;
    private IAuth auth;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private String invalidToken;
    private String notPermission;
    private EventCompanyManageService eventCompanyManageService;

    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
        userRepo = new UserRepo();
        passwordEncoder = new PasswordEncoderUtil();
        tokenService = new TokenService();
        auth = new Auth(tokenService, userRepo, passwordEncoder);

        CompanyRepoImpl companyRepo = new CompanyRepoImpl();
        eventRepo = new EventRepoImpl();
        lotteryRepo = new LotteryRepoImpl();

        IPaymentSystem paymentSystem = Mockito.mock(IPaymentSystem.class);

        UserService userService = new UserService(tokenService, auth, userRepo, passwordEncoder);
        CompanyService companyService = new CompanyService(auth, companyRepo, userRepo);
         eventCompanyManageService =
                new EventCompanyManageService(companyRepo, eventRepo, auth, paymentSystem);

        lotteryService = new LotteryService(lotteryRepo, eventRepo, auth);

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
    void GivenUserNotCompanyOwner_WhenCreateLottery_ThenPermissionErrorIsReturned() {
        // Arrange: Use an unauthorized user ID
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

        // Act
        Response<Boolean> response = lotteryService.createLottery(
                notPermission, eventId, 50, lotteryDate_X, (long) 24.0
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("User id mismatch to the creator of this event", response.getMessage());
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
    void GivenMoreRegistrationsThanCapacity_WhenDrawLotteryIsTriggered_ThenWinnersAreSelectedUpToCapacity() {
        // Arrange: Create an event and a lottery with a capacity of 2
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
        lotteryService.createLottery(validToken, eventId, 2, lotteryDate_X, (long) 24.0);

        lotteryService.registerUserToLottery(validToken, eventId);
        lotteryService.registerUserToLottery(validToken1, eventId);
        lotteryService.registerUserToLottery(validToken2, eventId);
        lotteryService.registerUserToLottery(validToken3, eventId);
        // Retrieve the newly created lottery to simulate users registering
        Lottery lottery = lotteryRepo.getAll().get(0);

        // Act: Manually trigger the draw (simulating the scheduler waking up)
        lotteryService.drawLottery(lottery.getId());

        // Then: Fetch the updated lottery and verify
        Lottery updatedLottery = lotteryRepo.findById(lottery.getId());
        assertEquals(2, updatedLottery.getWinners().size(), "Should only have exactly 2 winners");

        // Verify winners are from the registered list
        assertTrue(lottery.getRegistered().containsAll(updatedLottery.getWinners()), "Winners must be among those who registered");
    }

    @Test
    void GivenFewerRegistrationsThanCapacity_WhenDrawLotteryIsTriggered_ThenAllRegisteredWin() {
        // Arrange: Create an event and a lottery with a capacity of 10
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
        lotteryService.createLottery(validToken, eventId, 10, lotteryDate_X, (long) 24.0);

        Lottery lottery = lotteryRepo.getAll().get(0);
        lotteryService.registerUserToLottery(validToken, eventId);
        lotteryService.registerUserToLottery(validToken1, eventId);
        lotteryService.registerUserToLottery(validToken2, eventId);

        // Act: Manually trigger the draw
        lotteryService.drawLottery(lottery.getId());

        // Then: Verify everyone won
        Lottery updatedLottery = lotteryRepo.findById(lottery.getId());
        assertEquals(3, updatedLottery.getWinners().size(), "All 3 registered users should win");
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

        UserService userService = new UserService(tokenService, auth, userRepo, passwordEncoder);
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