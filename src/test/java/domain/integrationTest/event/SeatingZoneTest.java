package domain.integrationTest.event;

import domain.dataType.ElementPosition;
import domain.event.SeatingTicket;
import domain.event.SeatingZone;
import DTO.SeatingTicketDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SeatingZoneTest {

    private SeatingZone seatingZone;

    @BeforeEach
    void setUp() {
        seatingZone = new SeatingZone("zone A", 50.0, 2, 3, new ElementPosition(0, 0));
        assignTicketIds(seatingZone);
    }

    private void assignTicketIds(SeatingZone seatingZone) {
        int id = 1;

        for (SeatingTicket ticket : seatingZone.getTicketMap().values()) {
            if (ticket.getId() == null) {
                ticket.setId(id++);
            }
        }
    }

    @Test
    void GivenZoneWithAvailableSeats_WhenBookTickets_ThenSeatsBecomeLocked() {
        List<SeatingTicketDTO> ticketsToBook = List.of(
                new SeatingTicketDTO(0, 0),
                new SeatingTicketDTO(0, 1)
        );

        seatingZone.bookTickets(ticketsToBook);

        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.bookTickets(List.of(new SeatingTicketDTO(0, 0))),
                "Booked seat should be locked");

        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.bookTickets(List.of(new SeatingTicketDTO(0, 1))),
                "Booked seat should be locked");
    }



    @Test
    void GivenZoneWhenBookingAllSeats_WhenBookTickets_ThenAllSeatsBecomeLocked() {
        List<SeatingTicketDTO> allSeats = List.of(
                new SeatingTicketDTO(0, 0), new SeatingTicketDTO(0, 1), new SeatingTicketDTO(0, 2),
                new SeatingTicketDTO(1, 0), new SeatingTicketDTO(1, 1), new SeatingTicketDTO(1, 2)
        );

        seatingZone.bookTickets(allSeats);

        for (SeatingTicketDTO seat : allSeats) {
            assertThrows(IllegalArgumentException.class,
                    () -> seatingZone.bookTickets(List.of(seat)),
                    "Booked seat should be locked");
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

    @Test
    void GivenSingleSeat_WhenFindSeatingTicketIds_ThenReturnsOneId() {
        Collection<Integer> result = seatingZone.findSeatingTicketIds(
                List.of(new SeatingTicketDTO(0, 0)));

        assertEquals(1, result.size());
    }

    @Test
    void GivenAllSeatsRequested_WhenFindSeatingTicketIds_ThenReturnsAllSixDistinct() {
        List<SeatingTicketDTO> all = new ArrayList<>();
        for (int r = 0; r < 2; r++)
            for (int c = 0; c < 3; c++)
                all.add(new SeatingTicketDTO(r, c));

        Collection<Integer> result = seatingZone.findSeatingTicketIds(all);

        assertEquals(6, result.size());
    }

    @Test
    void GivenMultipleValidSeats_WhenFindSeatingTicketIds_ThenReturnsMatchingIds() {
        Collection<Integer> result = seatingZone.findSeatingTicketIds(List.of(
                new SeatingTicketDTO(0, 1),
                new SeatingTicketDTO(1, 2)));

        assertEquals(2, result.size());
    }

    @Test
    void GivenReturnedIdsMatchTicketMap_WhenFindSeatingTicketIds_ThenIdsCorrespondToCorrectSeats() {
        // Stronger assertion: the returned IDs must equal what the ticketMap holds for those seats.
        int expectedId00 = seatingZone.getTicketMap().get("0-0").getTicketId();
        int expectedId12 = seatingZone.getTicketMap().get("1-2").getTicketId();

        Collection<Integer> result = seatingZone.findSeatingTicketIds(List.of(
                new SeatingTicketDTO(0, 0),
                new SeatingTicketDTO(1, 2)));

        assertTrue(result.contains(expectedId00));
        assertTrue(result.contains(expectedId12));
    }

    @Test
    void GivenEmptyInput_WhenFindSeatingTicketIds_ThenReturnsEmpty() {
        Collection<Integer> result = seatingZone.findSeatingTicketIds(List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void GivenSeatOutsideRows_WhenFindSeatingTicketIds_ThenThrows() {
        // zone is 2 rows (0,1), so row 2 doesn't exist
        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.findSeatingTicketIds(List.of(new SeatingTicketDTO(2, 0))));
    }

    @Test
    void GivenSeatOutsideCols_WhenFindSeatingTicketIds_ThenThrows() {
        // zone is 3 cols (0,1,2), so col 3 doesn't exist
        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.findSeatingTicketIds(List.of(new SeatingTicketDTO(0, 3))));
    }

    @Test
    void GivenNegativeCoordinates_WhenFindSeatingTicketIds_ThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.findSeatingTicketIds(List.of(new SeatingTicketDTO(-1, 0))));
    }

    @Test
    void GivenMixOfValidAndInvalid_WhenFindSeatingTicketIds_ThenThrowsAndReportsAll() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> seatingZone.findSeatingTicketIds(List.of(
                        new SeatingTicketDTO(0, 0),    // valid
                        new SeatingTicketDTO(99, 99))));// invalid
        assertTrue(ex.getMessage().toLowerCase().contains("not found"),
                "got: " + ex.getMessage());
    }

    @Test
    void GivenDuplicateSeats_WhenFindSeatingTicketIds_ThenReturnsDuplicateIds() {
        // Honest test of current behavior: the method does not de-duplicate.
        // Asking for the same seat twice returns its ID twice, and size matches input size,
        // so no exception is thrown. If you decide that's a bug, change to assertThrows.
        Collection<Integer> result = seatingZone.findSeatingTicketIds(List.of(
                new SeatingTicketDTO(0, 0),
                new SeatingTicketDTO(0, 0)));

        assertEquals(2, result.size());
        assertEquals(1, new HashSet<>(result).size(), "both entries should resolve to the same ID");
    }

    @Test
    void GivenSoldSeats_WhenReleaseTickets_ThenSeatsStaySoldAndNotAvailable() {
        List<SeatingTicketDTO> seats = List.of(
                new SeatingTicketDTO(0, 0),
                new SeatingTicketDTO(0, 1)
        );

        Collection<Integer> lockedIds = seatingZone.bookTickets(seats);

        seatingZone.markTicketsAsSold(new ArrayList<>(lockedIds));

        seatingZone.releaseTickets(new ArrayList<>(lockedIds));

        // try to rebook → should fail because seats are SOLD
        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.bookTickets(seats),
                "Sold seats must not become available after releaseTickets");

        // verify they are still SOLD
        for (Integer ticketId : lockedIds) {

            boolean foundSoldSeat = false;

            for (SeatingTicket ticket : seatingZone.getTicketMap().values()) {
                if (ticket.getTicketId() == ticketId &&
                        ticket.getStatus() == domain.dataType.TicketStatus.SOLD) {
                    foundSoldSeat = true;
                    break;
                }
            }

            assertTrue(foundSoldSeat,
                    "Seat " + ticketId + " must remain SOLD after releaseTickets");
        }
    }

    @Test
    void GivenLockedSeats_WhenMarkTicketsAsSold_ThenSeatsBecomeSold() {
        List<SeatingTicketDTO> seats = List.of(
                new SeatingTicketDTO(0, 0),
                new SeatingTicketDTO(0, 1)
        );

        Collection<Integer> lockedIds = seatingZone.bookTickets(seats);
        List<Integer> ticketIds = new ArrayList<>(lockedIds);

        seatingZone.markTicketsAsSold(ticketIds);

        for (Integer ticketId : ticketIds) {
            boolean foundSoldSeat = false;

            for (SeatingTicket ticket : seatingZone.getTicketMap().values()) {
                if (ticket.getTicketId() == ticketId &&
                        ticket.getStatus() == domain.dataType.TicketStatus.SOLD) {
                    foundSoldSeat = true;
                    break;
                }
            }

            assertTrue(foundSoldSeat,
                    "Locked seat " + ticketId + " should become SOLD");
        }
    }

    @Test
    void GivenSoldSeats_WhenReleaseTickets_ThenSeatsStaySoldAndNotBookable() {
        List<SeatingTicketDTO> seats = List.of(
                new SeatingTicketDTO(0, 0),
                new SeatingTicketDTO(0, 1)
        );

        Collection<Integer> lockedIds = seatingZone.bookTickets(seats);
        List<Integer> ticketIds = new ArrayList<>(lockedIds);

        seatingZone.markTicketsAsSold(ticketIds);

        seatingZone.releaseTickets(ticketIds);

        for (Integer ticketId : ticketIds) {
            boolean foundSoldSeat = false;

            for (SeatingTicket ticket : seatingZone.getTicketMap().values()) {
                if (ticket.getTicketId() == ticketId &&
                        ticket.getStatus() == domain.dataType.TicketStatus.SOLD) {
                    foundSoldSeat = true;
                    break;
                }
            }

            assertTrue(foundSoldSeat,
                    "Sold seat " + ticketId + " should remain SOLD after releaseTickets");
        }

        assertThrows(IllegalArgumentException.class,
                () -> seatingZone.bookTickets(seats),
                "Sold seats must not become bookable again");
    }
}
