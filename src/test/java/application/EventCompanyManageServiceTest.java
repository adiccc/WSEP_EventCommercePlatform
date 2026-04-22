package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.company.Company;
import domain.event.Event;
import infrastructure.CompanyRepoImpl;
import infrastructure.EventRepoImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventCompanyManageServiceTest {

    private final int eventId = 444;
    private final int companyId = 900;
    private final int creatorId = 123;

    private TokenService tokenService;
    private CompanyRepoImpl companyRepo;
    private EventRepoImpl eventRepo;
    private ElementPositionDTO stage;
    private List<ElementPositionDTO> entries;
    private List<StandingZoneDTO> standingZones;
    private List<SeatingZoneDTO> seatingZones;
    private EventCompanyManageService service;
    private Event event;
    private String validToken;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        validToken=tokenService.generateToken("test-user");
        companyRepo = new CompanyRepoImpl();
        companyRepo.store(new Company(companyId, "Test Company", creatorId));
        eventRepo = new EventRepoImpl();
        event=new Event(eventId, companyId, creatorId, null, null);
        eventRepo.store(event);
        service = new EventCompanyManageService(companyRepo, eventRepo,tokenService);
        stage = new ElementPositionDTO(10, 20);
        entries = List.of(new ElementPositionDTO(0, 0), new ElementPositionDTO(50, 10));
        standingZones = List.of(new StandingZoneDTO(200, "floor", 100.0, new ElementPositionDTO(1, 1)));
        seatingZones = List.of(new SeatingZoneDTO(10, 20, "tribune", 150.0, new ElementPositionDTO(5, 5)));
    }

    @Test
    void GivenValidAreaSetupScenario_WhenDefineVenueAndSeatingMap_ThanHallIsCreatedAndAssignedToEvent() throws Exception {

        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                validToken,
                creatorId,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        assertEquals("map saved successfully", response.getMessage());
        assertTrue(response.getValue());
        assertNotNull(event.getMap());
    }

    @Test
    void GivenUnauthorizedUserScenario_WhenDefineVenueAndSeatingMap_ThanPermissionErrorIsShown() throws Exception {
        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                validToken,
                999,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        assertFalse(response.getValue());
        assertEquals("Permission required", response.getMessage());
        assertTrue(event.getMap()==null);
    }

    @Test
    void GivenLoggedOutUserScenario_WhenDefineVenueAndSeatingMap_ThanInvalidTokenErrorIsShown() {

        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                "",
                creatorId,
                eventId,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        assertFalse(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenMissingEventScenario_WhenDefineVenueAndSeatingMap_ThanEventNotFoundErrorIsShown() {
        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                validToken,
                creatorId,
                123,
                stage,
                entries,
                standingZones,
                seatingZones
        );

        assertFalse(response.getValue());
        assertEquals("Event not found", response.getMessage());
    }

    @Test
    void GivenWrongMandatoryFieldsScenario_WhenDefineVenueAndSeatingMap_ThanValidationErrorIsShown() throws Exception {

        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                validToken,
                creatorId,
                eventId,
                null,
                entries,
                standingZones,
                seatingZones
        );

        assertFalse(response.getValue());
        assertEquals("map element null", response.getMessage());
    }

}
