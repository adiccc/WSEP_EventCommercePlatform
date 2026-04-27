package domain.unitTest.Event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import domain.event.Order;
import domain.event.OrderStatus;
class OrderTest {

    @Test
    void GivenOrderInRefundRequired_WhenCanBeRefunded_ThenReturnTrue() {
        Order order = new Order(1, 10, "event1", List.of(1,2), 100.0, "pay123");

        order.markRefundRequired();

        assertTrue(order.canBeRefunded());
    }
    @Test
    void GivenOrderNotInRefundRequired_WhenCanBeRefunded_ThenReturnFalse() {
        Order order = new Order(1, 10, "event1", List.of(1,2), 100.0, "pay123");
        // As default order in status APPROVED (not REFUND_REQUIRED)
        assertFalse(order.canBeRefunded());
    }

    @Test
    void GivenOrderInRefundRequired_WhenMarkRefunded_ThenStatusIsRefunded() {
        Order order = new Order(1, 10, "event1", List.of(1,2), 100.0, "pay123");

        order.markRefundRequired();
        order.markRefunded();

        assertEquals(OrderStatus.REFUNDED, order.getStatus());
    }
}