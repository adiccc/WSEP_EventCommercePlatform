package domain.integrationTest.event;

import domain.dataType.ElementPosition;
import domain.event.SeatingTicket;
import domain.event.SeatingZone;
import domain.dto.SeatingTicketDTO; // ייבוא ה-DTO הנכון
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SeatingZoneTest {

    private SeatingZone seatingZone;

    @BeforeEach
    void setUp() {
        seatingZone = new SeatingZone("zone A", 50.0, 2, 3, new ElementPosition(0, 0), new java.util.concurrent.atomic.AtomicInteger(1));
    }

    @Test
    void GivenZoneWithAvailableSeats_WhenBookTickets_ThenReturnsTicketIds() {
        // Arrange
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

    @Test
    void GivenLockedSeats_WhenReleaseTickets_ThenSeatsBecomeBookableAgain() {
        Collection<Integer> lockedIds = seatingZone.bookTickets(
                List.of(new SeatingTicketDTO(0, 0), new SeatingTicketDTO(0, 1)));

        seatingZone.releaseTickets(new ArrayList<>(lockedIds));

        // round-trip proof — if this throws, release didn't work
        assertDoesNotThrow(() -> seatingZone.bookTickets(
                        List.of(new SeatingTicketDTO(0, 0), new SeatingTicketDTO(0, 1))),
                "Released seats must be bookable again");
    }
    @Test
    void GivenSubsetOfLockedSeats_WhenReleaseTickets_ThenOnlyThoseAreReleased() {
        List<Integer> lockedIds = new ArrayList<>(seatingZone.bookTickets(
                List.of(new SeatingTicketDTO(0, 0),
                        new SeatingTicketDTO(0, 1),
                        new SeatingTicketDTO(1, 0))));

        seatingZone.releaseTickets(List.of(lockedIds.get(0)));  // release only the first

        // released seat should be bookable again
        assertDoesNotThrow(() -> seatingZone.bookTickets(List.of(new SeatingTicketDTO(0, 0))),
                "Released seat should be bookable");

        // non-released seats should still throw on rebook
        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.bookTickets(List.of(new SeatingTicketDTO(0, 1))),
                "Non-released seat must remain locked");
        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.bookTickets(List.of(new SeatingTicketDTO(1, 0))),
                "Non-released seat must remain locked");
    }

    @Test
    void GivenUnknownTicketIds_WhenReleaseTickets_ThenNoChangeAndNoException() {
        Collection<Integer> lockedIds = seatingZone.bookTickets(
                List.of(new SeatingTicketDTO(0, 0)));

        assertDoesNotThrow(() -> seatingZone.releaseTickets(List.of(9999, 8888)));

        // the originally-booked seat must still be locked
        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.bookTickets(List.of(new SeatingTicketDTO(0, 0))),
                "Original lock must not be affected by unknown-id release");
    }

    @Test
    void GivenMixOfKnownAndUnknownIds_WhenReleaseTickets_ThenOnlyKnownAreReleased() {
        List<Integer> lockedIds = new ArrayList<>(seatingZone.bookTickets(
                List.of(new SeatingTicketDTO(0, 0), new SeatingTicketDTO(0, 1))));

        seatingZone.releaseTickets(List.of(lockedIds.get(0), 9999));

        // first seat should be released
        assertDoesNotThrow(() -> seatingZone.bookTickets(List.of(new SeatingTicketDTO(0, 0))));
        // second seat should still be locked
        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.bookTickets(List.of(new SeatingTicketDTO(0, 1))));
    }

    @Test
    void GivenEmptyTicketList_WhenReleaseTickets_ThenNoChange() {
        seatingZone.bookTickets(List.of(new SeatingTicketDTO(0, 0)));

        assertDoesNotThrow(() -> seatingZone.releaseTickets(List.of()));

        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.bookTickets(List.of(new SeatingTicketDTO(0, 0))),
                "Empty release must not unlock anything");
    }

    @Test
    void GivenAlreadyReleasedSeat_WhenReleaseAgain_ThenIdempotent() {
        Collection<Integer> lockedIds = seatingZone.bookTickets(
                List.of(new SeatingTicketDTO(0, 0)));
        List<Integer> ids = new ArrayList<>(lockedIds);

        seatingZone.releaseTickets(ids);
        assertDoesNotThrow(() -> seatingZone.releaseTickets(ids),
                "Releasing the same ticket twice must not throw");

        // and the seat is in fact AVAILABLE
        assertDoesNotThrow(() -> seatingZone.bookTickets(List.of(new SeatingTicketDTO(0, 0))));
    }

    @Test
    void GivenAllSeatsLocked_WhenReleaseAll_ThenZoneFullyAvailable() {
        List<SeatingTicketDTO> allSeats = new ArrayList<>();
        for (int i = 0; i < seatingZone.getRows(); i++) {
            for (int j = 0; j < seatingZone.getCols(); j++) {
                allSeats.add(new SeatingTicketDTO(i, j));
            }
        }

        Collection<Integer> lockedIds = seatingZone.bookTickets(allSeats);
        seatingZone.releaseTickets(new ArrayList<>(lockedIds));

        // all seats should be re-bookable in one shot
        assertDoesNotThrow(() -> seatingZone.bookTickets(allSeats),
                "Fully released zone must be fully re-bookable");
    }

    @Test
    void GivenReleasedSeats_WhenLockAgain_ThenSameSeatsCanBeBooked() {
        List<SeatingTicketDTO> firstBatch = List.of(
                new SeatingTicketDTO(0, 0),
                new SeatingTicketDTO(1, 2));

        Collection<Integer> firstLock = seatingZone.bookTickets(firstBatch);
        seatingZone.releaseTickets(new ArrayList<>(firstLock));

        Collection<Integer> secondLock = seatingZone.bookTickets(firstBatch);
        assertEquals(2, secondLock.size(),
                "After release, exactly the same seats must be lockable again");
    }
}