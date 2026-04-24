package domain.policy;

import domain.dto.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PurchasePolicyTest {

    private UserDTO user;

    @BeforeEach
    void setUp() {
        user = new UserDTO("user1@test.com", "Test", "User", "Pass123!", 1, 1, 2001, "City", "050-123-4567");
    }

    // --- MaxTicketsRule ---

    @Test
    void GivenQuantityWithinLimit_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new MaxTicketsRule(4).isSatisfied(user, 3, 0));
    }

    @Test
    void GivenQuantityEqualsLimit_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new MaxTicketsRule(4).isSatisfied(user, 4, 0));
    }

    @Test
    void GivenQuantityExceedsLimit_WhenIsSatisfied_ThenReturnFalse() {
        assertFalse(new MaxTicketsRule(4).isSatisfied(user, 5, 0));
    }

    @Test
    void GivenUserAlreadyBoughtTickets_WhenIsSatisfied_ThenCountCombined() {
        assertFalse(new MaxTicketsRule(4).isSatisfied(user, 2, 3)); // 3 prior + 2 new = 5 > 4
    }

    @Test
    void GivenUserAlreadyBoughtAndTotalWithinLimit_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new MaxTicketsRule(4).isSatisfied(user, 2, 2)); // 2 prior + 2 new = 4 = limit
    }

    @Test
    void GivenLimitIsZero_WhenIsValid_ThenReturnFalse() {
        assertFalse(new MaxTicketsRule(0).isValid());
    }

    @Test
    void GivenLimitIsNegative_WhenIsValid_ThenReturnFalse() {
        assertFalse(new MaxTicketsRule(-1).isValid());
    }

    @Test
    void GivenLimitIsPositive_WhenIsValid_ThenReturnTrue() {
        assertTrue(new MaxTicketsRule(4).isValid());
    }

    // --- MinAgeRule ---

    @Test
    void GivenUserMeetsMinAge_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new MinAgeRule(18).isSatisfied(user, 1, 0));
    }

    @Test
    void GivenUserAgeEqualsMinAge_WhenIsSatisfied_ThenReturnTrue() {
        UserDTO user18 = new UserDTO("user2@test.com", "Test", "User", "Pass123!", 1, 1, 2008, "City", "050-123-4567");
        assertTrue(new MinAgeRule(18).isSatisfied(user18, 1, 0));
    }

    @Test
    void GivenUserBelowMinAge_WhenIsSatisfied_ThenReturnFalse() {
        UserDTO user16 = new UserDTO("user3@test.com", "Test", "User", "Pass123!", 1, 1, 2010, "City", "050-123-4567");
        assertFalse(new MinAgeRule(18).isSatisfied(user16, 1, 0));
    }

    @Test
    void GivenMinAgeIsNegative_WhenIsValid_ThenReturnFalse() {
        assertFalse(new MinAgeRule(-1).isValid());
    }

    @Test
    void GivenMinAgeIsZero_WhenIsValid_ThenReturnTrue() {
        assertTrue(new MinAgeRule(0).isValid());
    }

    // --- PurchasePolicy (composite) ---

    @Test
    void GivenEmptyPolicy_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new PurchasePolicy().isSatisfied(user, 10, 0));
    }

    @Test
    void GivenAllRulesPass_WhenIsSatisfied_ThenReturnTrue() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        policy.addRule(new MinAgeRule(18));
        assertTrue(policy.isSatisfied(user, 3, 0));
    }

    @Test
    void GivenQuantityExceedsMaxTickets_WhenIsSatisfied_ThenReturnFalse() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        assertFalse(policy.isSatisfied(user, 5, 0));
    }

    @Test
    void GivenUserBelowMinAgeInPolicy_WhenIsSatisfied_ThenReturnFalse() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MinAgeRule(18));

        // הותאם למשתמש בן 16 (יליד 2010)
        UserDTO user16 = new UserDTO("user4@test.com", "Test", "User", "Pass123!", 1, 1, 2010, "City", "050-123-4567");
        assertFalse(policy.isSatisfied(user16, 2, 0));
    }

    @Test
    void GivenOneInvalidRule_WhenIsValid_ThenReturnFalse() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        policy.addRule(new MaxTicketsRule(-1));
        assertFalse(policy.isValid());
    }

    @Test
    void GivenAllRulesValid_WhenIsValid_ThenReturnTrue() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        policy.addRule(new MinAgeRule(18));
        assertTrue(policy.isValid());
    }

    @Test
    void GivenRuleRemoved_WhenIsSatisfied_ThenIgnoreRemovedRule() {
        PurchasePolicy policy = new PurchasePolicy();
        MinAgeRule ageRule = new MinAgeRule(18);
        policy.addRule(new MaxTicketsRule(4));
        policy.addRule(ageRule);
        policy.removeRule(ageRule);
        UserDTO user16 = new UserDTO("user5@test.com", "Test", "User", "Pass123!", 1, 1, 2010, "City", "050-123-4567");
        assertTrue(policy.isSatisfied(user16, 2, 0));
    }
}