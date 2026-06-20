package app.init.handlers;

import DTO.DiscountDTO;
import DTO.PurchaseRuleDTO;
import DTO.QueueEntryResultDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import app.init.InitContext;
import application.CompanyService;
import application.EventCompanyManageService;
import application.LotteryService;
import application.Response;
import application.UserService;
import domain.company.Company;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dataType.PermissionType;
import domain.dto.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies each init handler calls the right service method with the right params. Services are mocked.
 */
class HandlerServiceInteractionTest {

    private final InitContext context = new InitContext();

    private UserService userService;
    private CompanyService companyService;
    private EventCompanyManageService eventService;
    private LotteryService lotteryService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        companyService = mock(CompanyService.class);
        eventService = mock(EventCompanyManageService.class);
        lotteryService = mock(LotteryService.class);
    }

    private <H> H inject(H handler, String field, Object service) {
        ReflectionTestUtils.setField(handler, field, service);
        return handler;
    }

    @Test
    void GivenAdmittedGuest_WhenEnterExecuted_ThenCallsEnter() {
        QueueEntryResultDTO result = mock(QueueEntryResultDTO.class);
        when(result.isAdmitted()).thenReturn(true);
        when(result.getToken()).thenReturn("guest-token");
        when(userService.enter()).thenReturn(new Response<>(result, null));

        inject(new EnterHandler(), "userService", userService).execute(Map.of(), context);

        verify(userService).enter();
    }

    @Test
    void GivenRegisterParams_WhenRegisterExecuted_ThenCallsRegisterUserWithRightValues() {
        when(userService.registerUser(any(), any())).thenReturn(new Response<>(true, null));

        Map<String, String> params = Map.ofEntries(
                Map.entry("guestToken", "g-token"),
                Map.entry("email", "alice@demo.com"),
                Map.entry("firstName", "Alice"),
                Map.entry("lastName", "Cohen"),
                Map.entry("password", "Alice123!"),
                Map.entry("birthDay", "3"),
                Map.entry("birthMonth", "7"),
                Map.entry("birthYear", "1995"),
                Map.entry("address", "123 Main St"),
                Map.entry("phone", "050-123-4567")
        );
        inject(new RegisterHandler(), "userService", userService).execute(params, context);

        ArgumentCaptor<UserDTO> dtoCaptor = ArgumentCaptor.forClass(UserDTO.class);
        verify(userService).registerUser(eq("g-token"), dtoCaptor.capture());
        UserDTO dto = dtoCaptor.getValue();
        assertEquals("alice@demo.com", dto.getEmail());
        assertEquals("Alice", dto.getFirstName());
        assertEquals("Cohen", dto.getLastName());
        assertEquals("Alice123!", dto.getPassword());
        assertEquals(3, dto.getDay());
        assertEquals(7, dto.getMonth());
        assertEquals(1995, dto.getYear());
        assertEquals("123 Main St", dto.getAddress());
        assertEquals("050-123-4567", dto.getPhone());
    }

    @Test
    void GivenEmailAndPassword_WhenLoginExecuted_ThenCallsLoginWithRightValues() {
        when(userService.login(anyString(), anyString())).thenReturn(new Response<>("session-token", null));

        inject(new LoginHandler(), "userService", userService)
                .execute(Map.of("email", "alice@demo.com", "password", "Alice123!"), context);

        verify(userService).login("alice@demo.com", "Alice123!");
    }

    @Test
    void GivenToken_WhenGetUserIdExecuted_ThenCallsGetUserIdWithToken() {
        when(userService.getUserId(anyString())).thenReturn(new Response<>(42, null));

        inject(new GetUserIdHandler(), "userService", userService).execute(Map.of("token", "tok"), context);

        verify(userService).getUserId("tok");
    }

    @Test
    void GivenCompanyParams_WhenOpenCompanyExecuted_ThenCallsCreateProductionCompanyWithRightValues() {
        Company company = mock(Company.class);
        when(company.getCompanyId()).thenReturn(7);
        when(companyService.createProductionCompany(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Response<>(company, null));

        Map<String, String> params = Map.of(
                "token", "t", "companyId", "7", "companyName", "SoundWave",
                "email", "info@sw.com", "phone", "050-111-0001", "bankAccount", "IL-1234"
        );
        inject(new OpenCompanyHandler(), "companyService", companyService).execute(params, context);

        verify(companyService).createProductionCompany("t", 7, "SoundWave", "info@sw.com", "050-111-0001", "IL-1234");
    }

    @Test
    void GivenOwnerAndAppointee_WhenAppointOwnerExecuted_ThenCallsRequestThenRespondWithRightValues() {
        when(companyService.requestAppointOwner(anyString(), anyInt(), anyInt())).thenReturn(new Response<>(true, null));
        when(companyService.respondToOwnerAppointment(anyString(), anyInt(), anyBoolean())).thenReturn(new Response<>(true, null));

        Map<String, String> params = Map.of(
                "ownerToken", "owner-t", "appointeeToken", "appointee-t",
                "companyId", "1", "appointeeId", "5"
        );
        inject(new AppointOwnerHandler(), "companyService", companyService).execute(params, context);

        InOrder order = inOrder(companyService);
        order.verify(companyService).requestAppointOwner("owner-t", 1, 5);
        order.verify(companyService).respondToOwnerAppointment("appointee-t", 1, true);
    }

    @Test
    void GivenPermissionsCsv_WhenAppointManagerExecuted_ThenCallsRequestWithParsedPermissions() {
        when(companyService.requestAppointManager(anyString(), anyInt(), anyInt(), any())).thenReturn(new Response<>(true, null));
        when(companyService.respondToManagerAppointment(anyString(), anyInt(), anyBoolean())).thenReturn(new Response<>(true, null));

        Map<String, String> params = Map.of(
                "ownerToken", "owner-t", "appointeeToken", "appointee-t",
                "companyId", "1", "appointeeId", "5",
                "permissions", "CREATE_EVENT, DELETE_EVENT"
        );
        inject(new AppointManagerHandler(), "companyService", companyService).execute(params, context);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<PermissionType>> permsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(companyService).requestAppointManager(eq("owner-t"), eq(1), eq(5), permsCaptor.capture());
        assertEquals(Set.of(PermissionType.CREATE_EVENT, PermissionType.DELETE_EVENT), permsCaptor.getValue());
        verify(companyService).respondToManagerAppointment("appointee-t", 1, true);
    }

    @Test
    void GivenEventParams_WhenCreateEventExecuted_ThenCallsCreateEventWithRightValues() {
        when(eventService.createEvent(anyString(), anyInt(), any(), anyString(), any(), anyBoolean(), any(), any()))
                .thenReturn(new Response<>(99, null));

        Map<String, String> params = Map.of(
                "token", "t", "companyId", "1", "name", "Rock Night",
                "date", "+1M", "saleStart", "-1D",
                "area", "CENTER", "category", "LIVEMUSIC", "hasLottery", "true"
        );
        inject(new CreateEventHandler(), "eventService", eventService).execute(params, context);

        verify(eventService).createEvent(eq("t"), eq(1), any(LocalDateTime.class), eq("Rock Night"),
                any(LocalDateTime.class), eq(true), eq(GeographicalArea.CENTER), eq(CategoryEvent.LIVEMUSIC));
    }

    @Test
    void GivenZoneParams_WhenDefineVenueExecuted_ThenCallsDefineVenueWithParsedZones() {
        when(eventService.DefineVenueAndSeatingMap(anyString(), anyInt(), any(), any(), any(), any()))
                .thenReturn(new Response<>(true, null));

        Map<String, String> params = Map.of(
                "token", "t", "eventId", "12",
                "standingCapacity", "30", "standingPrice", "50.0", "standingName", "Standing",
                "seatingRows", "10", "seatingCols", "8", "seatingPrice", "100.0", "seatingName", "Seating"
        );
        inject(new DefineVenueHandler(), "eventService", eventService).execute(params, context);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StandingZoneDTO>> standingCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SeatingZoneDTO>> seatingCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventService).DefineVenueAndSeatingMap(eq("t"), eq(12), any(), any(),
                standingCaptor.capture(), seatingCaptor.capture());

        StandingZoneDTO standing = standingCaptor.getValue().get(0);
        assertEquals(30, standing.getCapacty());
        assertEquals(50.0, standing.getPrice());
        assertEquals("Standing", standing.getName());

        SeatingZoneDTO seating = seatingCaptor.getValue().get(0);
        assertEquals(10, seating.getRows());
        assertEquals(8, seating.getCols());
        assertEquals(100.0, seating.getPrice());
        assertEquals("Seating", seating.getName());
    }

    @Test
    void GivenDiscountParams_WhenAddCompanyDiscountExecuted_ThenCallsAddDiscountToCompanyWithRightValues() {
        when(companyService.addDiscountToCompany(anyString(), anyInt(), any())).thenReturn(new Response<>(true, null));

        Map<String, String> params = Map.of(
                "token", "t", "companyId", "1",
                "couponCode", "SALE20", "percent", "20.0", "expiryDaysFromNow", "30"
        );
        inject(new AddCompanyDiscountHandler(), "companyService", companyService).execute(params, context);

        ArgumentCaptor<DiscountDTO> captor = ArgumentCaptor.forClass(DiscountDTO.class);
        verify(companyService).addDiscountToCompany(eq("t"), eq(1), captor.capture());
        assertEquals("SALE20", captor.getValue().getCode());
        assertEquals(20.0, captor.getValue().getPercentage());
    }

    @Test
    void GivenDiscountParams_WhenAddEventDiscountExecuted_ThenCallsAddDiscountToEventWithRightValues() {
        when(eventService.addDiscountToEvent(anyString(), anyInt(), any())).thenReturn(new Response<>(true, null));

        Map<String, String> params = Map.of(
                "token", "t", "eventId", "12",
                "couponCode", "ROCK50", "percent", "50.0", "expiryDaysFromNow", "10"
        );
        inject(new AddEventDiscountHandler(), "eventService", eventService).execute(params, context);

        ArgumentCaptor<DiscountDTO> captor = ArgumentCaptor.forClass(DiscountDTO.class);
        verify(eventService).addDiscountToEvent(eq("t"), eq(12), captor.capture());
        assertEquals("ROCK50", captor.getValue().getCode());
        assertEquals(50.0, captor.getValue().getPercentage());
    }

    @Test
    void GivenRuleParams_WhenAddCompanyRuleExecuted_ThenCallsAddRuleToCompanyWithRightValues() {
        when(companyService.addRuleToCompany(anyString(), anyInt(), any())).thenReturn(new Response<>(true, null));

        Map<String, String> params = Map.of(
                "token", "t", "companyId", "1", "ruleType", "MIN_AGE", "value", "18"
        );
        inject(new AddCompanyRuleHandler(), "companyService", companyService).execute(params, context);

        ArgumentCaptor<PurchaseRuleDTO> captor = ArgumentCaptor.forClass(PurchaseRuleDTO.class);
        verify(companyService).addRuleToCompany(eq("t"), eq(1), captor.capture());
        assertEquals(PurchaseRuleDTO.Type.MIN_AGE, captor.getValue().getType());
        assertEquals(18, captor.getValue().getMinAge());
    }

    @Test
    void GivenLotteryParams_WhenCreateLotteryExecuted_ThenCallsCreateLotteryWithRightValues() {
        when(lotteryService.createLottery(anyString(), anyInt(), anyInt(), any(), anyLong()))
                .thenReturn(new Response<>(true, null));

        Map<String, String> params = Map.of(
                "token", "t", "eventId", "12", "capacity", "1",
                "minutesFromNow", "2", "expirationHours", "24"
        );
        inject(new CreateLotteryHandler(), "lotteryService", lotteryService).execute(params, context);

        verify(lotteryService).createLottery(eq("t"), eq(12), eq(1), any(LocalDateTime.class), eq(24L));
    }
}
