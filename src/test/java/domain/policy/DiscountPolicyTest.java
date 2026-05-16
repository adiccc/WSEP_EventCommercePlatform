package domain.policy;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class DiscountPolicyTest {

    private static final LocalDate FUTURE       = LocalDate.now().plusDays(1);
    private static final LocalDate PAST         = LocalDate.now().minusDays(1);
    private static final LocalDateTime FUTURE_DT = LocalDateTime.now().plusDays(1);
    private static final LocalDateTime PAST_DT   = LocalDateTime.now().minusDays(1);

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

    // --- MinQuantityDiscount ---

    @Test
    void GivenMinQuantity_WhenQuantityBelow_ThenNoPriceChange() {
        assertEquals(100.0, new MinQuantityDiscount(50, 3).apply(100.0, 2, null), 0.001);
    }

    @Test
    void GivenMinQuantity_WhenQuantityMeetsThreshold_ThenDiscountApplied() {
        assertEquals(50.0, new MinQuantityDiscount(50, 3).apply(100.0, 3, null), 0.001);
    }

    @Test
    void GivenMinQuantity_WhenQuantityExceedsThreshold_ThenDiscountApplied() {
        assertEquals(50.0, new MinQuantityDiscount(50, 3).apply(100.0, 5, null), 0.001);
    }

    @Test
    void GivenZeroMinQuantity_WhenConstructing_ThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MinQuantityDiscount(50, 0));
    }

    @Test
    void GivenInvalidPercentage_WhenConstructingMinQuantityDiscount_ThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MinQuantityDiscount(-10, 3));
    }

    @Test
    void GivenValidMinQuantityDiscount_WhenIsValid_ThenReturnTrue() {
        assertTrue(new MinQuantityDiscount(50, 3).isValid());
    }

    // --- MaxQuantityDiscount ---

    @Test
    void GivenMaxQuantity_WhenQuantityAbove_ThenNoPriceChange() {
        assertEquals(100.0, new MaxQuantityDiscount(50, 5).apply(100.0, 6, null), 0.001);
    }

    @Test
    void GivenMaxQuantity_WhenQuantityWithinLimit_ThenDiscountApplied() {
        assertEquals(50.0, new MaxQuantityDiscount(50, 5).apply(100.0, 5, null), 0.001);
    }

    @Test
    void GivenMaxQuantity_WhenQuantityBelowLimit_ThenDiscountApplied() {
        assertEquals(50.0, new MaxQuantityDiscount(50, 5).apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenZeroMaxQuantity_WhenConstructing_ThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MaxQuantityDiscount(50, 0));
    }

    @Test
    void GivenInvalidPercentage_WhenConstructingMaxQuantityDiscount_ThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MaxQuantityDiscount(-10, 5));
    }

    @Test
    void GivenValidMaxQuantityDiscount_WhenIsValid_ThenReturnTrue() {
        assertTrue(new MaxQuantityDiscount(50, 5).isValid());
    }

    // --- DateRangeDiscount ---

    @Test
    void GivenDateRange_WhenWithinRange_ThenDiscountApplied() {
        assertEquals(80.0, new DateRangeDiscount(20, PAST_DT, FUTURE_DT).apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenDateRange_WhenOutsideRange_ThenNoPriceChange() {
        assertEquals(100.0, new DateRangeDiscount(20, PAST_DT.minusDays(1), PAST_DT).apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenNullDates_WhenConstructing_ThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new DateRangeDiscount(20, null, FUTURE_DT));
    }

    @Test
    void GivenEndBeforeStart_WhenConstructing_ThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new DateRangeDiscount(20, FUTURE_DT, PAST_DT));
    }

    @Test
    void GivenInvalidPercentage_WhenConstructingDateRangeDiscount_ThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new DateRangeDiscount(-10, PAST_DT, FUTURE_DT));
    }

    @Test
    void GivenValidDateRangeDiscount_WhenIsValid_ThenReturnTrue() {
        assertTrue(new DateRangeDiscount(20, PAST_DT, FUTURE_DT).isValid());
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

    // --- SumDiscountPolicy ---

    @Test
    void GivenEmptySumPolicy_WhenApply_ThenNoPriceChange() {
        assertEquals(100.0, new SumDiscountPolicy().apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenTwoVisualDiscounts_WhenSumApply_ThenReductionsSummed() {
        SumDiscountPolicy policy = new SumDiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE)); // 10 reduction
        policy.addDiscount(new VisualDiscount(20, FUTURE)); // 20 reduction
        assertEquals(70.0, policy.apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenSumPolicyWithCoupon_WhenApply_ThenBothApplied() {
        SumDiscountPolicy policy = new SumDiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));
        policy.addDiscount(new CodeCoupun("VIP20", 20, FUTURE));
        assertEquals(70.0, policy.apply(100.0, 1, "VIP20"), 0.001);
    }

    @Test
    void GivenSumPolicyWithMinQuantity_WhenQuantityMet_ThenDiscountIncluded() {
        SumDiscountPolicy policy = new SumDiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));
        policy.addDiscount(new MinQuantityDiscount(20, 3));
        assertEquals(70.0, policy.apply(100.0, 3, null), 0.001);
    }

    @Test
    void GivenSumPolicyWithMinQuantity_WhenQuantityNotMet_ThenOnlyVisualApplied() {
        SumDiscountPolicy policy = new SumDiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));
        policy.addDiscount(new MinQuantityDiscount(20, 3));
        assertEquals(90.0, policy.apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenDiscountRemoved_WhenSumApply_ThenRemovedIgnored() {
        SumDiscountPolicy policy = new SumDiscountPolicy();
        VisualDiscount d1 = new VisualDiscount(10, FUTURE);
        VisualDiscount d2 = new VisualDiscount(20, FUTURE);
        policy.addDiscount(d1);
        policy.addDiscount(d2);
        policy.removeDiscount(d1);
        assertEquals(80.0, policy.apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenInvalidDiscount_WhenAddToSumPolicy_ThenThrows() {
        SumDiscountPolicy policy = new SumDiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));
        assertThrows(IllegalArgumentException.class, () -> policy.addDiscount(new VisualDiscount(-5, FUTURE)));
    }

    // --- MaxDiscountPolicy ---

    @Test
    void GivenEmptyMaxPolicy_WhenApply_ThenNoPriceChange() {
        assertEquals(100.0, new MaxDiscountPolicy().apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenTwoDiscounts_WhenMaxApply_ThenOnlyBestApplied() {
        MaxDiscountPolicy policy = new MaxDiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE)); // 90
        policy.addDiscount(new VisualDiscount(20, FUTURE)); // 80 → best
        assertEquals(80.0, policy.apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenMaxPolicyWithCoupon_WhenCouponIsBest_ThenCouponApplied() {
        MaxDiscountPolicy policy = new MaxDiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));
        policy.addDiscount(new CodeCoupun("VIP30", 30, FUTURE)); // 70 → best
        assertEquals(70.0, policy.apply(100.0, 1, "VIP30"), 0.001);
    }

    @Test
    void GivenMaxPolicyWithExpiredCoupon_WhenVisualIsBest_ThenVisualApplied() {
        MaxDiscountPolicy policy = new MaxDiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));           // 90 → best
        policy.addDiscount(new CodeCoupun("VIP30", 30, PAST));        // expired → 100
        assertEquals(90.0, policy.apply(100.0, 1, "VIP30"), 0.001);
    }

    @Test
    void GivenMaxPolicyWithMaxQuantity_WhenQuantityWithinLimit_ThenMaxQuantityIsBest() {
        MaxDiscountPolicy policy = new MaxDiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));   // 90
        policy.addDiscount(new MaxQuantityDiscount(30, 5));   // 70 → best (qty=1 <= 5)
        assertEquals(70.0, policy.apply(100.0, 1, null), 0.001);
    }

    @Test
    void GivenMaxPolicyWithMaxQuantity_WhenQuantityExceedsLimit_ThenVisualIsBest() {
        MaxDiscountPolicy policy = new MaxDiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, FUTURE));   // 90 → best
        policy.addDiscount(new MaxQuantityDiscount(30, 5));   // qty=6 > 5, no discount → 100
        assertEquals(90.0, policy.apply(100.0, 6, null), 0.001);
    }
}
