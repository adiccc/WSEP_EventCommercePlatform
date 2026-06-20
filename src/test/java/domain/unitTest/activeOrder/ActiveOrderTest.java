package domain.unitTest.activeOrder;

import static org.junit.jupiter.api.Assertions.*;

import domain.activeOrder.ActiveOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class ActiveOrderTest {

    private static final int CHECKOUT_TIMEOUT_MINUTES = 10;

    @Test
    void GivenOrderStillSelectingTickets_WhenGetRemainingCheckoutSeconds_ThenZeroReturned() {
        ActiveOrder order = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of()
        );

        long remainingSeconds =
                order.getRemainingCheckoutSeconds(LocalDateTime.now(), CHECKOUT_TIMEOUT_MINUTES);

        assertEquals(0, remainingSeconds);
    }

    @Test
    void GivenOrderInCheckout_WhenGetRemainingCheckoutSeconds_ThenPositiveSecondsReturned() {
        ActiveOrder order = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101, 102)
        );

        order.proceedToCheckout();

        long remainingSeconds =
                order.getRemainingCheckoutSeconds(LocalDateTime.now(), CHECKOUT_TIMEOUT_MINUTES);

        assertTrue(remainingSeconds > 0);
        assertTrue(remainingSeconds <= 600);
    }

    @Test
    void GivenOrderInEditMode_WhenGetRemainingCheckoutSeconds_ThenTimerStillRuns() {
        ActiveOrder order = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101)
        );

        order.proceedToCheckout();
        order.returnToEditSelection();

        long remainingSeconds =
                order.getRemainingCheckoutSeconds(LocalDateTime.now(), CHECKOUT_TIMEOUT_MINUTES);

        assertTrue(remainingSeconds > 0);
        assertTrue(remainingSeconds <= 600);
    }

    @Test
    void GivenOrderReturnedFromPaymentToCheckout_WhenGetRemainingCheckoutSeconds_ThenTimerStillRuns() {
        ActiveOrder order = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101)
        );

        order.proceedToCheckout();
        order.startPayment();
        order.returnToCheckout();

        long remainingSeconds =
                order.getRemainingCheckoutSeconds(LocalDateTime.now(), CHECKOUT_TIMEOUT_MINUTES);

        assertTrue(remainingSeconds > 0);
        assertTrue(remainingSeconds <= 600);
    }

    @Test
    void GivenExpiredCheckoutOrder_WhenGetRemainingCheckoutSeconds_ThenZeroReturned() {
        ActiveOrder order = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101)
        );

        order.proceedToCheckout();
        order.forceExpireForTest(LocalDateTime.now(), CHECKOUT_TIMEOUT_MINUTES);

        long remainingSeconds =
                order.getRemainingCheckoutSeconds(LocalDateTime.now(), CHECKOUT_TIMEOUT_MINUTES);

        assertEquals(0, remainingSeconds);
    }

    @Test
    void GivenPaymentInProgressOrder_WhenGetRemainingCheckoutSeconds_ThenZeroReturned() {
        ActiveOrder order = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101)
        );

        order.proceedToCheckout();
        order.startPayment();

        long remainingSeconds =
                order.getRemainingCheckoutSeconds(LocalDateTime.now(), CHECKOUT_TIMEOUT_MINUTES);

        assertEquals(0, remainingSeconds);
    }

    @Test
    void GivenSameObject_WhenEquals_ThenTrue() {
        ActiveOrder order = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101)
        );

        assertEquals(order, order);
    }

    @Test
    void GivenNull_WhenEquals_ThenFalse() {
        ActiveOrder order = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101)
        );

        assertNotEquals(order, null);
    }

    @Test
    void GivenDifferentClass_WhenEquals_ThenFalse() {
        ActiveOrder order = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101)
        );

        assertNotEquals(order, "not-active-order");
    }

    @Test
    void GivenSameOrderIdAndSameVersion_WhenEquals_ThenTrue() {
        ActiveOrder order1 = new ActiveOrder(
                1,
                "first@mail.com",
                10,
                List.of(101)
        );

        ActiveOrder order2 = new ActiveOrder(
                1,
                "second@mail.com",
                20,
                List.of(202)
        );

        assertEquals(order1, order2);
    }

    @Test
    void GivenSameOrderIdButDifferentVersion_WhenEquals_ThenFalse() {
        ActiveOrder order1 = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101)
        );

        ActiveOrder order2 = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101)
        );

        order2.setVersion(1);

        assertNotEquals(order1, order2);
    }

    @Test
    void GivenDifferentOrderIdAndSameVersion_WhenEquals_ThenFalse() {
        ActiveOrder order1 = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101)
        );

        ActiveOrder order2 = new ActiveOrder(
                2,
                "user@mail.com",
                10,
                List.of(101)
        );

        assertNotEquals(order1, order2);
    }

    @Test
    void GivenCopiedActiveOrder_WhenEquals_ThenTrue() {
        ActiveOrder original = new ActiveOrder(
                1,
                "user@mail.com",
                10,
                List.of(101, 102)
        );

        ActiveOrder copy = new ActiveOrder(original);

        assertEquals(original, copy);
    }
}