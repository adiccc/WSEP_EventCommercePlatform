package domain.integrationTest.lottery;

import domain.dto.LotteryDTO;
import domain.lottery.Lottery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LotteryTest {

    private Lottery lottery;
    private final int capacity = 3;

    @BeforeEach
    void setUp() {
        // Create a lottery with a capacity of 3
        lottery = new Lottery(201, capacity, LocalDateTime.now().plusDays(5), 24);
    }

    @Test
    void GivenNoRegisteredUsers_WhenDrawWinners_ThenReturnedMapIsEmpty() {
        // Arrange: No users registered

        // Act
        Map<Integer, String> winnersMap = lottery.drawWinners();

        // Assert
        assertTrue(winnersMap.isEmpty(), "Returned map should be empty when no one registered");
        assertTrue(lottery.getWinners().isEmpty(), "Internal winners list should also be empty");
    }

    @Test
    void GivenLessOrEqualRegisteredThanCapacity_WhenDrawWinners_ThenAllWinAndReceiveValidCodes() {
        // Arrange: 2 users registered, capacity is 3
        lottery.registerUserToLottery(101);
        lottery.registerUserToLottery(102);

        // Act
        Map<Integer, String> winnersMap = lottery.drawWinners();

        // Assert: Check winning status
        assertEquals(2, winnersMap.size(), "All registered users should win");
        assertTrue(winnersMap.containsKey(101), "User 101 should be a winner");
        assertTrue(winnersMap.containsKey(102), "User 102 should be a winner");

        // Assert: Validate code generation logic
        for (String code : winnersMap.values()) {
            assertNotNull(code, "Generated code must not be null");
            assertEquals(7, code.length(), "Code should be exactly 7 characters (6 chars + 1 hyphen)");
            assertTrue(code.contains("-"), "Code must contain a hyphen for readability");
        }
    }

    @Test
    void GivenMoreRegisteredThanCapacity_WhenDrawWinners_ThenWinnersEqualToCapacityAndCodesAreUnique() {
        // Arrange: 5 users registered, capacity is 3
        lottery.registerUserToLottery(101);
        lottery.registerUserToLottery(102);
        lottery.registerUserToLottery(103);
        lottery.registerUserToLottery(104);
        lottery.registerUserToLottery(105);

        // Act
        Map<Integer, String> winnersMap = lottery.drawWinners();

        // Assert: Check capacity enforcement
        assertEquals(capacity, winnersMap.size(), "Winners count must exactly match the capacity");

        // Assert: Ensure all winners are originally from the registered list
        for (Integer winnerId : winnersMap.keySet()) {
            assertTrue(lottery.getRegistered().contains(winnerId), "Winner must be from the registered users");
        }

        // Assert: Verify uniqueness of generated codes
        long uniqueCodes = winnersMap.values().stream().distinct().count();
        assertEquals(winnersMap.size(), uniqueCodes, "All generated access codes must be entirely unique");
    }

    @Test
    void GivenWinnerWithCode_WhenCodeMatchesUser_ThenValidationReturnsCorrectBoolean() {
        // Arrange: Register one user and perform the draw
        lottery.registerUserToLottery(101);
        Map<Integer, String> winnersMap = lottery.drawWinners();
        String assignedCode = winnersMap.get(101);

        // Act & Assert: Test the validation function scenarios
        assertTrue(lottery.codeMatchesUser(101, assignedCode), "Validation should succeed for correct user and code pair");
        assertFalse(lottery.codeMatchesUser(101, "WRONG-CODE"), "Validation should fail for an incorrect code");
        assertFalse(lottery.codeMatchesUser(999, assignedCode), "Validation should fail for an unregistered user");
    }

    // ==========================================
    // Unit tests for updateLottery
    // ==========================================

    private final LocalDateTime saleStartDate = LocalDateTime.now().plusDays(10);

    @Test
    void GivenValidDTO_WhenUpdateLottery_ThenFieldsAreUpdated() {
        LocalDateTime newWindow = LocalDateTime.now().plusDays(5);
        LotteryDTO dto = new LotteryDTO(201, 10, newWindow, 48L);

        lottery.updateLottery(dto, saleStartDate);

        assertEquals(10, lottery.getCapacity());
        assertEquals(newWindow, lottery.getRegisterWindow());
        assertEquals(48L, lottery.getExpirationTime());
    }

    @Test
    void GivenCapacityZero_WhenUpdateLottery_ThenThrowsMissingDetails() {
        LotteryDTO dto = new LotteryDTO(201, 0, LocalDateTime.now().plusDays(5), 48L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> lottery.updateLottery(dto, saleStartDate));
        assertTrue(ex.getMessage().contains("complete all lottery details"));
    }

    @Test
    void GivenNullRegisterWindow_WhenUpdateLottery_ThenThrowsMissingDetails() {
        LotteryDTO dto = new LotteryDTO(201, 10, null, 48L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> lottery.updateLottery(dto, saleStartDate));
        assertTrue(ex.getMessage().contains("complete all lottery details"));
    }

    @Test
    void GivenZeroExpirationTime_WhenUpdateLottery_ThenThrowsMissingDetails() {
        LotteryDTO dto = new LotteryDTO(201, 10, LocalDateTime.now().plusDays(5), 0L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> lottery.updateLottery(dto, saleStartDate));
        assertTrue(ex.getMessage().contains("complete all lottery details"));
    }

    @Test
    void GivenRegisterWindowInPast_WhenUpdateLottery_ThenThrowsInvalid() {
        LotteryDTO dto = new LotteryDTO(201, 10, LocalDateTime.now().minusDays(1), 48L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> lottery.updateLottery(dto, saleStartDate));
        assertEquals("Register window must be in the future", ex.getMessage());
    }

    @Test
    void GivenRegisterWindowAfterSaleStart_WhenUpdateLottery_ThenThrowsInvalid() {
        LotteryDTO dto = new LotteryDTO(201, 10, saleStartDate.plusDays(1), 48L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> lottery.updateLottery(dto, saleStartDate));
        assertEquals("Register window must be before sale start date", ex.getMessage());
    }

    @Test
    void GivenWinnersAlreadyDrawn_WhenUpdateLottery_ThenThrowsException() {
        lottery.registerUserToLottery(101);
        lottery.registerUserToLottery(102);
        lottery.drawWinners();

        assertFalse(lottery.getWinners().isEmpty(),
                "Precondition: winners should be drawn");

        LotteryDTO dto = new LotteryDTO(
                201,
                5,
                LocalDateTime.now().plusDays(5),
                48L
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> lottery.updateLottery(dto, saleStartDate)
        );

        assertEquals(
                "Cannot update lottery after winners have been drawn",
                exception.getMessage()
        );

        assertFalse(lottery.getWinners().isEmpty(),
                "Winners should not be cleared after failed update");

        assertNotEquals(5, lottery.getCapacity(),
                "Capacity should not be updated after winners were drawn");
    }

}