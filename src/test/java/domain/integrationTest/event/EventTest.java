package domain.integrationTest.event;

import domain.dataType.CategoryEvent;
import domain.dataType.ElementPosition;
import domain.dataType.GeographicalArea;
import domain.dto.SeatingTicketDTO;
import domain.event.SeatingZone;
import domain.event.Event;
import domain.dataType.EventSearchFilter;
import domain.event.EventMap;
import domain.event.StandingZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    private final int companyId = 900;
    private final int creatorId = 123;
    private EventMap map1;
    private EventMap map2;
    private Event event;
    private List<Integer> bookedStandingIds;

    @BeforeEach
    void setUp() {
        AtomicInteger ticketIdGenerator = new AtomicInteger(1);
        ElementPosition stage = new ElementPosition(10, 20);
        ElementPosition entry = new ElementPosition(5, 5);
        ElementPosition posZone = new ElementPosition(15, 15);
        SeatingZone vipZone = new SeatingZone("VIP", 100, 50, 50, posZone, ticketIdGenerator);
        map1 = new EventMap(stage, List.of(entry), List.of(vipZone));

        event = new Event(
                companyId,
                creatorId,
                LocalDateTime.now().plusDays(5),
                "Test Event",
                LocalDateTime.now().minusDays(1),
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );

        // map2 now has BOTH a seating zone ("Zone") and a standing zone ("floor")
        SeatingZone seatingZone = new SeatingZone("Zone", 50, 10, 10,
                new ElementPosition(1, 1), ticketIdGenerator);
        StandingZone standingZone = new StandingZone("floor", 30, 100,
                new ElementPosition(2, 2), ticketIdGenerator);

        map2 = new EventMap(
                new ElementPosition(0, 0),
                List.of(new ElementPosition(1, 1)),
                List.of(seatingZone, standingZone)
        );

        event.setMap(map2);
        event.setActive(true);
        bookedStandingIds = new ArrayList<>(standingZone.bookTickets(10));
    }


    @Test
    void GivenSaleStartedAndMapIsSet_WhenIsAvailableForSale_ThenReturnTrue() {
        // Arrange: Sale start date is in the past (1 hour ago)
        LocalDateTime pastSaleDate = LocalDateTime.now().minusHours(1);
        Event event = new Event(companyId, creatorId, LocalDateTime.now().plusDays(10), "Test Event", pastSaleDate, false, GeographicalArea.CENTER, CategoryEvent.SPORTS);

        event.setMap(map1);

        // Assert
        assertTrue(event.isAvailableForSale(), "Event should be available for sale since the map exists and the sale date has passed.");
    }

    @Test
    void GivenSaleNotStartedAndMapIsSet_WhenIsAvailableForSale_ThenReturnFalse() {
        // Arrange: Sale start date is in the future (in 1 hour)
        LocalDateTime futureSaleDate = LocalDateTime.now().plusHours(1);
        Event event = new Event(companyId, creatorId, LocalDateTime.now().plusDays(10), "Test Event", futureSaleDate, false, GeographicalArea.CENTER, CategoryEvent.SPORTS);

        event.setMap(map1);

        // Assert
        assertFalse(event.isAvailableForSale(), "Event should not be available since the sale start date has not yet arrived.");
    }

    @Test
    void GivenSaleStartedButMapIsNull_WhenIsAvailableForSale_ThenReturnFalse() {
        // Arrange: Sale start date is in the past, but no map is defined
        LocalDateTime pastSaleDate = LocalDateTime.now().minusHours(1);
        Event event = new Event(companyId, creatorId, LocalDateTime.now().plusDays(10), "Test Event", pastSaleDate, false, GeographicalArea.CENTER, CategoryEvent.SPORTS);

        // Do not set a map (remains null as defined in the constructor)

        // Assert
        assertFalse(event.isAvailableForSale(), "Event should not be available since no seating map is defined.");
    }

    @Test
    void GivenSaleNotStartedAndMapIsNull_WhenIsAvailableForSale_ThenReturnFalse() {
        // Arrange: Date is in the future and there is no map
        LocalDateTime futureSaleDate = LocalDateTime.now().plusHours(1);
        Event event = new Event(companyId, creatorId, LocalDateTime.now().plusDays(10), "Test Event", futureSaleDate, false, GeographicalArea.NORTH, CategoryEvent.SPORTS);

        // Assert
        assertFalse(event.isAvailableForSale(), "Event should not be available - no map and date is in the future.");
    }

    @Test
    void GivenEvent_WhenSetActive_ThenIsActiveReturnsTrue() {
        Event event = new Event(companyId, creatorId,
                LocalDateTime.now().plusDays(1),
                "Test",
                LocalDateTime.now(),
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS);

        event.setActive(true);

        assertTrue(event.isActive());
    }

    @Test
    void GivenPriceWithinRange_WhenMatches_ThenReturnTrue() {
        event.setMap(map1);
        EventSearchFilter filter = new EventSearchFilter();
        filter.setMinPrice(80.0);
        filter.setMaxPrice(120.0);

        assertTrue(event.matches(filter));
    }

    @Test
    void GivenPriceBelowMin_WhenMatches_ThenReturnFalse() {
        EventSearchFilter filter = new EventSearchFilter();
        filter.setMinPrice(80.0);

        assertFalse(event.matches(filter));
    }

    @Test
    void GivenMultipleZones_OneMatchesPrice_WhenMatches_ThenReturnTrue() {
        SeatingZone cheapZone = new SeatingZone("Cheap", 50, 10, 10, new ElementPosition(1,1),new AtomicInteger(1));
        SeatingZone vipZone = new SeatingZone("VIP", 100, 10, 10, new ElementPosition(2,2),new AtomicInteger(1));

        EventMap customMap = new EventMap(
                new ElementPosition(0,0),
                List.of(new ElementPosition(1,1)),
                List.of(cheapZone, vipZone)
        );

        event.setMap(customMap);

        EventSearchFilter filter = new EventSearchFilter();
        filter.setMinPrice(90.0);
        filter.setMaxPrice(110.0);

        assertTrue(event.matches(filter)); //  vipZone
    }


    @Test
    void GivenSingleZoneSingleSeat_WhenFindSeatingTicketIds_ThenReturnsOneId() {
        Map<String, List<SeatingTicketDTO>> input =
                Map.of("Zone", List.of(new SeatingTicketDTO(0, 0)));

        List<Integer> result = event.findSeatingTicketIds(input);

        assertEquals(1, result.size());
    }

    @Test
    void GivenSingleZoneMultipleSeats_WhenFindSeatingTicketIds_ThenReturnsAllIds() {
        Map<String, List<SeatingTicketDTO>> input = Map.of("Zone", List.of(
                new SeatingTicketDTO(0, 0),
                new SeatingTicketDTO(1, 1),
                new SeatingTicketDTO(2, 2)));

        List<Integer> result = event.findSeatingTicketIds(input);

        assertEquals(3, result.size());
        assertEquals(3, new HashSet<>(result).size(), "all returned IDs must be distinct");
    }

    @Test
    void GivenMultipleZones_WhenFindSeatingTicketIds_ThenReturnsIdsFromAllZones() {
        AtomicInteger gen = new AtomicInteger(1);
        SeatingZone z1 = new SeatingZone("A", 50, 3, 3, new ElementPosition(1, 1), gen);
        SeatingZone z2 = new SeatingZone("B", 50, 3, 3, new ElementPosition(2, 2), gen);
        event.setMap(new EventMap(
                new ElementPosition(0, 0), List.of(new ElementPosition(1, 1)), List.of(z1, z2)));

        Map<String, List<SeatingTicketDTO>> input = Map.of(
                "A", List.of(new SeatingTicketDTO(0, 0), new SeatingTicketDTO(1, 1)),
                "B", List.of(new SeatingTicketDTO(2, 2)));

        List<Integer> result = event.findSeatingTicketIds(input);

        assertEquals(3, result.size());
        assertEquals(3, new HashSet<>(result).size());
    }

    @Test
    void GivenEmptyMap_WhenFindSeatingTicketIds_ThenReturnsEmptyList() {
        List<Integer> result = event.findSeatingTicketIds(new HashMap<>());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void GivenZoneEntryWithEmptySeatList_WhenFindSeatingTicketIds_ThenReturnsEmptyList() {
        Map<String, List<SeatingTicketDTO>> input = Map.of("Zone", List.of());

        List<Integer> result = event.findSeatingTicketIds(input);

        assertTrue(result.isEmpty());
    }

    @Test
    void GivenNonExistentZone_WhenFindSeatingTicketIds_ThenThrows() {
        Map<String, List<SeatingTicketDTO>> input =
                Map.of("DoesNotExist", List.of(new SeatingTicketDTO(0, 0)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> event.findSeatingTicketIds(input));
        assertTrue(ex.getMessage().contains("does not exist"), "got: " + ex.getMessage());
    }

    @Test
    void GivenZoneIsStandingNotSeating_WhenFindSeatingTicketIds_ThenThrows() {
        Map<String, List<SeatingTicketDTO>> input =
                Map.of("floor", List.of(new SeatingTicketDTO(0, 0)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> event.findSeatingTicketIds(input));
        assertTrue(ex.getMessage().toLowerCase().contains("not a seating zone"));
    }

    @Test
    void GivenSeatThatDoesNotExistInZone_WhenFindSeatingTicketIds_ThenThrows() {
        // "Zone" is 10x10, so (99,99) doesn't exist
        Map<String, List<SeatingTicketDTO>> input =
                Map.of("Zone", List.of(new SeatingTicketDTO(99, 99)));

        assertThrows(IllegalArgumentException.class,
                () -> event.findSeatingTicketIds(input));
    }

    @Test
    void GivenMixOfValidAndInvalidSeats_WhenFindSeatingTicketIds_ThenThrows() {
        Map<String, List<SeatingTicketDTO>> input = Map.of("Zone", List.of(
                new SeatingTicketDTO(0, 0),    // valid
                new SeatingTicketDTO(99, 99))); // invalid

        assertThrows(IllegalArgumentException.class,
                () -> event.findSeatingTicketIds(input));
    }

    @Test
    void GivenUserHoldsAllOccupied_WhenPickStandingFromZone_ThenReturnsRequestedCount() {
        List<Integer> picked = event.pickStandingFromZone("floor", bookedStandingIds, 3);

        assertEquals(3, picked.size());
        assertTrue(bookedStandingIds.containsAll(picked));
    }

    @Test
    void GivenPickAll_WhenPickStandingFromZone_ThenReturnsAll() {
        List<Integer> picked = event.pickStandingFromZone("floor", bookedStandingIds, 10);

        assertEquals(10, picked.size());
        assertEquals(new HashSet<>(bookedStandingIds), new HashSet<>(picked));
    }

    @Test
    void GivenNumToPickIsZero_WhenPickStandingFromZone_ThenReturnsEmpty() {
        List<Integer> picked = event.pickStandingFromZone("floor", bookedStandingIds, 0);

        assertTrue(picked.isEmpty());
    }

    @Test
    void GivenEmptyUserTickets_WhenPickStandingFromZone_ThenThrowsBecauseNotEnough() {
        // After your recent change, asking for >0 when user holds 0 throws.
        assertThrows(IllegalArgumentException.class,
                () -> event.pickStandingFromZone("floor", List.of(), 3));
    }

    @Test
    void GivenUserHasFewerThanRequested_WhenPickStandingFromZone_ThenThrows() {
        List<Integer> onlyTwo = bookedStandingIds.subList(0, 2);

        assertThrows(IllegalArgumentException.class,
                () -> event.pickStandingFromZone("floor", onlyTwo, 5));
    }

    @Test
    void GivenUserTicketsContainUnrelatedIds_WhenPickStandingFromZone_ThenIgnoresUnrelated() {
        List<Integer> mixed = new ArrayList<>(bookedStandingIds);
        mixed.add(999_999);

        List<Integer> picked = event.pickStandingFromZone("floor", mixed, 3);

        assertEquals(3, picked.size());
        assertFalse(picked.contains(999_999));
    }

    @Test
    void GivenNonExistentZone_WhenPickStandingFromZone_ThenThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> event.pickStandingFromZone("ghost", bookedStandingIds, 1));
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    void GivenZoneIsSeatingNotStanding_WhenPickStandingFromZone_ThenThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> event.pickStandingFromZone("Zone", bookedStandingIds, 1));
        assertTrue(ex.getMessage().toLowerCase().contains("not a standing zone"));
    }
}