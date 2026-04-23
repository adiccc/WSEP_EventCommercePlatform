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

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventCompanyManageServiceTest {

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
        event=new Event(companyId, creatorId, LocalDateTime.now().plusYears(1),"some test", LocalDateTime.now().plusYears(2), false);
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
                event.getId(),
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
                event.getId(),
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
                event.getId(),
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
                "non-existing-event-id",
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
                event.getId(),
                null,
                entries,
                standingZones,
                seatingZones
        );

        assertFalse(response.getValue());
        assertEquals("map element null", response.getMessage());
    }

    @Test
    void GivenValidInput_WhenCreateEvent_ThenEventIsSuccessfullyStored() {
        // Arrange: Event date in the future, sale start date in the future (but before the event)
        LocalDateTime eventDate = LocalDateTime.now().plusDays(30);
        LocalDateTime saleStartDate = LocalDateTime.now().plusDays(1);

        // Act: Standard sale (hasLottery = false)
        Response<Boolean> response = service.createEvent(
                validToken, companyId, creatorId, eventDate, "Standard Event", saleStartDate, false
        );

        // Assert result
        assertTrue(response.getValue());
        assertEquals("Event created successfully", response.getMessage());
    }

    @Test
    void GivenLotteryOptionSelected_WhenCreateEvent_ThenEventWithLotteryIsCreated() {
        // Arrange: Event date in the future, sale start date in the future (but before the event)
        LocalDateTime eventDate = LocalDateTime.now().plusDays(30);
        LocalDateTime saleStartDate = LocalDateTime.now().plusDays(1);

        // Act: Lottery sale (hasLottery = true)
        Response<Boolean> response = service.createEvent(
                validToken, companyId, creatorId, eventDate, "Lottery Event", saleStartDate, true
        );

        // Assert result
        assertTrue(response.getValue());
        assertEquals("Event created successfully", response.getMessage());
    }

    @Test
    void GivenUnauthorizedUser_WhenCreateEvent_ThenPermissionErrorIsReturned() {
        // Arrange: Setup dates and an unauthorized user ID
        LocalDateTime eventDate = LocalDateTime.now().plusDays(30);
        LocalDateTime saleStartDate = LocalDateTime.now().plusDays(1);

        int unauthorizedUserId = 999; // User with no permissions / not the company creator

        // Act
        Response<Boolean> response = service.createEvent(
                validToken, companyId, unauthorizedUserId, eventDate, "Unauthorized Event", saleStartDate, false
        );

        // Assert: System should reject the request due to lack of permissions
        assertFalse(response.getValue());
        assertEquals("Permission required", response.getMessage());
    }

    @Test
    void GivenPastEventDate_WhenCreateEvent_ThenDateValidationErrorIsReturned() {
        // Arrange: Event date is one hour before the current time
        LocalDateTime pastEventDate = LocalDateTime.now().minusHours(1);
        LocalDateTime saleStartDate = pastEventDate.minusDays(1);

        // Act
        Response<Boolean> response = service.createEvent(
                validToken, companyId, creatorId, pastEventDate, "Past Event", saleStartDate, false
        );

        // Assert: System identifies that the date is invalid
        assertFalse(response.getValue());
        assertEquals("Event date must be in the future", response.getMessage());
    }

    @Test
    void GivenInvalidOrMissingToken_WhenCreateEvent_ThenInvalidTokenErrorIsReturned() {
        // Arrange: Setup dates and an invalid token
        LocalDateTime eventDate = LocalDateTime.now().plusDays(30);
        LocalDateTime saleStartDate = LocalDateTime.now().plusDays(1);

        String invalidToken = ""; // Unauthenticated user or invalid token

        // Act
        Response<Boolean> response = service.createEvent(
                invalidToken, companyId, creatorId, eventDate, "No Token Event", saleStartDate, false
        );

        // Assert: System blocks and alerts about invalid token
        assertFalse(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

}
