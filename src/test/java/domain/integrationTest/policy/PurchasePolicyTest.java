package domain.integrationTest.policy;

import domain.dto.UserDTO;
import domain.policy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PurchasePolicyTest {

    private UserDTO adult;  // born 2001 → age 25 in 2026
    private UserDTO minor;  // born 2010 → age 16 in 2026
    private UserDTO user18; // born 2008 → age 18 in 2026

    @BeforeEach
    void setUp() {
        adult  = new UserDTO("adult@test.com",  "Adult",  "User", "Pass123!", 1, 1, 2001, "City", "050-123-4567");
        minor  = new UserDTO("minor@test.com",  "Minor",  "User", "Pass123!", 1, 1, 2010, "City", "050-000-0000");
        user18 = new UserDTO("user18@test.com", "User18", "User", "Pass123!", 1, 1, 2008, "City", "050-111-1111");
    }

    // --- MaxTicketsRule ---

    @Test
    void GivenQuantityWithinLimit_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new MaxTicketsRule(4).isSatisfied(adult, 3, 0));
    }

    @Test
    void GivenQuantityEqualsLimit_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new MaxTicketsRule(4).isSatisfied(adult, 4, 0));
    }

    @Test
    void GivenQuantityExceedsLimit_WhenIsSatisfied_ThenReturnFalse() {
        assertFalse(new MaxTicketsRule(4).isSatisfied(adult, 5, 0));
    }

    @Test
    void GivenUserAlreadyBoughtTickets_WhenIsSatisfied_ThenCountCombined() {
        assertFalse(new MaxTicketsRule(4).isSatisfied(adult, 2, 3)); // 3+2=5 > 4
    }

    @Test
    void GivenUserAlreadyBoughtAndTotalWithinLimit_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new MaxTicketsRule(4).isSatisfied(adult, 2, 2)); // 2+2=4 = limit
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
        assertTrue(new MinAgeRule(18).isSatisfied(adult, 1, 0));
    }

    @Test
    void GivenUserAgeEqualsMinAge_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new MinAgeRule(18).isSatisfied(user18, 1, 0));
    }

    @Test
    void GivenUserBelowMinAge_WhenIsSatisfied_ThenReturnFalse() {
        assertFalse(new MinAgeRule(18).isSatisfied(minor, 1, 0));
    }

    @Test
    void GivenMinAgeIsNegative_WhenIsValid_ThenReturnFalse() {
        assertFalse(new MinAgeRule(-1).isValid());
    }

    @Test
    void GivenMinAgeIsZero_WhenIsValid_ThenReturnTrue() {
        assertTrue(new MinAgeRule(0).isValid());
    }

    // --- MinTicketsRule ---

    @Test
    void GivenQuantityMeetsMinimum_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new MinTicketsRule(3).isSatisfied(adult, 3, 0));
    }

    @Test
    void GivenQuantityExceedsMinimum_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new MinTicketsRule(3).isSatisfied(adult, 5, 0));
    }

    @Test
    void GivenQuantityBelowMinimum_WhenIsSatisfied_ThenReturnFalse() {
        assertFalse(new MinTicketsRule(3).isSatisfied(adult, 2, 0));
    }

    @Test
    void GivenZeroMinTickets_WhenIsValid_ThenReturnFalse() {
        assertFalse(new MinTicketsRule(0).isValid());
    }

    @Test
    void GivenNegativeMinTickets_WhenIsValid_ThenReturnFalse() {
        assertFalse(new MinTicketsRule(-1).isValid());
    }

    @Test
    void GivenPositiveMinTickets_WhenIsValid_ThenReturnTrue() {
        assertTrue(new MinTicketsRule(1).isValid());
    }

    // --- AndPurchasePolicy ---

    @Test
    void GivenEmptyAndPolicy_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new AndPurchasePolicy().isSatisfied(adult, 10, 0));
    }

    @Test
    void GivenAllRulesPass_WhenAndPolicyIsSatisfied_ThenReturnTrue() {
        AndPurchasePolicy policy = new AndPurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        policy.addRule(new MinAgeRule(18));
        assertTrue(policy.isSatisfied(adult, 3, 0));
    }

    @Test
    void GivenOneRuleFails_WhenAndPolicyIsSatisfied_ThenReturnFalse() {
        AndPurchasePolicy policy = new AndPurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        policy.addRule(new MinAgeRule(18));
        assertFalse(policy.isSatisfied(minor, 3, 0));
    }

    @Test
    void GivenInvalidRule_WhenAddRuleToAndPolicy_ThenThrowsIllegalArgument() {
        AndPurchasePolicy policy = new AndPurchasePolicy();
        assertThrows(IllegalArgumentException.class, () -> policy.addRule(new MaxTicketsRule(-1)));
    }

    @Test
    void GivenDuplicateRule_WhenAddRuleToAndPolicy_ThenThrowsRuntimeException() {
        AndPurchasePolicy policy = new AndPurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        assertThrows(RuntimeException.class, () -> policy.addRule(new MaxTicketsRule(4)));
    }

    @Test
    void GivenRuleRemoved_WhenAndPolicyIsSatisfied_ThenIgnoreRemovedRule() {
        AndPurchasePolicy policy = new AndPurchasePolicy();
        MinAgeRule ageRule = new MinAgeRule(18);
        policy.addRule(new MaxTicketsRule(4));
        policy.addRule(ageRule);
        policy.removeRule(ageRule);
        assertTrue(policy.isSatisfied(minor, 2, 0));
    }

    @Test
    void GivenAndPolicy_WhenCopied_ThenRulesAreIndependent() {
        AndPurchasePolicy original = new AndPurchasePolicy();
        original.addRule(new MaxTicketsRule(4));
        AndPurchasePolicy copy = (AndPurchasePolicy) original.copyPolicy();
        copy.addRule(new MinAgeRule(18));
        assertEquals(1, original.getRules().size(), "Copy should not affect original");
    }

    // --- OrPurchasePolicy ---

    @Test
    void GivenEmptyOrPolicy_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new OrPurchasePolicy().isSatisfied(adult, 1, 0));
    }

    @Test
    void GivenAtLeastOneRulePasses_WhenOrPolicyIsSatisfied_ThenReturnTrue() {
        OrPurchasePolicy policy = new OrPurchasePolicy();
        policy.addRule(new MinAgeRule(18));   // passes for adult
        policy.addRule(new MaxTicketsRule(2)); // fails (qty=5)
        assertTrue(policy.isSatisfied(adult, 5, 0));
    }

    @Test
    void GivenAllRulesFail_WhenOrPolicyIsSatisfied_ThenReturnFalse() {
        OrPurchasePolicy policy = new OrPurchasePolicy();
        policy.addRule(new MinAgeRule(18));    // fails for minor
        policy.addRule(new MaxTicketsRule(2)); // fails (qty=5)
        assertFalse(policy.isSatisfied(minor, 5, 0));
    }

    @Test
    void GivenOrPolicy_WhenCopied_ThenRulesAreIndependent() {
        OrPurchasePolicy original = new OrPurchasePolicy();
        original.addRule(new MaxTicketsRule(2));
        OrPurchasePolicy copy = (OrPurchasePolicy) original.copyPolicy();
        copy.addRule(new MinTicketsRule(100));
        assertEquals(1, original.getRules().size(), "Copy should not affect original");
    }

    // --- Nested Composition: age >= 18 AND (qty <= 2 OR qty >= 100) ---

    private AndPurchasePolicy buildComplexPolicy() {
        OrPurchasePolicy orPolicy = new OrPurchasePolicy();
        orPolicy.addRule(new MaxTicketsRule(2));
        orPolicy.addRule(new MinTicketsRule(100));

        AndPurchasePolicy policy = new AndPurchasePolicy();
        policy.addRule(new MinAgeRule(18));
        policy.addRule(orPolicy);
        return policy;
    }

    @Test
    void GivenComplexPolicy_WhenAdultBuys1Ticket_ThenSatisfied() {
        assertTrue(buildComplexPolicy().isSatisfied(adult, 1, 0));
    }

    @Test
    void GivenComplexPolicy_WhenAdultBuys5Tickets_ThenNotSatisfied() {
        assertFalse(buildComplexPolicy().isSatisfied(adult, 5, 0)); // 5 > 2 and 5 < 100
    }

    @Test
    void GivenComplexPolicy_WhenAdultBuys100Tickets_ThenSatisfied() {
        assertTrue(buildComplexPolicy().isSatisfied(adult, 100, 0));
    }

    @Test
    void GivenComplexPolicy_WhenMinorBuys1Ticket_ThenNotSatisfied() {
        assertFalse(buildComplexPolicy().isSatisfied(minor, 1, 0)); // age fails AND
    }

    @Test
    void GivenNestedAndInsideOr_WhenOneBranchPasses_ThenSatisfied() {
        // OR( MaxTickets(2), AND(MinAge(18), MinTickets(10)) )
        AndPurchasePolicy innerAnd = new AndPurchasePolicy();
        innerAnd.addRule(new MinAgeRule(18));
        innerAnd.addRule(new MinTicketsRule(10));

        OrPurchasePolicy policy = new OrPurchasePolicy();
        policy.addRule(new MaxTicketsRule(2));
        policy.addRule(innerAnd);

        assertTrue(policy.isSatisfied(adult, 15, 0));  // inner AND passes (adult, qty=15>=10)
        assertTrue(policy.isSatisfied(minor, 1, 0));   // MaxTickets(2) passes (qty=1<=2)
        assertFalse(policy.isSatisfied(minor, 5, 0));  // qty>2 fails MaxTickets, minor fails MinAge
    }

    // --- getPolicyType ---

    @Test
    void GivenAndPolicy_WhenGetPolicyType_ThenReturnsAND() {
        assertEquals(PurchasePolicyType.AND, new AndPurchasePolicy().getPolicyType());
    }

    @Test
    void GivenOrPolicy_WhenGetPolicyType_ThenReturnsOR() {
        assertEquals(PurchasePolicyType.OR, new OrPurchasePolicy().getPolicyType());
    }
}
