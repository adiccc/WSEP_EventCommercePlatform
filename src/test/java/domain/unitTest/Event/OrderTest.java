package domain.unitTest.Event;

import org.junit.jupiter.api.Test;
import DTO.PurchasedTicketDTO;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import domain.event.Order;
import domain.event.OrderStatus;
class OrderTest {

    @Test
    void GivenOrderInRefundRequired_WhenCanBeRefunded_ThenReturnTrue() {
        Order order = new Order(
                1,
                "10",
                1,
                "Test Event",
                "2026-01-01T20:00",
                "TEL_AVIV",
                List.of(
                        new PurchasedTicketDTO(
                                1,
                                "VIP",
                                "SEATING",
                                1,
                                1,
                                50.0
                        ),
                        new PurchasedTicketDTO(
                                2,
                                "VIP",
                                "SEATING",
                                1,
                                2,
                                50.0
                        )
                ),
                List.of(1, 2),
                100.0,
                "pay123",
                new ArrayList<>()
        );
        order.markRefundRequired();

        assertTrue(order.canBeRefunded());
    }
    @Test
    void GivenOrderNotInRefundRequired_WhenCanBeRefunded_ThenReturnFalse() {
        Order order = new Order(
                1,
                "10",
                1,
                "Test Event",
                "2026-01-01T20:00",
                "TEL_AVIV",
                List.of(
                        new PurchasedTicketDTO(
                                1,
                                "VIP",
                                "SEATING",
                                1,
                                1,
                                50.0
                        ),
                        new PurchasedTicketDTO(
                                2,
                                "VIP",
                                "SEATING",
                                1,
                                2,
                                50.0
                        )
                ),
                List.of(1, 2),
                100.0,
                "pay123",
                new ArrayList<>()
        );        // As default order in status APPROVED (not REFUND_REQUIRED)
        assertFalse(order.canBeRefunded());
    }

    @Test
    void GivenOrderInRefundRequired_WhenMarkRefunded_ThenStatusIsRefunded() {
        Order order = new Order(
                1,
                "10",
                1,
                "Test Event",
                "2026-01-01T20:00",
                "TEL_AVIV",
                List.of(
                        new PurchasedTicketDTO(
                                1,
                                "VIP",
                                "SEATING",
                                1,
                                1,
                                50.0
                        ),
                        new PurchasedTicketDTO(
                                2,
                                "VIP",
                                "SEATING",
                                1,
                                2,
                                50.0
                        )
                ),
                List.of(1, 2),
                100.0,
                "pay123",
                new ArrayList<>()
        );
        order.markRefundRequired();
        order.markRefunded();

        assertEquals(OrderStatus.REFUNDED, order.getStatus());
    }
}