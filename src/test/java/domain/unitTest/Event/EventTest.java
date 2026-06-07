package domain.unitTest.Event;

import domain.dataType.CategoryEvent;
import domain.dataType.ElementPosition;
import domain.dataType.EventSearchFilter;
import domain.dataType.GeographicalArea;
import domain.event.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    private final int companyId = 900;
    private final int creatorId = 123;

    private void assignTicketIds(StandingZone standingZone) {
        int id = 1;

        for (Ticket ticket : standingZone.getTickets()) {
            if (ticket.getId() == null) {
                ticket.setId(id++);
            }
        }
    }

    @Test
    void GivenSaleStartedAndMapIsSet_WhenIsAvailableForSale_ThenReturnTrue() {
        // Arrange: Sale start date is in the past (1 hour ago)
        LocalDateTime pastSaleDate = LocalDateTime.now().minusHours(1);
        Event event = new Event(companyId, creatorId, LocalDateTime.now().plusDays(10), "Test Event", pastSaleDate, false, GeographicalArea.CENTER, CategoryEvent.SPORTS);

        // Mock an existing map
        EventMap mockMap = Mockito.mock(EventMap.class);
        event.setMap(mockMap);

        // Assert
        assertTrue(event.isAvailableForSale(), "Event should be available for sale since the map exists and the sale date has passed.");
    }

    @Test
    void GivenSaleNotStartedAndMapIsSet_WhenIsAvailableForSale_ThenReturnFalse() {
        // Arrange: Sale start date is in the future (in 1 hour)
        LocalDateTime futureSaleDate = LocalDateTime.now().plusHours(1);
        Event event = new Event(companyId, creatorId, LocalDateTime.now().plusDays(10), "Test Event", futureSaleDate, false, GeographicalArea.CENTER, CategoryEvent.SPORTS);

        // Mock an existing map
        EventMap mockMap = Mockito.mock(EventMap.class);
        event.setMap(mockMap);

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
    void GivenEventWithoutMap_WhenIsSoldOut_ThenReturnFalse() {
        Event event = new Event(
                companyId, creatorId, LocalDateTime.now().plusDays(10),
                "Test Event", LocalDateTime.now().minusHours(1), false,
                GeographicalArea.CENTER, CategoryEvent.SPORTS);

        assertFalse(event.isSoldOut(), "Event without a map cannot be sold out");
    }

    @Test
    void GivenStandingZoneWithAvailableTickets_WhenIsSoldOut_ThenReturnFalse() {
        StandingZone zone = new StandingZone(
                "floor", 100.0, 5, new ElementPosition(0, 0));
        EventMap map = new EventMap(
                new ElementPosition(0, 0), List.of(), List.<Zone>of(zone));

        Event event = new Event(
                companyId, creatorId, LocalDateTime.now().plusDays(10),
                "Test Event", LocalDateTime.now().minusHours(1), false,
                GeographicalArea.CENTER, CategoryEvent.SPORTS);
        event.setMap(map);

        assertFalse(event.isSoldOut(),
                "Event with available tickets in any zone should not be sold out");
    }

    @Test
    void GivenAllStandingTicketsSold_WhenIsSoldOut_ThenReturnTrue() {
        StandingZone zone = new StandingZone(
                "floor", 100.0, 2, new ElementPosition(0, 0));
        assignTicketIds(zone);
        List<Integer> bookedTickets = zone.bookTickets(2);
        zone.markTicketsAsSold(bookedTickets);

        EventMap map = new EventMap(
                new ElementPosition(0, 0), List.of(), List.<Zone>of(zone));

        Event event = new Event(
                companyId, creatorId, LocalDateTime.now().plusDays(10),
                "Test Event", LocalDateTime.now().minusHours(1), false,
                GeographicalArea.CENTER, CategoryEvent.SPORTS);
        event.setMap(map);

        assertTrue(event.isSoldOut(),
                "Event with every ticket marked SOLD should be sold out");
    }

    @Test
    void GivenAllStandingTicketsLockedAndNoneAvailable_WhenIsSoldOut_ThenReturnTrue() {
        StandingZone zone = new StandingZone(
                "floor", 100.0, 2, new ElementPosition(0, 0));
        assignTicketIds(zone);
        zone.bookTickets(2); // LOCKED state, no SOLD transition; available is now 0

        EventMap map = new EventMap(
                new ElementPosition(0, 0), List.of(), List.<Zone>of(zone));

        Event event = new Event(
                companyId, creatorId, LocalDateTime.now().plusDays(10),
                "Test Event", LocalDateTime.now().minusHours(1), false,
                GeographicalArea.CENTER, CategoryEvent.SPORTS);
        event.setMap(map);

        assertTrue(event.isSoldOut(),
                "Event should be sold out when no ticket is AVAILABLE, even if some are still LOCKED");
    }

    @Test
    void GivenMatchingKeyword_WhenMatches_ThenReturnTrue() {
        Event event = new Event(
                companyId,
                creatorId,
                LocalDateTime.now().plusDays(1), // future
                "Football Game",
                LocalDateTime.now().minusDays(1), // sale started
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );
        event.setActive(true);

        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("football");

        assertTrue(event.matches(filter));
    }

    @Test
    void GivenNonMatchingKeyword_WhenMatches_ThenReturnFalse() {
        Event event = new Event(
                companyId,
                creatorId,
                LocalDateTime.now().plusDays(1), // future
                "Basketball Game",
                LocalDateTime.now().minusDays(1), // sale started
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );
        event.setActive(true);

        EventSearchFilter filter = new EventSearchFilter();
        filter.setKeyword("football");

        assertFalse(event.matches(filter));
    }

    @Test
    void GivenDateOutOfRange_WhenMatches_ThenReturnFalse() {
        Event event = new Event(
                companyId,
                creatorId,
                LocalDateTime.now().plusDays(1), // future
                "Football Game",
                LocalDateTime.now().minusDays(1), // sale started
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );
        event.setActive(true);

        EventSearchFilter filter = new EventSearchFilter();
        filter.setStartDate(LocalDateTime.now().plusDays(2));

        assertFalse(event.matches(filter));
    }

    @Test
    void GivenCategoryMismatch_WhenMatches_ThenReturnFalse() {
        Event event = new Event(
                companyId,
                creatorId,
                LocalDateTime.now().plusDays(1), // future
                "Football Game",
                LocalDateTime.now().minusDays(1), // sale started
                false,
                GeographicalArea.CENTER,
                CategoryEvent.SPORTS
        );
        event.setActive(true);

        EventSearchFilter filter = new EventSearchFilter();
        filter.setCategory(CategoryEvent.FESTIVAL);

        assertFalse(event.matches(filter));
    }

}