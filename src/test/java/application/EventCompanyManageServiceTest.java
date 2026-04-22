package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.company.ICompanyRepo;
import domain.dataType.PermissionType;
import domain.event.Event;
import domain.event.EventMap;
import domain.event.IEventRepo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class EventCompanyManageServiceTest {

    @Test
    void GivenValidAreaSetupScenario_WhenDefineVenueAndSeatingMap_ThanHallIsCreatedAndAssignedToEvent() throws Exception {
        Event event = eventWithIds(444, 900, 123);
        AcceptanceCompanyRepo companyRepo = new AcceptanceCompanyRepo(true);
        AcceptanceEventRepo eventRepo = new AcceptanceEventRepo(event);
        EventCompanyManageService service = new EventCompanyManageService(companyRepo, eventRepo);

        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                "logged-in-user",
                123,
                444,
                stage(),
                entries(),
                standingZones(),
                seatingZones()
        );

        assertTrue(response.getValue());
        assertEquals("map saved successfully", response.getMessage());
        assertTrue(eventRepo.createMapCalled);
        assertNotNull(getEventMap(event));
    }

    @Test
    void GivenUnauthorizedUserScenario_WhenDefineVenueAndSeatingMap_ThanPermissionErrorIsShown() throws Exception {
        Event event = eventWithIds(444, 900, 123);
        EventCompanyManageService service = new EventCompanyManageService(
                new AcceptanceCompanyRepo(false),
                new AcceptanceEventRepo(event)
        );

        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                "logged-in-user",
                123,
                444,
                stage(),
                entries(),
                standingZones(),
                seatingZones()
        );

        assertFalse(response.getValue());
        assertEquals("Permission required", response.getMessage());
        assertNull(getEventMap(event));
    }

    @Test
    void GivenLoggedOutUserScenario_WhenDefineVenueAndSeatingMap_ThanInvalidTokenErrorIsShown() {
        EventCompanyManageService service = new EventCompanyManageService(
                new AcceptanceCompanyRepo(true),
                new AcceptanceEventRepo(new Event(null, null))
        );

        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                "",
                123,
                444,
                stage(),
                entries(),
                standingZones(),
                seatingZones()
        );

        assertFalse(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenMissingEventScenario_WhenDefineVenueAndSeatingMap_ThanEventNotFoundErrorIsShown() {
        EventCompanyManageService service = new EventCompanyManageService(
                new AcceptanceCompanyRepo(true),
                new MissingAcceptanceEventRepo()
        );

        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                "logged-in-user",
                123,
                444,
                stage(),
                entries(),
                standingZones(),
                seatingZones()
        );

        assertFalse(response.getValue());
        assertEquals("Event not found", response.getMessage());
    }

    @Test
    void GivenWrongMandatoryFieldsScenario_WhenDefineVenueAndSeatingMap_ThanValidationErrorIsShown() throws Exception {
        Event event = eventWithIds(444, 900, 123);
        EventCompanyManageService service = new EventCompanyManageService(
                new AcceptanceCompanyRepo(true),
                new AcceptanceEventRepo(event)
        );

        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                "logged-in-user",
                123,
                444,
                null,
                entries(),
                standingZones(),
                seatingZones()
        );

        assertFalse(response.getValue());
        assertEquals("map element null", response.getMessage());
    }

    private static Event eventWithIds(int eventId, int companyId, int creatorId) throws Exception {
        Event event = new Event(null, null);
        setField(event, "id", eventId);
        setField(event, "companyId", companyId);
        setField(event, "creatorId", creatorId);
        return event;
    }

    private static Object getEventMap(Event event) throws Exception {
        Field field = Event.class.getDeclaredField("eventMap");
        field.setAccessible(true);
        return field.get(event);
    }

    private static void setField(Event event, String fieldName, Object value) throws Exception {
        Field field = Event.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(event, value);
    }

    private static ElementPositionDTO stage() {
        return new ElementPositionDTO(10, 20);
    }

    private static List<ElementPositionDTO> entries() {
        return List.of(new ElementPositionDTO(0, 0), new ElementPositionDTO(50, 10));
    }

    private static List<StandingZoneDTO> standingZones() {
        return List.of(new StandingZoneDTO(200, "floor", 100.0, new ElementPositionDTO(1, 1)));
    }

    private static List<SeatingZoneDTO> seatingZones() {
        return List.of(new SeatingZoneDTO(10, 20, "tribune", 150.0, new ElementPositionDTO(5, 5)));
    }

    private static class AcceptanceCompanyRepo implements ICompanyRepo {
        private final boolean hasPermission;

        private AcceptanceCompanyRepo(boolean hasPermission) {
            this.hasPermission = hasPermission;
        }

        @Override
        public boolean checkPremissions(int companyId, int userId, PermissionType permissionType) {
            return hasPermission;
        }
    }

    private static class AcceptanceEventRepo implements IEventRepo {
        private final Event event;
        private boolean createMapCalled;

        private AcceptanceEventRepo(Event event) {
            this.event = event;
        }

        @Override
        public Event getEvent(int eventId) {
            return event;
        }

        @Override
        public EventMap createMap(ElementPositionDTO stage, List<ElementPositionDTO> entries, List<StandingZoneDTO> standingZone, List<SeatingZoneDTO> seatingZone) {
            this.createMapCalled = true;
            return new EventMap(null, null, null);
        }
    }

    private static class MissingAcceptanceEventRepo implements IEventRepo {
        @Override
        public Event getEvent(int eventId) {
            throw new NoSuchElementException("event does not exist");
        }

        @Override
        public EventMap createMap(ElementPositionDTO stage, List<ElementPositionDTO> entries, List<StandingZoneDTO> standingZone, List<SeatingZoneDTO> seatingZone) {
            fail("createMap should not be called when the event is missing");
            return null;
        }
    }
}
