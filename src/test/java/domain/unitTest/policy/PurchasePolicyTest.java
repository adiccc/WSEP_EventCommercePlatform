package domain.unitTest.policy;

import DTO.UserDTO;
import domain.policy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void GivenUserAlreadyBoughtTickets_WhenTotalExceedsLimit_ThenReturnFalse() {
        assertFalse(new MaxTicketsRule(4).isSatisfied(adult, 2, 3)); // 3+2=5 > 4
    }

    @Test
    void GivenUserAlreadyBoughtTickets_WhenTotalEqualsLimit_ThenReturnTrue() {
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

    @Test
    void GivenMaxTicketsRule_WhenDescribe_ThenContainsLimit() {
        assertTrue(new MaxTicketsRule(4).describe().contains("4"));
    }

    @Test
    void GivenMaxTicketsRule_WhenCopied_ThenIndependentOfOriginal() {
        MaxTicketsRule original = new MaxTicketsRule(4);
        Purchase copy = original.copy();
        assertNotSame(original, copy);
        assertEquals(original, copy);
    }

    @Test
    void GivenTwoRulesWithSameLimit_WhenEquals_ThenReturnTrue() {
        assertEquals(new MaxTicketsRule(4), new MaxTicketsRule(4));
    }

    @Test
    void GivenTwoRulesWithDifferentLimits_WhenEquals_ThenReturnFalse() {
        assertNotEquals(new MaxTicketsRule(4), new MaxTicketsRule(5));
    }

    // --- MinAgeRule ---

    @Test
    void GivenUserAboveMinAge_WhenIsSatisfied_ThenReturnTrue() {
        assertTrue(new MinAgeRule(18).isSatisfied(adult, 1, 0));
    }

    @Test
    void GivenUserExactlyAtMinAge_WhenIsSatisfied_ThenReturnTrue() {
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

    @Test
    void GivenMinAgeIsPositive_WhenIsValid_ThenReturnTrue() {
        assertTrue(new MinAgeRule(18).isValid());
    }

    @Test
    void GivenMinAgeRule_WhenDescribe_ThenContainsAge() {
        assertTrue(new MinAgeRule(18).describe().contains("18"));
    }

    @Test
    void GivenMinAgeRule_WhenCopied_ThenIndependentOfOriginal() {
        MinAgeRule original = new MinAgeRule(18);
        Purchase copy = original.copy();
        assertNotSame(original, copy);
        assertEquals(original, copy);
    }

    @Test
    void GivenTwoRulesWithSameAge_WhenEquals_ThenReturnTrue() {
        assertEquals(new MinAgeRule(18), new MinAgeRule(18));
    }

    @Test
    void GivenTwoRulesWithDifferentAges_WhenEquals_ThenReturnFalse() {
        assertNotEquals(new MinAgeRule(18), new MinAgeRule(21));
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

    @Test
    void GivenMinTicketsRule_WhenDescribe_ThenContainsMinimum() {
        assertTrue(new MinTicketsRule(3).describe().contains("3"));
    }

    @Test
    void GivenMinTicketsRule_WhenCopied_ThenIndependentOfOriginal() {
        MinTicketsRule original = new MinTicketsRule(3);
        Purchase copy = original.copy();
        assertNotSame(original, copy);
        assertEquals(original, copy);
    }

    @Test
    void GivenTwoRulesWithSameMinimum_WhenEquals_ThenReturnTrue() {
        assertEquals(new MinTicketsRule(3), new MinTicketsRule(3));
    }

    @Test
    void GivenTwoRulesWithDifferentMinimums_WhenEquals_ThenReturnFalse() {
        assertNotEquals(new MinTicketsRule(3), new MinTicketsRule(5));
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
    void GivenAllRulesFail_WhenAndPolicyIsSatisfied_ThenReturnFalse() {
        AndPurchasePolicy policy = new AndPurchasePolicy();
        policy.addRule(new MaxTicketsRule(2));
        policy.addRule(new MinAgeRule(18));
        assertFalse(policy.isSatisfied(minor, 5, 0));
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
    void GivenRuleRemoved_WhenAndPolicyIsSatisfied_ThenRemovedRuleIgnored() {
        AndPurchasePolicy policy = new AndPurchasePolicy();
        MinAgeRule ageRule = new MinAgeRule(18);
        policy.addRule(new MaxTicketsRule(4));
        policy.addRule(ageRule);
        policy.removeRule(ageRule);
        assertTrue(policy.isSatisfied(minor, 2, 0));
    }

    @Test
    void GivenNonExistentRule_WhenRemoveFromAndPolicy_ThenThrowsRuntimeException() {
        AndPurchasePolicy policy = new AndPurchasePolicy();
        assertThrows(RuntimeException.class, () -> policy.removeRule(new MinAgeRule(18)));
    }

    @Test
    void GivenAndPolicy_WhenCopied_ThenRulesAreIndependent() {
        AndPurchasePolicy original = new AndPurchasePolicy();
        original.addRule(new MaxTicketsRule(4));
        AndPurchasePolicy copy = (AndPurchasePolicy) original.copyPolicy();
        copy.addRule(new MinAgeRule(18));
        assertEquals(1, original.getRules().size(), "Copy should not affect original");
    }

    @Test
    void GivenAndPolicyWithOneRule_WhenDescribe_ThenDelegatesToRule() {
        AndPurchasePolicy policy = new AndPurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        assertEquals(new MaxTicketsRule(4).describe(), policy.describe());
    }

    @Test
    void GivenAndPolicyWithMultipleRules_WhenDescribe_ThenContainsAND() {
        AndPurchasePolicy policy = new AndPurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        policy.addRule(new MinAgeRule(18));
        assertTrue(policy.describe().contains("AND"));
    }

    @Test
    void GivenAndPolicy_WhenGetPolicyType_ThenReturnsAND() {
        assertEquals(PurchasePolicyType.AND, new AndPurchasePolicy().getPolicyType());
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
    void GivenFirstRuleFails_WhenSecondPasses_WhenOrPolicyIsSatisfied_ThenReturnTrue() {
        OrPurchasePolicy policy = new OrPurchasePolicy();
        policy.addRule(new MinAgeRule(18));   // fails for minor
        policy.addRule(new MinTicketsRule(1)); // passes (qty=2>=1)
        assertTrue(policy.isSatisfied(minor, 2, 0));
    }

    @Test
    void GivenInvalidRule_WhenAddRuleToOrPolicy_ThenThrowsIllegalArgument() {
        OrPurchasePolicy policy = new OrPurchasePolicy();
        assertThrows(IllegalArgumentException.class, () -> policy.addRule(new MaxTicketsRule(0)));
    }

    @Test
    void GivenDuplicateRule_WhenAddRuleToOrPolicy_ThenThrowsRuntimeException() {
        OrPurchasePolicy policy = new OrPurchasePolicy();
        policy.addRule(new MinAgeRule(18));
        assertThrows(RuntimeException.class, () -> policy.addRule(new MinAgeRule(18)));
    }

    @Test
    void GivenRuleRemoved_WhenOrPolicyIsSatisfied_ThenRemovedRuleIgnored() {
        OrPurchasePolicy policy = new OrPurchasePolicy();
        MinAgeRule ageRule = new MinAgeRule(18);
        policy.addRule(ageRule);
        policy.addRule(new MaxTicketsRule(4));
        policy.removeRule(ageRule);
        assertFalse(policy.isSatisfied(minor, 5, 0)); // only MaxTickets(4) remains, qty=5 fails
    }

    @Test
    void GivenNonExistentRule_WhenRemoveFromOrPolicy_ThenThrowsRuntimeException() {
        OrPurchasePolicy policy = new OrPurchasePolicy();
        assertThrows(RuntimeException.class, () -> policy.removeRule(new MinAgeRule(18)));
    }

    @Test
    void GivenOrPolicy_WhenCopied_ThenRulesAreIndependent() {
        OrPurchasePolicy original = new OrPurchasePolicy();
        original.addRule(new MaxTicketsRule(2));
        OrPurchasePolicy copy = (OrPurchasePolicy) original.copyPolicy();
        copy.addRule(new MinTicketsRule(100));
        assertEquals(1, original.getRules().size(), "Copy should not affect original");
    }

    @Test
    void GivenOrPolicyWithMultipleRules_WhenDescribe_ThenContainsOR() {
        OrPurchasePolicy policy = new OrPurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        policy.addRule(new MinAgeRule(18));
        assertTrue(policy.describe().contains("OR"));
    }

    @Test
    void GivenOrPolicy_WhenGetPolicyType_ThenReturnsOR() {
        assertEquals(PurchasePolicyType.OR, new OrPurchasePolicy().getPolicyType());
    }

    // --- Nested Composition ---

    @Test
    void GivenAndWithNestedOr_WhenAdultBuysSmallQuantity_ThenSatisfied() {
        // AND( MinAge(18), OR( MaxTickets(2), MinTickets(10) ) )
        OrPurchasePolicy or = new OrPurchasePolicy();
        or.addRule(new MaxTicketsRule(2));
        or.addRule(new MinTicketsRule(10));

        AndPurchasePolicy policy = new AndPurchasePolicy();
        policy.addRule(new MinAgeRule(18));
        policy.addRule(or);

        assertTrue(policy.isSatisfied(adult, 1, 0));  // age ok, qty=1<=2 → passes
    }

    @Test
    void GivenAndWithNestedOr_WhenAdultBuysMidQuantity_ThenNotSatisfied() {
        OrPurchasePolicy or = new OrPurchasePolicy();
        or.addRule(new MaxTicketsRule(2));
        or.addRule(new MinTicketsRule(10));

        AndPurchasePolicy policy = new AndPurchasePolicy();
        policy.addRule(new MinAgeRule(18));
        policy.addRule(or);

        assertFalse(policy.isSatisfied(adult, 5, 0)); // 5>2 and 5<10 → OR fails
    }

    @Test
    void GivenAndWithNestedOr_WhenMinorBuysSmallQuantity_ThenNotSatisfied() {
        OrPurchasePolicy or = new OrPurchasePolicy();
        or.addRule(new MaxTicketsRule(2));
        or.addRule(new MinTicketsRule(10));

        AndPurchasePolicy policy = new AndPurchasePolicy();
        policy.addRule(new MinAgeRule(18));
        policy.addRule(or);

        assertFalse(policy.isSatisfied(minor, 1, 0)); // age fails AND
    }

    @Test
    void GivenOrWithNestedAnd_WhenOneBranchPasses_ThenSatisfied() {
        // OR( MaxTickets(2), AND(MinAge(18), MinTickets(10)) )
        AndPurchasePolicy innerAnd = new AndPurchasePolicy();
        innerAnd.addRule(new MinAgeRule(18));
        innerAnd.addRule(new MinTicketsRule(10));

        OrPurchasePolicy policy = new OrPurchasePolicy();
        policy.addRule(new MaxTicketsRule(2));
        policy.addRule(innerAnd);

        assertTrue(policy.isSatisfied(adult, 15, 0));  // inner AND passes
        assertTrue(policy.isSatisfied(minor, 1, 0));   // MaxTickets(2) passes
        assertFalse(policy.isSatisfied(minor, 5, 0));  // qty>2, minor fails MinAge
    }

    @Test
    void GivenNestedPolicy_WhenCopied_ThenDeepCopyIsIndependent() {
        AndPurchasePolicy inner = new AndPurchasePolicy();
        inner.addRule(new MinAgeRule(18));

        OrPurchasePolicy outer = new OrPurchasePolicy();
        outer.addRule(inner);

        OrPurchasePolicy copy = (OrPurchasePolicy) outer.copyPolicy();
        copy.addRule(new MaxTicketsRule(5));

        assertEquals(1, outer.getRules().size(), "Copy modification should not affect original");
    }
}
