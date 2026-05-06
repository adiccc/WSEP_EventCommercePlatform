package domain.integrationTest.event;

import domain.dataType.ElementPosition;
import domain.event.StandingTicket;
import domain.event.StandingZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StandingZoneTest {

    private StandingZone standingZone;
    private final int USER_ID = 123;

    @BeforeEach
    void setUp() {
        standingZone = new StandingZone("zone A", 50.0,5, new ElementPosition(0, 0), new java.util.concurrent.atomic.AtomicInteger(1));
    }

    @Test
    void GivenZoneWithEnoughAvailableTickets_WhenBookTickets_ThenReturnsTicketsAndUpdatesAvailability() {
        // Act
        List<Integer> bookedIds = standingZone.bookTickets(2);
        // Assert
        assertNotNull(bookedIds);
        assertEquals(2, bookedIds.size());
        assertEquals(3, standingZone.getAvailable());
        assertTrue(bookedIds.contains(1));
        assertTrue(bookedIds.contains(2));
    }

    @Test
    void GivenZoneWithExactlyAvailableTickets_WhenBookTickets_ThenReturnsTicketsAndZoneIsFull() {
        // Act
        List<Integer> bookedIds = standingZone.bookTickets(5);

        // Assert
        assertEquals(5, bookedIds.size());
        assertEquals(0, standingZone.getAvailable());
    }

    @Test
    void GivenZoneWithNotEnoughAvailableTickets_WhenBookTickets_ThenThrowsIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> standingZone.bookTickets(6)
        );

        assertEquals("Not enough tickets available in this zone.", exception.getMessage());
        assertEquals(5, standingZone.getAvailable());
    }

    @Test
    void GivenLockedTickets_WhenReleaseTickets_ThenTicketsBecomeAvailable() {
        List<Integer> lockedIds = standingZone.bookTickets(3);

        standingZone.releaseTickets(lockedIds);

        assertEquals(5, standingZone.getAvailable(),
                "All released tickets should restore full capacity");
        assertTrue(standingZone.getOccupiedTickets().isEmpty(),
                "Released tickets must be removed from the occupied collection");
    }

    @Test
    void GivenSubsetOfLockedTickets_WhenReleaseTickets_ThenOnlyThoseAreReleased() {
        List<Integer> lockedIds = standingZone.bookTickets(4);
        List<Integer> toRelease = List.of(lockedIds.get(0), lockedIds.get(2));

        standingZone.releaseTickets(toRelease);

        assertEquals(3, standingZone.getAvailable(),
                "Two tickets released → capacity should be 1 + 2 = 3");
        assertEquals(2, standingZone.getOccupiedTickets().size(),
                "Two tickets must still be locked");
    }

    @Test
    void GivenUnknownTicketIds_WhenReleaseTickets_ThenNoChangeAndNoException() {
        standingZone.bookTickets(2);
        int availableBefore = standingZone.getAvailable();
        int occupiedBefore = standingZone.getOccupiedTickets().size();

        assertDoesNotThrow(() -> standingZone.releaseTickets(List.of(9999, 8888)));

        assertEquals(availableBefore, standingZone.getAvailable(),
                "Available count must not change when releasing unknown ids");
        assertEquals(occupiedBefore, standingZone.getOccupiedTickets().size(),
                "Occupied set must not change when releasing unknown ids");
    }

    @Test
    void GivenMixOfKnownAndUnknownIds_WhenReleaseTickets_ThenOnlyKnownAreReleased() {
        List<Integer> lockedIds = standingZone.bookTickets(2);
        List<Integer> toRelease = List.of(lockedIds.get(0), 9999);

        standingZone.releaseTickets(toRelease);

        assertEquals(4, standingZone.getAvailable(),
                "Only the one known ticket should have been released");
        assertEquals(1, standingZone.getOccupiedTickets().size(),
                "Unknown id must not affect the occupied set");
    }

    @Test
    void GivenEmptyTicketList_WhenReleaseTickets_ThenNoChange() {
        standingZone.bookTickets(2);
        int availableBefore = standingZone.getAvailable();
        int occupiedBefore = standingZone.getOccupiedTickets().size();

        standingZone.releaseTickets(List.of());

        assertEquals(availableBefore, standingZone.getAvailable());
        assertEquals(occupiedBefore, standingZone.getOccupiedTickets().size());
    }

    @Test
    void GivenAlreadyReleasedTicket_WhenReleaseTicketsAgain_ThenNoDoubleRelease() {
        List<Integer> lockedIds = standingZone.bookTickets(2);
        standingZone.releaseTickets(lockedIds);

        int availableAfterFirst = standingZone.getAvailable();

        standingZone.releaseTickets(lockedIds);

        assertEquals(availableAfterFirst, standingZone.getAvailable(),
                "Releasing the same tickets twice must not exceed capacity");
    }

    @Test
    void GivenAllTicketsLocked_WhenReleaseAll_ThenZoneFullyAvailable() {
        List<Integer> lockedIds = standingZone.bookTickets(5);
        assertEquals(0, standingZone.getAvailable(), "Sanity: all should be locked");

        standingZone.releaseTickets(lockedIds);

        assertEquals(5, standingZone.getAvailable(),
                "All-released zone should be back to full capacity");
        assertTrue(standingZone.getOccupiedTickets().isEmpty(),
                "All-released zone should have no occupied tickets");
    }

    @Test
    void GivenReleasedTickets_WhenLockAgain_ThenSameOrNewTicketsCanBeBooked() {
        List<Integer> firstLock = standingZone.bookTickets(3);
        standingZone.releaseTickets(firstLock);

        List<Integer> secondLock = standingZone.bookTickets(3);

        assertEquals(3, secondLock.size(),
                "After release, the same number of tickets must be lockable again");
        assertEquals(2, standingZone.getAvailable(),
                "After 3-of-5 are locked, capacity should be 2");
    }


    @Test
    void GivenEmptyInput_WhenCountTickets_ThenReturnsZero() {
        assertEquals(0, standingZone.countTickets(List.of()));
    }

    @Test
    void GivenIdsNotInZone_WhenCountTickets_ThenReturnsZero() {
        assertEquals(0, standingZone.countTickets(List.of(999, 1000, 1001)));
    }

    @Test
    void GivenAllAvailableTicketsButNoneBooked_WhenCountTickets_ThenCountsAvailable() {
        assertEquals(0, standingZone.countTickets(List.of(1, 2, 3, 4, 5)));
    }

    @Test
    void GivenSomeBookedTickets_WhenCountTickets_ThenReturnsBookedCount() {
        List<Integer> booked = standingZone.bookTickets(3);

        assertEquals(3, standingZone.countTickets(booked));
    }

    @Test
    void GivenUserHoldsSubsetOfBooked_WhenCountTickets_ThenReturnsSubsetSize() {
        List<Integer> booked = standingZone.bookTickets(3);
        List<Integer> userHolds = booked.subList(0, 2);

        assertEquals(2, standingZone.countTickets(userHolds));
    }

    @Test
    void GivenUserHoldsBookedPlusUnrelated_WhenCountTickets_ThenIgnoresUnrelated() {
        List<Integer> booked = standingZone.bookTickets(2);
        List<Integer> mixed = new ArrayList<>(booked);
        mixed.add(999_999);

        assertEquals(2, standingZone.countTickets(mixed));
    }

    @Test
    void GivenSameIdAppearsTwiceInInput_WhenCountTickets_ThenCountsTwice() {
        List<Integer> booked = standingZone.bookTickets(1);
        Integer id = booked.get(0);

        assertEquals(2, standingZone.countTickets(List.of(id, id)));
    }

    // ===== pickStandingFromZone =====

    @Test
    void GivenUserHoldsAllOccupied_WhenPickStandingFromZone_ThenReturnsRequestedCount() {
        List<Integer> booked = standingZone.bookTickets(5);

        List<Integer> picked = standingZone.pickStandingFromZone(booked, 3);

        assertEquals(3, picked.size());
        assertTrue(booked.containsAll(picked));
        assertEquals(3, new HashSet<>(picked).size(), "picked IDs must be distinct");
    }

    @Test
    void GivenPickAll_WhenPickStandingFromZone_ThenReturnsAll() {
        List<Integer> booked = standingZone.bookTickets(4);

        List<Integer> picked = standingZone.pickStandingFromZone(booked, 4);

        assertEquals(new HashSet<>(booked), new HashSet<>(picked));
    }

    @Test
    void GivenNumToPickIsZero_WhenPickStandingFromZone_ThenReturnsEmpty() {
        List<Integer> booked = standingZone.bookTickets(3);

        List<Integer> picked = standingZone.pickStandingFromZone(booked, 0);

        assertTrue(picked.isEmpty());
    }

    @Test
    void GivenEmptyUserTickets_WhenPickStandingFromZone_ThenThrows() {
        standingZone.bookTickets(3); // populate occupiedTickets, but caller's list is empty

        assertThrows(IllegalArgumentException.class,
                () -> standingZone.pickStandingFromZone(List.of(), 1));
    }

    @Test
    void GivenUserHasFewerOccupiedThanRequested_WhenPickStandingFromZone_ThenThrows() {
        List<Integer> booked = standingZone.bookTickets(2);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> standingZone.pickStandingFromZone(booked, 5));
        assertTrue(ex.getMessage().toLowerCase().contains("not enough"),
                "got: " + ex.getMessage());
    }

    @Test
    void GivenUserListContainsOnlyAvailableIds_WhenPickStandingFromZone_ThenThrows() {
        // Important: pickStandingFromZone only matches against occupiedTickets,
        // unlike countTickets which scans both lists. So a user list of unbooked
        // (still-available) IDs picks nothing and throws.
        // This asymmetry between countTickets and pickStandingFromZone is the
        // exact "internal inconsistency" case I warned about earlier.
        assertThrows(IllegalArgumentException.class,
                () -> standingZone.pickStandingFromZone(List.of(1, 2, 3), 1));
    }

    @Test
    void GivenUserListContainsUnrelatedIds_WhenPickStandingFromZone_ThenSkipsThemButPicksValid() {
        List<Integer> booked = standingZone.bookTickets(3);
        List<Integer> mixed = new ArrayList<>(booked);
        mixed.add(0, 999_999); // unrelated ID at the front

        List<Integer> picked = standingZone.pickStandingFromZone(mixed, 2);

        assertEquals(2, picked.size());
        assertFalse(picked.contains(999_999));
        assertTrue(booked.containsAll(picked));
    }

    @Test
    void GivenUserHoldsExactlyEnough_WhenPickStandingFromZone_ThenReturnsAll() {
        List<Integer> booked = standingZone.bookTickets(3);

        List<Integer> picked = standingZone.pickStandingFromZone(booked, 3);

        assertEquals(3, picked.size());
        assertEquals(new HashSet<>(booked), new HashSet<>(picked));
    }

    @Test
    void GivenZoneIsEmpty_WhenPickStandingFromZoneAsksForOne_ThenThrows() {
        // No bookings at all — occupiedTickets is empty
        assertThrows(IllegalArgumentException.class,
                () -> standingZone.pickStandingFromZone(List.of(1, 2, 3), 1));
    }

    @Test
    void GivenLockedTickets_WhenMarkTicketsAsSold_ThenTicketsBecomeSold() {
        List<Integer> lockedIds = standingZone.bookTickets(2);

        standingZone.markTicketsAsSold(lockedIds);

        // was 5 booked 2
        assertEquals(3, standingZone.getAvailable(),
                "Selling locked tickets must not make them available again");

        assertEquals(2, standingZone.getOccupiedTickets().size(),
                "Sold standing tickets should remain in occupied tickets");

        for (Integer ticketId : lockedIds) {

            boolean foundSoldTicket = false;

            for (StandingTicket ticket : standingZone.getOccupiedTickets()) {
                if (ticket.getTicketId() == ticketId &&
                        ticket.getStatus() == domain.dataType.TicketStatus.SOLD) {
                    foundSoldTicket = true;
                    break;
                }
            }

            assertTrue(foundSoldTicket,
                    "Locked ticket " + ticketId + " should become SOLD");
        }
    }

    @Test
    void GivenSoldTickets_WhenReleaseTickets_ThenTicketsStaySoldAndNotAvailable() {
        List<Integer> lockedIds = standingZone.bookTickets(2);

        standingZone.markTicketsAsSold(lockedIds);

        standingZone.releaseTickets(lockedIds);

        // was 5 booked 2
        assertEquals(3, standingZone.getAvailable(),
                "Releasing SOLD tickets must not increase available tickets");

        assertEquals(2, standingZone.getOccupiedTickets().size(),
                "SOLD tickets must remain occupied and not return to available tickets");

        for (Integer ticketId : lockedIds) {
            boolean foundSoldTicket = false;

            for (StandingTicket ticket : standingZone.getOccupiedTickets()) {
                if (ticket.getTicketId() == ticketId &&
                        ticket.getStatus() == domain.dataType.TicketStatus.SOLD) {
                    foundSoldTicket = true;
                    break;
                }
            }

            assertTrue(foundSoldTicket,
                    "Sold ticket " + ticketId + " must stay SOLD after releaseTickets");
        }
    }
}
