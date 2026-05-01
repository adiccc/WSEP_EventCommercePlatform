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


}
