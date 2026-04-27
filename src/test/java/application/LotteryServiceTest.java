package application;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.mockito.Mockito;

class LotteryServiceTest {

    private final int companyId = 111;
    private String creatorId1;
    private String creatorId2;

    private TokenService tokenService;
    private EventRepoImpl eventRepo;
    private LotteryRepoImpl lotteryRepo;
    private LotteryService lotteryService;

    private LocalDateTime saleStartDate_Y;
    private String eventId;

    private String validToken;
    private IAuth auth;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private String invalidToken;
    private String notPermission;
    private EventCompanyManageService eventCompanyManageService;

    @BeforeEach
    void setUp() {
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

        Response<String> eventResponse = eventCompanyManageService.createEvent(
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
                validToken, "non-existing-event-id", 50, lotteryDate_X, (long) 24.0
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("event not found", response.getMessage());
    }

    @Test
    void GivenEventNotSupportingLottery_WhenCreateLottery_ThenLotteryNotSupportedErrorIsReturned() {
        // Arrange: Create an event that DOES NOT support lottery
        String eventId=eventCompanyManageService.createEvent(validToken,companyId,LocalDateTime.now().plusDays(30),"test-event-no-lottery",saleStartDate_Y,false,GeographicalArea.CENTER,CategoryEvent.FESTIVAL).getValue();
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

        // TODO: use service function to registerate users to the lottery
        // Retrieve the newly created lottery to simulate users registering
        Lottery lottery = lotteryRepo.getAll().get(0);
        lottery.getRegistered().addAll(List.of(101, 102, 103, 104)); // 4 users register
        lotteryRepo.store(lottery); // Save state

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

        // TODO: use service function to registerate users to the lottery
        Lottery lottery = lotteryRepo.getAll().get(0);
        lottery.getRegistered().addAll(List.of(101, 102, 103)); // Only 3 users register
        lotteryRepo.store(lottery);

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
        assertDoesNotThrow(() -> lotteryService.drawLottery("non-existent-lottery-id"),
                "The service should catch the exception and log it, not crash the system.");
    }

}