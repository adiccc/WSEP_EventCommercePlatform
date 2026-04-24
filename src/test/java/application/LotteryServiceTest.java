package application;

import domain.company.Company;
import domain.event.Event;
import domain.lottery.Lottery;
import infrastructure.CompanyRepoImpl;
import infrastructure.EventRepoImpl;
import infrastructure.LotteryRepoImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LotteryServiceTest {

    private final int companyId = 111;
    private final int creatorId = 789;

    private TokenService tokenService;
    private EventRepoImpl eventRepo;
    private LotteryRepoImpl lotteryRepo;
    private LotteryService lotteryService;

    private String validToken;
    private LocalDateTime saleStartDate_Y;
    private Event eventWithLottery;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        validToken = tokenService.generateToken("user-789");

        eventRepo = new EventRepoImpl();
        lotteryRepo = new LotteryRepoImpl();
        lotteryService = new LotteryService(lotteryRepo, eventRepo, tokenService);

        // Date 'Y': Sale start date is 14 days from now
        saleStartDate_Y = LocalDateTime.now().plusDays(14);

        // Create an event that supports lottery
        eventWithLottery = new Event(companyId, creatorId, LocalDateTime.now().plusDays(30), "Lottery Event", saleStartDate_Y, true);
        eventRepo.store(eventWithLottery);
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
                validToken, creatorId, eventWithLottery.getId(), 50, lotteryDate_X, 24.0
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
        String invalidToken = "";

        // Act
        Response<Boolean> response = lotteryService.createLottery(
                invalidToken, creatorId, eventWithLottery.getId(), 50, lotteryDate_X, 24.0
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenUserNotCompanyOwner_WhenCreateLottery_ThenPermissionErrorIsReturned() {
        // Arrange: Use an unauthorized user ID
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
        int unauthorizedUserId = 999;

        // Act
        Response<Boolean> response = lotteryService.createLottery(
                validToken, unauthorizedUserId, eventWithLottery.getId(), 50, lotteryDate_X, 24.0
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
                validToken, creatorId, "non-existing-event-id", 50, lotteryDate_X, 24.0
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("event not found", response.getMessage());
    }

    @Test
    void GivenEventNotSupportingLottery_WhenCreateLottery_ThenLotteryNotSupportedErrorIsReturned() {
        // Arrange: Create an event that DOES NOT support lottery
        Event eventWithoutLottery = new Event(companyId, creatorId, LocalDateTime.now().plusDays(30), "Regular Event", saleStartDate_Y, false);
        eventRepo.store(eventWithoutLottery);
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

        // Act
        Response<Boolean> response = lotteryService.createLottery(
                validToken, creatorId, eventWithoutLottery.getId(), 50, lotteryDate_X, 24.0
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
                validToken, creatorId, eventWithLottery.getId(), 50, pastDate_Z, 24.0
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
                validToken, creatorId, eventWithLottery.getId(), 50, lateDate_T, 24.0
        );

        // Assert
        assertFalse(response.getValue());
        assertEquals("Register window must be before sale start date", response.getMessage());
    }

    @Test
    void GivenMoreRegistrationsThanCapacity_WhenDrawLotteryIsTriggered_ThenWinnersAreSelectedUpToCapacity() {
        // Arrange: Create an event and a lottery with a capacity of 2
        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
        lotteryService.createLottery(validToken, creatorId, eventWithLottery.getId(), 2, lotteryDate_X, 24.0);

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
        lotteryService.createLottery(validToken, creatorId, eventWithLottery.getId(), 10, lotteryDate_X, 24.0);

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