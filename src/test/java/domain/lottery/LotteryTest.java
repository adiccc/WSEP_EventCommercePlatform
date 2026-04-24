package domain.lottery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LotteryTest {

    private Lottery lottery;
    private final int capacity = 3;

    @BeforeEach
    void setUp() {
        // Create a lottery with a capacity of 3
        lottery = new Lottery("event-201", capacity, LocalDateTime.now().plusDays(5), 24.0);
    }

    @Test
    void GivenNoRegisteredUsers_WhenDrawWinners_ThenWinnersListIsEmpty() {
        // Arrange: No users added to lottery.getRegistered()

        // Act
        lottery.drawWinners();

        // Assert
        assertTrue(lottery.getWinners().isEmpty(), "Winners list should be empty when no one registered");
    }

    @Test
    void GivenLessOrEqualRegisteredThanCapacity_WhenDrawWinners_ThenAllWin() {
        // Arrange: 2 users registered, capacity is 3
        lottery.getRegistered().add(101);
        lottery.getRegistered().add(102);

        // Act
        lottery.drawWinners();

        // Assert
        assertEquals(2, lottery.getWinners().size(), "All registered users should win");
        assertTrue(lottery.getWinners().contains(101));
        assertTrue(lottery.getWinners().contains(102));
    }

    @Test
    void GivenMoreRegisteredThanCapacity_WhenDrawWinners_ThenWinnersEqualToCapacity() {
        // Arrange: 5 users registered, capacity is 3
        lottery.getRegistered().add(101);
        lottery.getRegistered().add(102);
        lottery.getRegistered().add(103);
        lottery.getRegistered().add(104);
        lottery.getRegistered().add(105);

        // Act
        lottery.drawWinners();

        // Assert
        assertEquals(capacity, lottery.getWinners().size(), "Winners count must exactly match the capacity");

        // Ensure all winners are from the registered list
        for (Integer winnerId : lottery.getWinners()) {
            assertTrue(lottery.getRegistered().contains(winnerId), "Winner must be from the registered users");
        }
    }

}