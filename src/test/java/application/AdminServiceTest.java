package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dto.UserDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.event.OrderStatus;
import domain.event.IEventRepo;
import domain.user.IUserRepo;
import domain.webQueue.WebQueue;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import domain.event.Order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdminServiceTest {

    private AdminService adminService;
    private UserService userService;
    private String adminToken;
    private String nonAdminToken;
    private ICompanyRepo companyRepo;
    private IUserRepo userRepo;
    private IPaymentSystem paymentSystem;
    private IEventRepo eventRepo;
    private EventCompanyManageService eventCompanyManageService;
    private CompanyService companyService;
    private final int companyId = 900;
    private LocalDateTime eventDate;
    private String eventId;
    private ElementPositionDTO stage;
    private List<ElementPositionDTO> entries;
    private List<StandingZoneDTO> standingZones;
    private List<SeatingZoneDTO> seatingZones;

    private static final String ADMIN_EMAIL = "admin@bgu.ac.il";
    private static final String USER_EMAIL = "user@bgu.ac.il";
    private static final String PASSWORD = "Pass123!";

    @BeforeEach
    void setUp() {
        WebQueue.resetForTesting();
        WebQueue.getInstance(100);

        TokenService tokenService = new TokenService();
        userRepo = new UserRepo();
        eventRepo = new EventRepoImpl();
        companyRepo = new CompanyRepoImpl();
        paymentSystem = Mockito.mock(IPaymentSystem.class);

        IPasswordEncoder passwordEncoder = new PasswordEncoderUtil();
        IAuth auth = new Auth(tokenService, userRepo, passwordEncoder, Set.of(ADMIN_EMAIL));

        userService = new UserService(tokenService, auth, userRepo, passwordEncoder);

        adminService = new AdminService(auth, userRepo, companyRepo, eventRepo,paymentSystem);

        eventCompanyManageService = new EventCompanyManageService(companyRepo, eventRepo, auth, paymentSystem);
        companyService = new CompanyService(auth, companyRepo, userRepo);

        UserDTO adminDTO = new UserDTO(ADMIN_EMAIL, "Admin", "User", PASSWORD, 1, 1, 1990, "City", "050-000-0000");
        UserDTO userDTO = new UserDTO(USER_EMAIL, "Regular", "User", PASSWORD, 1, 1, 1990, "City", "050-111-1111");
        userService.registerUser(null, adminDTO);
        userService.registerUser(null, userDTO);

        adminToken = userService.login(ADMIN_EMAIL, PASSWORD).getValue();
        nonAdminToken = userService.login(USER_EMAIL, PASSWORD).getValue();

        Response<Company> c = companyService.createProductionCompany(adminToken, companyId, "test-company",
                "testC@company.com", "054-5556677", "leumi");

        eventId = eventCompanyManageService
                .createEvent(adminToken, companyId, LocalDateTime.now().plusDays(10), "test-event",
                        LocalDateTime.now().plusDays(5), false, GeographicalArea.CENTER, CategoryEvent.FESTIVAL)
                .getValue();
        eventDate = LocalDateTime.now().plusDays(10);
        stage = new ElementPositionDTO(10, 20);
        entries = List.of(new ElementPositionDTO(0, 0), new ElementPositionDTO(50, 10));
        standingZones = List.of(new StandingZoneDTO(200, "floor", 100.0, new ElementPositionDTO(1, 1)));
        seatingZones = List.of(new SeatingZoneDTO(10, 20, "tribune", 150.0, new ElementPositionDTO(5, 5)));
        eventCompanyManageService.DefineVenueAndSeatingMap(adminToken, eventId, stage, entries, standingZones,
                seatingZones);
    }

    // --- setMaxCapacity ---

    @Test
    void GivenAdminToken_WhenSetMaxCapacity_ThenSuccess() {
        Response<Boolean> response = adminService.setMaxCapacity(adminToken, 200);

        assertFalse(response.isError());
        assertTrue(response.getValue());
        assertEquals(200, WebQueue.getInstance().getMaxCapacity());
    }

    @Test
    void GivenNonAdminToken_WhenSetMaxCapacity_ThenUnauthorized() {
        Response<Boolean> response = adminService.setMaxCapacity(nonAdminToken, 200);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
        assertEquals(100, WebQueue.getInstance().getMaxCapacity());
    }

    @Test
    void GivenAdminToken_WhenSetMaxCapacityZero_ThenError() {
        Response<Boolean> response = adminService.setMaxCapacity(adminToken, 0);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("greater than 0"));
    }

    @Test
    void GivenAdminToken_WhenSetMaxCapacityNegative_ThenError() {
        Response<Boolean> response = adminService.setMaxCapacity(adminToken, -5);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("greater than 0"));
    }

    // --- getMaxCapacity ---

    @Test
    void GivenAdminToken_WhenGetMaxCapacity_ThenReturnsCapacity() {
        Response<Integer> response = adminService.getMaxCapacity(adminToken);

        assertFalse(response.isError());
        assertEquals(100, response.getValue());
    }

    @Test
    void GivenNonAdminToken_WhenGetMaxCapacity_ThenUnauthorized() {
        Response<Integer> response = adminService.getMaxCapacity(nonAdminToken);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
    }

    // --- getActiveCount ---

    @Test
    void GivenAdminToken_WhenGetActiveCount_ThenReturnsCount() {
        WebQueue.getInstance().tryEnter(uuid -> {
        });
        WebQueue.getInstance().tryEnter(uuid -> {
        });

        Response<Integer> response = adminService.getActiveCount(adminToken);

        assertFalse(response.isError());
        assertEquals(2, response.getValue());
    }

    @Test
    void GivenNonAdminToken_WhenGetActiveCount_ThenUnauthorized() {
        Response<Integer> response = adminService.getActiveCount(nonAdminToken);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
    }

    // --- getWaitingCount ---

    @Test
    void GivenAdminToken_WhenGetWaitingCount_ThenReturnsCount() {
        WebQueue.resetForTesting();
        WebQueue.getInstance(1);
        WebQueue.getInstance().tryEnter(uuid -> {
        }); // fills the slot
        WebQueue.getInstance().tryEnter(uuid -> {
        }); // goes to queue
        WebQueue.getInstance().tryEnter(uuid -> {
        }); // goes to queue

        Response<Integer> response = adminService.getWaitingCount(adminToken);

        assertFalse(response.isError());
        assertEquals(2, response.getValue());
    }

    @Test
    void GivenNonAdminToken_WhenGetWaitingCount_ThenUnauthorized() {
        Response<Integer> response = adminService.getWaitingCount(nonAdminToken);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
    }

    // --- invalidToken ---

    @Test
    void GivenInvalidToken_WhenAnyAdminAction_ThenUnauthorized() {
        Response<Boolean> setCapacity = adminService.setMaxCapacity("invalid-token", 50);
        Response<Integer> getCapacity = adminService.getMaxCapacity("invalid-token");
        Response<Integer> getActive = adminService.getActiveCount("invalid-token");
        Response<Integer> getWaiting = adminService.getWaitingCount("invalid-token");

        assertTrue(setCapacity.isError());
        assertTrue(getCapacity.isError());
        assertTrue(getActive.isError());
        assertTrue(getWaiting.isError());
    }

    @Test
    void GivenValidInputs_WhenCloseCompanyByAdmin_ThenCompanyAndEventsClosed() {
        // Arrange
        // To Do: use createOrder when implemented
        Event event = eventRepo.findById(eventId);
        Order order = new Order(1, 999, eventId, List.of(1, 2), 300.0, "pay_123");
        event.getOrders().add(order);
        eventRepo.store(event);

        // Mock the external payment system to simulate a successful refund process
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble())).thenReturn(true);
        // Act: Admin requests to close the company
        Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, companyId);

        // Assert: Check response indicates success
        assertTrue(response.getValue());
        assertEquals("Company closed successfully", response.getMessage());

        // Assert: Verify the company status was updated to inactive
        Company updatedCompany = companyRepo.findById(companyId);
        assertFalse(updatedCompany.isActive());

        // Assert: Verify the associated future events are canceled (inactive)
        Event updatedEvent = eventRepo.findById(eventId);
        assertFalse(updatedEvent.isActive());

        // Assert: Verify the order was marked for a refund
        Order updatedOrder = updatedEvent.findOrderById(1);
        assertEquals(OrderStatus.REFUNDED, updatedOrder.getStatus());
    }

    @Test
    void GivenNonAdminToken_WhenCloseCompany_ThenErrorUnauthorized() {
        // Act: Attempt closure with the company owner's token (not a system admin)
        Response<Boolean> response = adminService.closeCompanyByAdmin(nonAdminToken, companyId);

        // Assert: Operation should be blocked
        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));

        // Assert: Verify the company remains active
        Company unchangedCompany = companyRepo.findById(companyId);
        assertTrue(unchangedCompany.isActive());
    }

    @Test
    void GivenNonExistCompany_WhenCloseCompany_ThenErrorNotFound() {
        // Act: Attempt to close a company ID that does not exist in the repo
        Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, 9999);

        // Assert: Verify standard not found error
        assertTrue(response.isError());
        assertEquals("Company not found", response.getMessage());
    }

    @Test
    void GivenCloseAlreadyClosedCompany_WhenCloseCompany_ThenErrorAlreadyClosed() {
        // Arrange: Deactivate the pre-existing company first
        Company company = companyRepo.findById(companyId);
        company.deactivate();
        companyRepo.store(company);

        // Act: Attempt to close it again
        Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, companyId);

        // Assert: System should detect the state and prevent redundant operations
        assertTrue(response.isError());
        assertEquals("Company is already closed", response.getMessage());
    }

    @Test
    void GivenInvalidToken_WhenCloseCompany_ThenErrorUnauthorized() {
        // Act: Use a logged out token on the active company
        userService.logout(adminToken);
        Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, companyId);

        // Assert: Operation should be blocked
        assertTrue(response.isError());
        assertEquals("Unauthorized: admin access required", response.getMessage());

        // Assert: Company remains active
        assertTrue(companyRepo.findById(companyId).isActive());
    }

    @Test
    void GivenAdminToken_WhenRefundFailsAfterClosure_ThenCompanyClosedAndFailureHandled() {
        // Arrange: Add an order to the existing event to test the refund mechanism
        Event event = eventRepo.findById(eventId);
        Order order = new Order(1, 333, eventId, List.of(1, 2), 300.0, "pay_123");
        event.getOrders().add(order);
        eventRepo.store(event);

        // Mock the external payment system to return false, simulating a refund failure
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble())).thenReturn(false);

        // Act: The system admin requests to close the company
        Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, companyId);

        // Assert: The main company closure operation should succeed despite the refund
        // failure
        assertTrue(response.getValue());
        assertEquals("Company closed successfully", response.getMessage());

        // Assert: Verify the company status was updated to inactive
        Company updatedCompany = companyRepo.findById(companyId);
        assertFalse(updatedCompany.isActive());

        // Assert: Verify the future event was canceled (inactive)
        Event updatedEvent = eventRepo.findById(eventId);
        assertFalse(updatedEvent.isActive());

        // Assert: The refund failed, so the order status remains REFUND_REQUIRED
        Order updatedOrder = updatedEvent.findOrderById(1);
        assertEquals(domain.event.OrderStatus.REFUND_REQUIRED, updatedOrder.getStatus());

    }
}
