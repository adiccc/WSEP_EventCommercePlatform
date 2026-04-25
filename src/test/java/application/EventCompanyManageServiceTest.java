package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.company.Company;
import domain.company.ContactInfo;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.event.Event;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import domain.user.IUserRepo;
import domain.user.Member;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventCompanyManageServiceTest {

    private final int companyId = 900;

    private TokenService tokenService;
    private CompanyRepoImpl companyRepo;
    private EventRepoImpl eventRepo;
    private ElementPositionDTO stage;
    private List<ElementPositionDTO> entries;
    private List<StandingZoneDTO> standingZones;
    private List<SeatingZoneDTO> seatingZones;
    private EventCompanyManageService service;
    private Event event;
    private String validToken1;
    private IAuth auth;
    private IUserRepo userRepo;
    private IPasswordEncoder passwordEncoder;
    private String invalidToken2;

    @BeforeEach
    void setUp() {

        tokenService = new TokenService();
        userRepo = new UserRepo();
        passwordEncoder = new PasswordEncoderUtil();
        auth = new Auth(tokenService,userRepo,passwordEncoder);

        Member member1 = new Member("test-user1", "yy","yarin", "shemer","050-4273201", LocalDate.of(2002,4,15),"Omer");
        userRepo.store(member1);
        validToken1=tokenService.generateToken("test-user1");
        Member member2 = new Member("test-user2", "yy","yarin", "shemer","050-4273201", LocalDate.of(2002,4,15),"Omer");
        userRepo.store(member2);
        invalidToken2 = tokenService.generateToken("test-user2");

        companyRepo = new CompanyRepoImpl();
        int creatorId = auth.getUserId(validToken1).getValue();
        companyRepo.store(new Company(companyId, "Test Company", creatorId,
                new ContactInfo("test@test.com", "0500000000", "bank-1"),
                new PurchasePolicy(), new DiscountPolicy()));
        eventRepo = new EventRepoImpl();
        event=new Event(companyId, creatorId, LocalDateTime.now().plusYears(1),"some test", LocalDateTime.now().plusYears(2), false, GeographicalArea.NORTH, CategoryEvent.LIVEMUSIC);
        eventRepo.store(event);
        service = new EventCompanyManageService(companyRepo, eventRepo,auth);
        stage = new ElementPositionDTO(10, 20);
        entries = List.of(new ElementPositionDTO(0, 0), new ElementPositionDTO(50, 10));
        standingZones = List.of(new StandingZoneDTO(200, "floor", 100.0, new ElementPositionDTO(1, 1)));
        seatingZones = List.of(new SeatingZoneDTO(10, 20, "tribune", 150.0, new ElementPositionDTO(5, 5)));
    }

    @Test
    void GivenValidAreaSetupScenario_WhenDefineVenueAndSeatingMap_ThenHallIsCreatedAndAssignedToEvent() throws Exception {

        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                validToken1,
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
    void GivenUnauthorizedUserScenario_WhenDefineVenueAndSeatingMap_ThenPermissionErrorIsShown() throws Exception {
        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                invalidToken2,
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
    void GivenLoggedOutUserScenario_WhenDefineVenueAndSeatingMap_ThenInvalidTokenErrorIsShown() {

        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                null,
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
    void GivenMissingEventScenario_WhenDefineVenueAndSeatingMap_ThenEventNotFoundErrorIsShown() {
        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                validToken1,
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
    void GivenWrongMandatoryFieldsScenario_WhenDefineVenueAndSeatingMap_ThenValidationErrorIsShown() throws Exception {

        Response<Boolean> response = service.DefineVenueAndSeatingMap(
                validToken1,
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
                validToken1, companyId, eventDate, "Standard Event", saleStartDate, false, GeographicalArea.CENTER, CategoryEvent.THEATER
        );

        // Assert result
        System.out.println(response.getMessage());
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
                validToken1, companyId, eventDate, "Lottery Event", saleStartDate, true, GeographicalArea.CENTER, CategoryEvent.THEATER
        );

        // Assert result
        System.out.println(response.getMessage());
        assertTrue(response.getValue());
        assertEquals("Event created successfully", response.getMessage());
    }

    @Test
    void GivenUnauthorizedUser_WhenCreateEvent_ThenPermissionErrorIsReturned() {
        // Arrange: Setup dates and an unauthorized user ID
        LocalDateTime eventDate = LocalDateTime.now().plusDays(30);
        LocalDateTime saleStartDate = LocalDateTime.now().plusDays(1);

        // Act
        Response<Boolean> response = service.createEvent(
                invalidToken2, companyId, eventDate, "Unauthorized Event", saleStartDate, false, GeographicalArea.CENTER, CategoryEvent.THEATER
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
                validToken1, companyId, pastEventDate, "Past Event", saleStartDate, false, GeographicalArea.CENTER, CategoryEvent.THEATER
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

        // Act
        Response<Boolean> response = service.createEvent(
                null, companyId, eventDate, "No Token Event", saleStartDate, false, GeographicalArea.CENTER, CategoryEvent.THEATER
);

        // Assert: System blocks and alerts about invalid token
        assertFalse(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenValidManagerAndFutureDate_WhenUpdateEventDate_ThenEventDateIsUpdatedSuccessfully() {
        // Given
        LocalDateTime originalDate = event.getDate();
        LocalDateTime requestedDate = originalDate.plusDays(7);

        // When
        Response<Boolean> response = service.UpdateEventDate(
                validToken1,
                event.getId(),
                requestedDate
        );

        // Then
        assertTrue(response.getValue());
        assertEquals("Event updated successfully", response.getMessage());

        Event updatedEvent = eventRepo.findById(event.getId());
        assertEquals(requestedDate, updatedEvent.getDate());
    }

    @Test
    void GivenPastEvent_WhenUpdateEventDate_ThenPastEventErrorIsReturned() {
        // Given
        int creatorId = auth.getUserId(validToken1).getValue();
        Event pastEvent = new Event(
                companyId,
                creatorId,
                LocalDateTime.now().minusDays(3),
                "past event",
                LocalDateTime.now().minusDays(10),
                false,
                GeographicalArea.CENTER,
                CategoryEvent.CONFERENCE
        );
        eventRepo.store(pastEvent);

        LocalDateTime requestedDate = LocalDateTime.now().plusDays(5);

        // When
        Response<Boolean> response = service.UpdateEventDate(
                validToken1,
                pastEvent.getId(),
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertEquals("Event date must be in the future", response.getMessage());
    }

    @Test
    void GivenPastRequestedDate_WhenUpdateEventDate_ThenInvalidNewDateErrorIsReturned() {
        // Given
        LocalDateTime originalDate = event.getDate();
        LocalDateTime requestedDate = LocalDateTime.now().minusDays(1);

        // When
        Response<Boolean> response = service.UpdateEventDate(
                validToken1,
                event.getId(),
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertEquals("Event date can only be after the original date", response.getMessage());

        Event updatedEvent = eventRepo.findById(event.getId());
        assertEquals(originalDate, updatedEvent.getDate());
    }

    @Test
    void GivenEarlierThenOriginalDate_WhenUpdateEventDate_ThenInvalidNewDateErrorIsReturned() {
        // Given
        LocalDateTime originalDate = event.getDate();
        LocalDateTime requestedDate = originalDate.minusDays(1);

        // When
        Response<Boolean> response = service.UpdateEventDate(
                validToken1,
                event.getId(),
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertEquals("Event date can only be after the original date", response.getMessage());

        Event updatedEvent = eventRepo.findById(event.getId());
        assertEquals(originalDate, updatedEvent.getDate());
    }

    @Test
    void GivenUnauthorizedUser_WhenUpdateEventDate_ThenPermissionErrorIsReturned() {
        // Given
        LocalDateTime originalDate = event.getDate();
        LocalDateTime requestedDate = originalDate.plusDays(10);

        // When
        Response<Boolean> response = service.UpdateEventDate(
                invalidToken2,
                event.getId(),
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertEquals("User id mismatch to the creator of the event", response.getMessage());

        Event updatedEvent = eventRepo.findById(event.getId());
        assertEquals(originalDate, updatedEvent.getDate());
    }

    @Test
    void GivenInvalidToken_WhenUpdateEventDate_ThenInvalidTokenErrorIsReturned() {
        // Given
        LocalDateTime requestedDate = event.getDate().plusDays(5);

        // When
        Response<Boolean> response = service.UpdateEventDate(
                null,
                event.getId(),
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertEquals("Invalid token", response.getMessage());
    }

    @Test
    void GivenEventDoesNotExist_WhenUpdateEventDate_ThenEventNotFoundErrorIsReturned() {
        // Given
        LocalDateTime requestedDate = LocalDateTime.now().plusDays(10);

        // When
        Response<Boolean> response = service.UpdateEventDate(
                validToken1,
                "non-existing-event-id",
                requestedDate
        );

        // Then
        assertFalse(response.getValue());
        assertTrue(response.getMessage().startsWith("failed to create event : "));
    }

}
