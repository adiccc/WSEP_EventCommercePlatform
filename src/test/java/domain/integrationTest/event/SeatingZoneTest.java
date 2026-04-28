package domain.integrationTest.event;

import domain.dataType.ElementPosition;
import domain.event.SeatingZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeatingZoneTest {

    private SeatingZone seatingZone;

    @BeforeEach void setUp() {
        seatingZone = new SeatingZone("zone A",50.0,2,3, new ElementPosition(0,0));
    }

    @Test
    void GivenZoneWithAvailableSeats_WhenBookTickets_ThenReturnsTicketIds() {
        // Act
        List<Integer> bookedIds = (List<Integer>) seatingZone.bookTickets(List.of("0-0", "0-1"));

        // Assert assertNotNull(bookedIds);
        assertEquals(2, bookedIds.size());
        assertTrue(bookedIds.contains(1));
        assertTrue(bookedIds.contains(2));
    }

    @Test
    void GivenZoneWhenBookingAllSeats_WhenBookTickets_ThenReturnsAllTicketIds() {
        // Act
        List<Integer> bookedIds = (List<Integer>) seatingZone.bookTickets(
        List.of("0-0", "0-1", "0-2", "1-0", "1-1", "1-2"));

        // Assert assertNotNull(bookedIds);
        assertEquals(6, bookedIds.size());
        assertTrue(bookedIds.contains(1));
        assertTrue(bookedIds.contains(2));
        assertTrue(bookedIds.contains(3));
        assertTrue(bookedIds.contains(4));
        assertTrue(bookedIds.contains(5));
        assertTrue(bookedIds.contains(6));
    }

    @Test
    void GivenSeatAlreadyBooked_WhenBookTickets_ThenThrowsIllegalArgumentException() {
        // Arrange
        seatingZone.bookTickets(List.of("1-1"));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(

        IllegalArgumentException.class,
                () -> seatingZone.bookTickets(List.of("1-1")));

        assertEquals("Seat 1-1 is not available.", exception.getMessage());
    }
}
