package domain.policy;

import domain.dto.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PurchasePolicyTest {

    private static final int EVENT_ID = 1;
    private UserDTO user;

    @BeforeEach
    void setUp() {
        user = new UserDTO(123, 25, new HashMap<>());
    }

    // --- MaxTicketsRule ---

    @Test
    void GivenQuantityWithinLimit_WhenIsSatisfied_ThenReturnTrue() {
        MaxTicketsRule rule = new MaxTicketsRule(4);
        assertTrue(rule.isSatisfied(user, 3, EVENT_ID));
    }

    @Test
    void GivenQuantityEqualsLimit_WhenIsSatisfied_ThenReturnTrue() {
        MaxTicketsRule rule = new MaxTicketsRule(4);
        assertTrue(rule.isSatisfied(user, 4, EVENT_ID));
    }

    @Test
    void GivenQuantityExceedsLimit_WhenIsSatisfied_ThenReturnFalse() {
        MaxTicketsRule rule = new MaxTicketsRule(4);
        assertFalse(rule.isSatisfied(user, 5, EVENT_ID));
    }

    @Test
    void GivenUserAlreadyBoughtTickets_WhenIsSatisfied_ThenCountCombined() {
        Map<Integer, Integer> history = new HashMap<>();
        history.put(EVENT_ID, 3);
        UserDTO userWithHistory = new UserDTO(123, 25, history);
        MaxTicketsRule rule = new MaxTicketsRule(4);
        assertFalse(rule.isSatisfied(userWithHistory, 2, EVENT_ID));
    }

    @Test
    void GivenUserAlreadyBoughtAndTotalWithinLimit_WhenIsSatisfied_ThenReturnTrue() {
        Map<Integer, Integer> history = new HashMap<>();
        history.put(EVENT_ID, 2);
        UserDTO userWithHistory = new UserDTO(123, 25, history);
        MaxTicketsRule rule = new MaxTicketsRule(4);
        assertTrue(rule.isSatisfied(userWithHistory, 2, EVENT_ID));
    }

    @Test
    void GivenHistoryForDifferentEvent_WhenIsSatisfied_ThenIgnoreOtherEvent() {
        Map<Integer, Integer> history = new HashMap<>();
        history.put(99, 3);
        UserDTO userWithHistory = new UserDTO(123, 25, history);
        MaxTicketsRule rule = new MaxTicketsRule(4);
        assertTrue(rule.isSatisfied(userWithHistory, 4, EVENT_ID));
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
        assertTrue(new MinAgeRule(18).isSatisfied(user, 1, EVENT_ID));
    }

    @Test
    void GivenUserAgeEqualsMinAge_WhenIsSatisfied_ThenReturnTrue() {
        UserDTO userAge18 = new UserDTO(456, 18, new HashMap<>());
        assertTrue(new MinAgeRule(18).isSatisfied(userAge18, 1, EVENT_ID));
    }

    @Test
    void GivenUserBelowMinAge_WhenIsSatisfied_ThenReturnFalse() {
        UserDTO youngUser = new UserDTO(456, 16, new HashMap<>());
        assertFalse(new MinAgeRule(18).isSatisfied(youngUser, 1, EVENT_ID));
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
        assertTrue(new PurchasePolicy().isSatisfied(user, 10, EVENT_ID));
    }

    @Test
    void GivenAllRulesPass_WhenIsSatisfied_ThenReturnTrue() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        policy.addRule(new MinAgeRule(18));
        assertTrue(policy.isSatisfied(user, 3, EVENT_ID));
    }

    @Test
    void GivenQuantityExceedsMaxTickets_WhenIsSatisfied_ThenReturnFalse() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        assertFalse(policy.isSatisfied(user, 5, EVENT_ID));
    }

    @Test
    void GivenUserBelowMinAgeInPolicy_WhenIsSatisfied_ThenReturnFalse() {
        UserDTO youngUser = new UserDTO(456, 16, new HashMap<>());
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MinAgeRule(18));
        assertFalse(policy.isSatisfied(youngUser, 2, EVENT_ID));
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

        UserDTO youngUser = new UserDTO(456, 16, new HashMap<>());
        assertTrue(policy.isSatisfied(youngUser, 2, EVENT_ID));
    }
}
