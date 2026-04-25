package domain.integrationTest.event;

import domain.dataType.CategoryEvent;
import domain.dataType.ElementPosition;
import domain.dataType.GeographicalArea;
import domain.dataType.SeatingZone;
import domain.event.Event;
import domain.event.EventMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    private final int companyId = 900;
    private final int creatorId = 123;
    private EventMap map;
    @BeforeEach
    void setUp() {
        ElementPosition stage = new ElementPosition(10, 20);
        ElementPosition entry = new ElementPosition(5, 5);
        ElementPosition posZone = new ElementPosition(15, 15);
        SeatingZone vipZone = new SeatingZone("VIP", 100, 50,50, posZone);
        map = new EventMap(stage, List.of(entry), List.of(vipZone));
    }

    @Test
    void GivenSaleStartedAndMapIsSet_WhenIsAvailableForSale_ThenReturnTrue() {
        // Arrange: Sale start date is in the past (1 hour ago)
        LocalDateTime pastSaleDate = LocalDateTime.now().minusHours(1);
        Event event = new Event(companyId, creatorId, LocalDateTime.now().plusDays(10), "Test Event", pastSaleDate, false, GeographicalArea.CENTER, CategoryEvent.SPORTS);

        event.setMap(map);

        // Assert
        assertTrue(event.isAvailableForSale(), "Event should be available for sale since the map exists and the sale date has passed.");
    }

    @Test
    void GivenSaleNotStartedAndMapIsSet_WhenIsAvailableForSale_ThenReturnFalse() {
        // Arrange: Sale start date is in the future (in 1 hour)
        LocalDateTime futureSaleDate = LocalDateTime.now().plusHours(1);
        Event event = new Event(companyId, creatorId, LocalDateTime.now().plusDays(10), "Test Event", futureSaleDate, false, GeographicalArea.CENTER, CategoryEvent.SPORTS);

        event.setMap(map);

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


}