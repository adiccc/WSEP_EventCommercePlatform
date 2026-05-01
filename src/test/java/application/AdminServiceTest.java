package application;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import Log.LoggerSetup;
import domain.activeOrder.IActiveOrderRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dto.UserDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.event.OrderStatus;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class AdminServiceTest {

    private AdminService adminService;
    private UserService userService;
    private String adminToken;
    private String nonAdminToken;
    private ICompanyRepo companyRepo;
    private IUserRepo userRepo;
    private IPaymentSystem paymentSystem;
    private ITicketSupply ticketSupply;
    private IEventRepo eventRepo;
    private EventCompanyManageService eventCompanyManageService;
    private CompanyService companyService;
    private ActiveOrderService activeOrderService;
    private final int companyId = 900;
    private LocalDateTime eventDate;
    private Integer eventId;
    private ElementPositionDTO stage;
    private List<ElementPositionDTO> entries;
    private List<StandingZoneDTO> standingZones;
    private List<SeatingZoneDTO> seatingZones;

    private static final String ADMIN_EMAIL = "admin@bgu.ac.il";
    private static final String USER_EMAIL = "user@bgu.ac.il";
    private static final String PASSWORD = "Pass123!";

    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
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

        IActiveOrderRepo activeOrderRepo =new ActiveOrderRepoImpl();
        ILotteryRepo lotteryRepo = new LotteryRepoImpl();
        activeOrderService=new ActiveOrderService(auth,activeOrderRepo,eventRepo,companyRepo,lotteryRepo,paymentSystem, ticketSupply,100,10);

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

        activeOrderService.placeOrder(adminToken,eventId,1);

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
        companyService.deactivateCompany(adminToken, companyId);

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
    void GivenNotActivePaymentSystem_WhenCloseCompany_ThenCompanyClosedAndFailureHandled() {
        // Arrange: Add an order to the existing event to test the refund mechanism
        activeOrderService.placeOrder(adminToken,eventId,1);

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

    // ============================================================
    // II.6.1  Remove User from System - Acceptance Tests
    // ============================================================

    /** Convenience: register a new user and return their assigned userId. */
    private int registerUser(String email) {
        userService.registerUser(null, new UserDTO(
                email, "Test", "User", PASSWORD, 1, 1, 1995, "City", "050-999-0000"));
        return userRepo.findUserByEmail(email).getUserId();
    }

    // --- Successful_Removal (plain member) ---
    @Test
    void GivenAdminToken_WhenRemovePlainUser_ThenUserDeactivated() {
        int plainId = registerUser("plain@accept.com");

        Response<Boolean> response = adminService.removeUser(adminToken, plainId);

        assertFalse(response.isError(), "Expected success but got: " + response.getMessage());
        assertTrue(response.getValue());
        assertFalse(userRepo.findById(plainId).isActive(),
                "User should be deactivated in the repo after removal");
    }

    // --- Unauthorized_Non_Admin ---
    @Test
    void GivenNonAdminToken_WhenRemoveUser_ThenUnauthorized() {
        int plainId = registerUser("noadmin_target@accept.com");

        Response<Boolean> response = adminService.removeUser(nonAdminToken, plainId);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
        assertTrue(userRepo.findById(plainId).isActive(), "Target user should remain active");
    }

    // --- User_Not_Found ---
    @Test
    void GivenNonExistentUserId_WhenRemoveUser_ThenError() {
        Response<Boolean> response = adminService.removeUser(adminToken, 99999);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("not found"));
    }

    // --- User_Already_Removed ---
    @Test
    void GivenAlreadyRemovedUser_WhenRemoveUserAgain_ThenError() {
        int userId = registerUser("once@accept.com");
        adminService.removeUser(adminToken, userId); // first removal - succeeds

        Response<Boolean> response = adminService.removeUser(adminToken, userId); // second attempt

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("already removed"));
    }

    // --- Founder_Blocked ---
    @Test
    void GivenUserIsCompanyFounder_WhenRemoveUser_ThenBlockedWithFounderError() {
        // The admin user is the founder of the company created in setUp()
        int adminId = userRepo.findUserByEmail(ADMIN_EMAIL).getUserId();

        Response<Boolean> response = adminService.removeUser(adminToken, adminId);

        assertTrue(response.isError());
        assertTrue(response.getMessage().toLowerCase().contains("founder"),
                "Error should mention 'founder'; got: " + response.getMessage());
        assertTrue(userRepo.findById(adminId).isActive(), "Founder should remain active");
    }

    // --- Logged_Out_Admin_Token ---
    @Test
    void GivenLoggedOutAdminToken_WhenRemoveUser_ThenUnauthorized() {
        int targetId = registerUser("target@accept.com");
        userService.logout(adminToken); // invalidate the admin's session

        Response<Boolean> response = adminService.removeUser(adminToken, targetId);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("Unauthorized"));
        assertTrue(userRepo.findById(targetId).isActive(), "Target user should not be removed");
    }

    // --- Concurrency: Remove User ---
    @Test
    void GivenTwoThreadsRaceToRemoveSameUser_WhenRemoveUser_ThenOnlyOneSucceeds() throws Exception {
        int targetId = registerUser("race_remove@accept.com");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        java.util.concurrent.Future<Response<Boolean>> f1 = executor.submit(() -> {
            start.await();
            return adminService.removeUser(adminToken, targetId);
        });
        java.util.concurrent.Future<Response<Boolean>> f2 = executor.submit(() -> {
            start.await();
            return adminService.removeUser(adminToken, targetId);
        });

        start.countDown();

        Response<Boolean> r1 = f1.get();
        Response<Boolean> r2 = f2.get();
        executor.shutdown();

        int success = 0;
        int failed  = 0;
        if (!r1.isError()) success++; else failed++;
        if (!r2.isError()) success++; else failed++;

        assertEquals(1, success, "Only one removal should succeed");
        assertEquals(1, failed,  "The second thread should see 'already removed'");
        assertFalse(userRepo.findById(targetId).isActive(), "User must be deactivated exactly once");
    }

    // Race Condition
    @Test
    void GivenHighLoad_WhenAdminClosesCompanyAndManagerUpdatesDateSimultaneously_ThenSystemRemainsConsistent() throws InterruptedException {
        // Arrange: Prepare a new date for the manager to set
        LocalDateTime newDate = eventDate.plusDays(10);

        // Setup concurrency tools
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(2);

        // Act: Thread 1 - Admin attempts to close the company
        executor.submit(() -> {
            try {
                startGun.await();
                adminService.closeCompanyByAdmin(adminToken, companyId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finishLine.countDown();
            }
        });

        // Act: Thread 2 - Manager attempts to update the event date
        executor.submit(() -> {
            try {
                startGun.await();
                eventCompanyManageService.UpdateEventDate(nonAdminToken, eventId, newDate);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finishLine.countDown();
            }
        });

        // Both threads execute exactly at the same millisecond
        startGun.countDown();
        finishLine.await(); // Wait for both threads to completely finish

        // Assert: Verify system consistency
        Company updatedCompany = companyRepo.findById(companyId);
        Event updatedEvent = eventRepo.findById(eventId);

        // Regardless of which thread finished first, the critical business rule is that a closed company means its events must be inactive.
        assertFalse(updatedCompany.isActive(), "Company should be closed by the admin");
        assertFalse(updatedEvent.isActive(), "Event should be deactivated due to company closure");

        executor.shutdown();
    }

    @Test
    void GivenHighLoad_WhenAdminClosesCompanyAndManagerAddsZoneSimultaneously_ThenSystemRemainsConsistent() throws InterruptedException {
        // Arrange: Prepare new zones for the manager to add
        List<StandingZoneDTO> newStandingZones = List.of(new StandingZoneDTO(500, "Golden Ring", 300.0, new ElementPositionDTO(2, 2)));

        // Setup concurrency tools
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(2);

        // Act: Thread 1 - Admin attempts to close the company
        executor.submit(() -> {
            try {
                startGun.await();
                adminService.closeCompanyByAdmin(adminToken, companyId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finishLine.countDown();
            }
        });

        // Act: Thread 2 - Manager attempts to add zones to the event map
        executor.submit(() -> {
            try {
                startGun.await();
                eventCompanyManageService.AddZonesToEventMap(nonAdminToken, eventId, newStandingZones, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finishLine.countDown();
            }
        });

        // Both threads execute exactly at the same millisecond
        startGun.countDown();
        finishLine.await();

        // Assert: Verify data integrity
        Company updatedCompany = companyRepo.findById(companyId);
        Event updatedEvent = eventRepo.findById(eventId);

        // The company and event MUST be inactive at the end of the process.
        assertFalse(updatedCompany.isActive(), "Company should be closed");
        assertFalse(updatedEvent.isActive(), "Event should be deactivated");

        executor.shutdown();
    }
}
