package domain.integrationTest.event;

import domain.dataType.ElementPosition;
import domain.event.SeatingZone;
import domain.dto.SeatingTicketDTO; // ייבוא ה-DTO הנכון
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeatingZoneTest {

    private SeatingZone seatingZone;

    @BeforeEach
    void setUp() {
        seatingZone = new SeatingZone("zone A", 50.0, 2, 3, new ElementPosition(0, 0));
    }

    @Test
    void GivenZoneWithAvailableSeats_WhenBookTickets_ThenReturnsTicketIds() {
        // Arrange
        // שימוש ב-SeatingTicketDTO במקום String
        List<SeatingTicketDTO> ticketsToBook = List.of(
                new SeatingTicketDTO(0, 0),
                new SeatingTicketDTO(0, 1)
        );

        // Act
        List<Integer> bookedIds = (List<Integer>) seatingZone.bookTickets(ticketsToBook);

        // Assert
        assertNotNull(bookedIds);
        assertEquals(2, bookedIds.size());
        assertTrue(bookedIds.contains(1));
        assertTrue(bookedIds.contains(2));
    }

    @Test
    void GivenZoneWhenBookingAllSeats_WhenBookTickets_ThenReturnsAllTicketIds() {
        // Arrange
        List<SeatingTicketDTO> allSeats = List.of(
                new SeatingTicketDTO(0, 0), new SeatingTicketDTO(0, 1), new SeatingTicketDTO(0, 2),
                new SeatingTicketDTO(1, 0), new SeatingTicketDTO(1, 1), new SeatingTicketDTO(1, 2)
        );

        // Act
        List<Integer> bookedIds = (List<Integer>) seatingZone.bookTickets(allSeats);

        // Assert
        assertNotNull(bookedIds);
        assertEquals(6, bookedIds.size());
        for (int i = 1; i <= 6; i++) {
            assertTrue(bookedIds.contains(i));
        }
    }

    @Test
    void GivenSeatAlreadyBooked_WhenBookTickets_ThenThrowsIllegalArgumentException() {
        // Arrange
        SeatingTicketDTO ticket = new SeatingTicketDTO(1, 1);
        seatingZone.bookTickets(List.of(ticket));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> seatingZone.bookTickets(List.of(ticket))
        );

        assertEquals("Seat 1-1 is not available.", exception.getMessage());
    }
}