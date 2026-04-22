package domain.policy;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DiscountPolicyTest {

    private static final LocalDate FUTURE = LocalDate.now().plusDays(1);
    private static final LocalDate PAST   = LocalDate.now().minusDays(1);

    // --- VisualDiscount ---

    @Test
    void GivenValidDate_WhenApply_ThenDiscountApplied() {
        assertEquals(80.0, new VisualDiscount(20, FUTURE).apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenExpiredDate_WhenApply_ThenNoPriceChange() {
        assertEquals(100.0, new VisualDiscount(20, PAST).apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenNegativePercentage_WhenIsValid_ThenReturnFalse() {
        assertFalse(new VisualDiscount(-10, FUTURE).isValid());
    }

    @Test
    void GivenNullEndDate_WhenIsValid_ThenReturnFalse() {
        assertFalse(new VisualDiscount(10, null).isValid());
    }

    @Test
    void GivenValidVisualDiscount_WhenIsValid_ThenReturnTrue() {
        assertTrue(new VisualDiscount(10, FUTURE).isValid());
    }

    // --- LimitedDiscount ---

    @Test
    void GivenQuantityBelowThreshold_WhenApply_ThenNoPriceChange() {
        // buy 3 get 4th free — need at least 4 tickets
        assertEquals(100.0, new LimitedDiscount(100, 3).apply(100.0, 3, null), 0.001);
    }

    @Test
    void GivenOneFullGroup_WhenApply_ThenOneTicketFree() {
        // 4 tickets at 25 each = 100; 1 group -> 1 ticket free
        assertEquals(75.0, new LimitedDiscount(100, 3).apply(100.0, 4, null), 0.001);
    }

    @Test
    void GivenTwoFullGroups_WhenApply_ThenTwoTicketsFree() {
        // 8 tickets at 12.5 each = 100; 2 groups -> 2 free tickets
        assertEquals(75.0, new LimitedDiscount(100, 3).apply(100.0, 8, null), 0.001);
    }

    @Test
    void GivenHalfDiscount_WhenApply_ThenHalfPriceOnEligibleTicket() {
        // 4 tickets at 25 each = 100; 50% off 1 ticket = 12.5 savings
        assertEquals(87.5, new LimitedDiscount(50, 3).apply(100.0, 4, null), 0.001);
    }

    @Test
    void GivenNegativePercentage_WhenLimitedIsValid_ThenReturnFalse() {
        assertFalse(new LimitedDiscount(-10, 3).isValid());
    }

    @Test
    void GivenZeroMinQuantity_WhenIsValid_ThenReturnFalse() {
        assertFalse(new LimitedDiscount(50, 0).isValid());
    }

    @Test
    void GivenValidLimitedDiscount_WhenIsValid_ThenReturnTrue() {
        assertTrue(new LimitedDiscount(50, 3).isValid());
    }

    // --- CodeCoupun ---

    @Test
    void GivenCorrectCodeAndNotExpired_WhenApply_ThenDiscountApplied() {
        assertEquals(90.0, new CodeCoupun("SAVE10", 10, FUTURE).apply(100.0, 1, "SAVE10"), 0.001);
    }

    @Test
    void GivenWrongCode_WhenApply_ThenNoPriceChange() {
        assertEquals(100.0, new CodeCoupun("SAVE10", 10, FUTURE).apply(100.0, 1, "WRONG"), 0.001);
    }

    @Test
    void GivenExpiredCoupon_WhenApply_ThenNoPriceChange() {
        assertEquals(100.0, new CodeCoupun("SAVE10", 10, PAST).apply(100.0, 1, "SAVE10"), 0.001);
    }

    @Test
    void GivenNullCode_WhenIsValid_ThenReturnFalse() {
        assertFalse(new CodeCoupun(null, 10, FUTURE).isValid());
    }

    @Test
    void GivenEmptyCode_WhenIsValid_ThenReturnFalse() {
        assertFalse(new CodeCoupun("", 10, FUTURE).isValid());
    }

    @Test
    void GivenValidCoupon_WhenIsValid_ThenReturnTrue() {
        assertTrue(new CodeCoupun("VIP", 15, FUTURE).isValid());
    }

    // --- DiscountPolicy (composite) ---

    @Test
    void GivenEmptyPolicy_WhenApply_ThenNoPriceChange() {
        assertEquals(100.0, new DiscountPolicy().apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenTwoVisualDiscounts_WhenApply_ThenBothAppliedSequentially() {
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE)); // 100 -> 90
        policy.addDiscount(new VisualDiscount(10, FUTURE)); // 90 -> 81
        assertEquals(81.0, policy.apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenDiscountRemoved_WhenApply_ThenRemovedDiscountIgnored() {
        DiscountPolicy policy = new DiscountPolicy();
        VisualDiscount d = new VisualDiscount(10, FUTURE);
        policy.addDiscount(d);
        policy.removeDiscount(d);
        assertEquals(100.0, policy.apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenOneInvalidDiscount_WhenIsValid_ThenReturnFalse() {
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));
        policy.addDiscount(new VisualDiscount(-5, FUTURE));
        assertFalse(policy.isValid());
    }

    @Test
    void GivenAllValidDiscounts_WhenIsValid_ThenReturnTrue() {
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));
        policy.addDiscount(new CodeCoupun("VIP", 15, FUTURE));
        assertTrue(policy.isValid());
    }

    @Test
    void GivenCouponCodePassedThroughComposite_WhenApply_ThenCouponDiscountApplied() {
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));       // 100 -> 90
        policy.addDiscount(new CodeCoupun("VIP20", 20, FUTURE)); // 90 -> 72
        assertEquals(72.0, policy.apply(100.0, 1, "VIP20"), 0.001);
    }

    @Test
    void GivenMixedDiscountAndLimited_WhenApply_ThenBothApplied() {
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));   // 100 -> 90
        policy.addDiscount(new LimitedDiscount(100, 3));      // 4 tickets: 90 -> 67.5
        assertEquals(67.5, policy.apply(100.0, 4, null), 0.001);
    }
}
