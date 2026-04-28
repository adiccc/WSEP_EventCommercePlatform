package domain.integrationTest.event;

import domain.dataType.ElementPosition;
import domain.event.StandingZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StandingZoneTest {

    private StandingZone standingZone;
    private final int USER_ID = 123;

    @BeforeEach
    void setUp() {
        standingZone = new StandingZone("zone A", 50.0,5, new ElementPosition(0, 0));
    }

    @Test
    void GivenZoneWithEnoughAvailableTickets_WhenBookTickets_ThenReturnsTicketsAndUpdatesAvailability() {
        // Act
        List<Integer> bookedIds = standingZone.bookTickets(2);
        // Assert
        assertNotNull(bookedIds);
        assertEquals(2, bookedIds.size());
        assertEquals(3, standingZone.getAvaliable());
        assertTrue(bookedIds.contains(1));
        assertTrue(bookedIds.contains(2));
    }

    @Test
    void GivenZoneWithExactlyAvailableTickets_WhenBookTickets_ThenReturnsTicketsAndZoneIsFull() {
        // Act
        List<Integer> bookedIds = standingZone.bookTickets(5);

        // Assert
        assertEquals(5, bookedIds.size());
        assertEquals(0, standingZone.getAvaliable());
    }

    @Test
    void GivenZoneWithNotEnoughAvailableTickets_WhenBookTickets_ThenThrowsIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> standingZone.bookTickets(6)
        );

        assertEquals("Not enough tickets available in this zone.", exception.getMessage());
        assertEquals(5, standingZone.getAvaliable()); // מוודאים שהכמות לא השתנתה בעקבות הכישלון
    }
}
