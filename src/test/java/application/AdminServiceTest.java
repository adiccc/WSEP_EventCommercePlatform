package application;

import DTO.*;
import Log.LoggerSetup;
import com.vaadin.copilot.SpringIntegration;
import com.vaadin.flow.shared.Registration;
import domain.Suspension.ISuspensionRepo;
import domain.activeOrder.IActiveOrderRepo;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dto.OrderDTO;
import domain.dto.SuspensionDTO;
import domain.dto.UserDTO;
import domain.event.Event;
import domain.event.IEventRepo;
import domain.event.OrderStatus;
import domain.lottery.ILotteryRepo;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.webQueue.WebQueue;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import domain.event.Order;
import domain.dto.EventMapDTO;
import Exception.OptimisticLockingFailureException;

import java.util.*;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AdminServiceTest {

    private AdminService adminService;
    private UserService userService;
    private String adminToken;
    private String nonAdminToken;
    private ICompanyRepo companyRepo;
    private IUserRepo userRepo;
    private ISuspensionRepo suspensionRepo;
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
    private INotifier notifier;
    private IAuth auth;
    private TokenService tokenService;

    private static final String ADMIN_EMAIL = "admin@bgu.ac.il";
    private static final String USER_EMAIL = "user@bgu.ac.il";
    private static final String PASSWORD = "Pass123!";

    private int userIdNotSuspened;
    private String userNotSusToken;

    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
        WebQueue.resetForTesting();
        WebQueue.getInstance(100);

        tokenService = new TokenService();
        userRepo = new UserRepo();
        eventRepo = new EventRepoImpl();
        companyRepo = new CompanyRepoImpl();
        suspensionRepo = new SuspensionRepoImpl();
        paymentSystem = Mockito.mock(IPaymentSystem.class);
        ticketSupply = Mockito.mock(ITicketSupply.class);

        IPasswordEncoder passwordEncoder = new PasswordEncoderUtil();
        auth = new Auth(tokenService, Set.of(ADMIN_EMAIL));
        notifier = new VaadinNotifier();
        userService = new UserService(tokenService, auth, userRepo, passwordEncoder,notifier);

        adminService = new AdminService(auth, userRepo, companyRepo, eventRepo,paymentSystem,suspensionRepo,notifier);

        IActiveOrderRepo activeOrderRepo =new ActiveOrderRepoImpl();
        ILotteryRepo lotteryRepo = new LotteryRepoImpl();
        activeOrderService=new ActiveOrderService(auth,activeOrderRepo,eventRepo,companyRepo,lotteryRepo,paymentSystem, ticketSupply,suspensionRepo,notifier,new PreExpirationNotificationScheduler(activeOrderRepo,notifier,auth),userRepo,100);

        eventCompanyManageService = new EventCompanyManageService(companyRepo, eventRepo, auth, paymentSystem,suspensionRepo,notifier,userRepo);
        companyService = new CompanyService(auth, companyRepo, userRepo,suspensionRepo,notifier);

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
                        LocalDateTime.now().minusMinutes(10), false, GeographicalArea.CENTER, CategoryEvent.FESTIVAL)
                .getValue();
        eventDate = LocalDateTime.now().plusDays(10);
        stage = new ElementPositionDTO(10, 20);
        entries = List.of(new ElementPositionDTO(0, 0), new ElementPositionDTO(50, 10));
        standingZones = List.of(new StandingZoneDTO(200, "floor", 100.0, new ElementPositionDTO(1, 1)));
        seatingZones = List.of(new SeatingZoneDTO(10, 20, "tribune", 150.0, new ElementPositionDTO(5, 5)));
        eventCompanyManageService.DefineVenueAndSeatingMap(adminToken, eventId, stage, entries, standingZones,
                seatingZones);

        userService.registerUser(null, new UserDTO("notSuspenededUser@gmail.com","notSuspenededUser","test","test",1,1,2000,"test-addtess","050-000-0032"));
        userNotSusToken = userService.login("notSuspenededUser@gmail.com","test").getValue();
        userIdNotSuspened=userService.getUserId(userNotSusToken).getValue();

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
    void GivenValidInputsAndBuyerOnline_WhenCloseCompanyByAdmin_ThenCompanyAndEventsClosedAndRefundNotificationSentRealtime() throws InterruptedException {
        // Arrange: Create an order and mock payment success
        int orderId = createCompletedOrderThroughPurchaseFlow(nonAdminToken, eventId, 1);
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble())).thenReturn(true);

        // Arrange: Extract identifiers to set up specific WebSocket listeners
        Event event = eventRepo.findById(eventId);
        String buyerIdentifier = event.findOrderById(orderId).getUserIdentifier();

        CountDownLatch buyerLatch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> buyerNotification = new AtomicReference<>();
        Registration buyerReg = Broadcaster.registerUser(buyerIdentifier, dto -> {
            buyerNotification.set(dto);
            buyerLatch.countDown();
        });

        CountDownLatch ownerLatch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> ownerNotification = new AtomicReference<>();
        String ownerIdentifier = auth.getUserEmail(adminToken).getValue();

        Registration ownerReg = Broadcaster.registerUser(ownerIdentifier, dto -> {
            ownerNotification.set(dto);
            ownerLatch.countDown();
        });

        try {
            // Act: Admin requests to close the company
            Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, companyId);

            // Assert: database state and response
            assertTrue(response.getValue());
            assertEquals("Company closed successfully", response.getMessage());
            assertFalse(companyRepo.findById(companyId).isActive());
            assertFalse(eventRepo.findById(eventId).isActive());

            assertEquals(OrderStatus.REFUNDED,
                    eventRepo.findById(eventId).findOrderById(orderId).getStatus());

            // Assert: buyer refund notification was delivered in real time
            assertTrue(buyerLatch.await(2000, TimeUnit.MILLISECONDS), "Buyer notification timeout");
            assertNotNull(buyerNotification.get());
            assertEquals(NotifyType.GENERAL_POPUP, buyerNotification.get().getType());
            assertTrue(buyerNotification.get().getPayload().getMessage().contains("Refund processed"),
                    "Buyer should receive refund processed notification in real time: "
                            + buyerNotification.get().getPayload().getMessage());

            Member buyer = userRepo.findUserByEmail(buyerIdentifier);

            assertFalse(buyer.getDelayedNotifications().stream()
                            .anyMatch(n ->
                                    n.getType() == NotifyType.GENERAL_POPUP
                                            && n.getPayload() != null
                                            && n.getPayload().getMessage().contains("Refund processed")
                            ),
                    "Online buyer should not have the refund processed notification saved as delayed");

            // Assert: owner notification was also delivered in real time
            assertTrue(ownerLatch.await(2000, TimeUnit.MILLISECONDS), "Owner notification timeout");
            assertNotNull(ownerNotification.get());
            assertEquals(NotifyType.GENERAL_POPUP, ownerNotification.get().getType());
            assertTrue(ownerNotification.get().getPayload().getMessage().contains("Company"),
                    "Owner should receive company closure notification in real time: "
                            + ownerNotification.get().getPayload().getMessage());

        } finally {
            buyerReg.remove();
            ownerReg.remove();
        }
    }

    @Test
    void GivenValidInputsAndBuyerOffline_WhenCloseCompanyByAdmin_ThenCompanyAndEventsClosedAndRefundNotificationSavedAsDelayed() {
        // Arrange: Create an order and mock payment success
        int orderId = createCompletedOrderThroughPurchaseFlow(nonAdminToken, eventId, 1);
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble())).thenReturn(true);

        Event event = eventRepo.findById(eventId);
        String buyerIdentifier = event.findOrderById(orderId).getUserIdentifier();

        // Buyer is offline/logged out while the admin closes the company.
        // Do not register the buyer in Broadcaster, so the refund notification should be saved as delayed.
        userService.logout(nonAdminToken);

        CountDownLatch ownerLatch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> ownerNotification = new AtomicReference<>();
        String ownerIdentifier = auth.getUserEmail(adminToken).getValue();
        Registration ownerReg = Broadcaster.registerUser(ownerIdentifier, dto -> {
            ownerNotification.set(dto);
            ownerLatch.countDown();
        });

        try {
            // Act: Admin requests to close the company
            Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, companyId);

            // Assert: database state and response
            assertTrue(response.getValue());
            assertEquals("Company closed successfully", response.getMessage());
            assertFalse(companyRepo.findById(companyId).isActive());
            assertFalse(eventRepo.findById(eventId).isActive());
            assertEquals(OrderStatus.REFUNDED,
                    eventRepo.findById(eventId).findOrderById(orderId).getStatus());

            // Assert: buyer refund notification was saved as delayed
            Member buyer = userRepo.findUserByEmail(buyerIdentifier);
            List<NotifyDTO> delayedNotifications = buyer.getDelayedNotifications();

            assertTrue(delayedNotifications.stream()
                            .anyMatch(n ->
                                    n.getType() == NotifyType.GENERAL_POPUP
                                            && n.getPayload() != null
                                            && n.getPayload().getMessage().contains("Refund processed")
                                            && n.getPayload().getMessage().contains("because of closing the company")
                            ),
                    "Buyer should have a delayed refund processed notification");


            // Assert: owner notification was still delivered in real time
            try {
                assertTrue(ownerLatch.await(2000, TimeUnit.MILLISECONDS), "Owner notification timeout");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertNotNull(ownerNotification.get());
            assertTrue(ownerNotification.get().getPayload().getMessage().contains("Company"));

        } finally {
            ownerReg.remove();
        }
    }

    @Test
    void GivenNonAdminToken_WhenCloseCompany_ThenErrorUnauthorized() throws InterruptedException {
        // Arrange: Retrieve the correct string identifier for the company owner
        String ownerIdentifier = auth.getUserIdentifier(adminToken).getValue();
        CountDownLatch shouldNotFire = new CountDownLatch(1);
        Registration reg = Broadcaster.registerUser(ownerIdentifier, dto -> shouldNotFire.countDown());

        try {
            // Act: Attempt closure with non-admin
            Response<Boolean> response = adminService.closeCompanyByAdmin(nonAdminToken, companyId);

            // Assert: Verify expected failure
            assertTrue(response.isError());
            assertTrue(response.getMessage().contains("Unauthorized"));
            assertTrue(companyRepo.findById(companyId).isActive());

            // Assert: Ensure latch times out, meaning no notification was dispatched to the real owner
            assertFalse(shouldNotFire.await(500, TimeUnit.MILLISECONDS), "Notification was wrongly sent on unauthorized access");
        } finally {
            reg.remove();
        }
    }

    @Test
    void GivenNonExistCompany_WhenCloseCompany_ThenErrorNotFound() throws InterruptedException {
        // Arrange: Listen to the admin identifier since the company doesn't exist
        String adminIdentifier = auth.getUserIdentifier(adminToken).getValue();
        CountDownLatch shouldNotFire = new CountDownLatch(1);
        Registration reg = Broadcaster.registerUser(adminIdentifier, dto -> shouldNotFire.countDown());

        try {
            // Act: Attempt to close non-existent company
            Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, 9999);

            // Assert
            assertTrue(response.isError());
            assertEquals("Company not found", response.getMessage());

            // Assert: Ensure no notifications were dispatched
            assertFalse(shouldNotFire.await(500, TimeUnit.MILLISECONDS));
        } finally {
            reg.remove();
        }
    }

    @Test
    void GivenCloseAlreadyClosedCompany_WhenCloseCompany_ThenErrorAlreadyClosed() throws InterruptedException {
        // Arrange: Deactivate company first
        companyService.deactivateCompany(adminToken, companyId);

        // Arrange: Retrieve correct owner identifier
        String ownerIdentifier = auth.getUserIdentifier(adminToken).getValue();
        CountDownLatch shouldNotFire = new CountDownLatch(1);
        Registration reg = Broadcaster.registerUser(ownerIdentifier, dto -> shouldNotFire.countDown());

        try {
            // Act
            Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, companyId);

            // Assert
            assertTrue(response.isError());
            assertEquals("Company is already closed", response.getMessage());

            // Assert: Verify no notifications were dispatched
            assertFalse(shouldNotFire.await(500, TimeUnit.MILLISECONDS));
        } finally {
            reg.remove();
        }
    }

    @Test
    void GivenInvalidToken_WhenCloseCompany_ThenErrorUnauthorized() throws InterruptedException {
        // Arrange: Retrieve identifier before logging out the token
        String ownerIdentifier = auth.getUserIdentifier(adminToken).getValue();
        userService.logout(adminToken);

        CountDownLatch shouldNotFire = new CountDownLatch(1);
        Registration reg = Broadcaster.registerUser(ownerIdentifier, dto -> shouldNotFire.countDown());

        try {
            // Act
            Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, companyId);

            // Assert
            assertTrue(response.isError());
            assertEquals("Unauthorized: admin access required", response.getMessage());
            assertTrue(companyRepo.findById(companyId).isActive());

            // Assert: Verify no notifications were dispatched
            assertFalse(shouldNotFire.await(500, TimeUnit.MILLISECONDS));
        } finally {
            reg.remove();
        }
    }

    @Test
    void GivenNotActivePaymentSystemAndBuyerOnline_WhenCloseCompany_ThenCompanyClosedAndFailureNotificationSentRealtime() throws InterruptedException {
        // Arrange: Create order using nonAdminToken to cleanly separate buyer from owner
        int orderId = createCompletedOrderThroughPurchaseFlow(nonAdminToken, eventId, 1);
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble())).thenReturn(false);

        Event event = eventRepo.findById(eventId);
        String buyerIdentifier = event.findOrderById(orderId).getUserIdentifier();

        CountDownLatch buyerLatch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> buyerNotification = new AtomicReference<>();
        Registration buyerReg = Broadcaster.registerUser(buyerIdentifier, dto -> {
            buyerNotification.set(dto);
            buyerLatch.countDown();
        });

        CountDownLatch ownerLatch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> ownerNotification = new AtomicReference<>();
        String ownerIdentifier = auth.getUserEmail(adminToken).getValue();

        Registration ownerReg = Broadcaster.registerUser(ownerIdentifier, dto -> {
            ownerNotification.set(dto);
            ownerLatch.countDown();
        });

        try {
            // Act
            Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, companyId);

            // Assert: database state and response
            assertTrue(response.getValue());
            assertEquals("Company closed successfully", response.getMessage());
            assertFalse(companyRepo.findById(companyId).isActive());
            assertFalse(eventRepo.findById(eventId).isActive());

            assertEquals(OrderStatus.REFUND_REQUIRED,
                    eventRepo.findById(eventId).findOrderById(orderId).getStatus());

            // Assert: buyer refund-failure notification was delivered in real time
            assertTrue(buyerLatch.await(2000, TimeUnit.MILLISECONDS), "Buyer notification timeout");
            assertNotNull(buyerNotification.get());
            assertEquals(NotifyType.GENERAL_POPUP, buyerNotification.get().getType());
            assertTrue(buyerNotification.get().getPayload().getMessage().contains("Refund failed"),
                    "Buyer should receive refund failure notification in real time: "
                            + buyerNotification.get().getPayload().getMessage());

            Member buyer = userRepo.findUserByEmail(buyerIdentifier);

            assertFalse(buyer.getDelayedNotifications().stream()
                            .anyMatch(n ->
                                    n.getType() == NotifyType.GENERAL_POPUP
                                            && n.getPayload() != null
                                            && n.getPayload().getMessage().contains("Refund failed")
                            ),
                    "Online buyer should not have the refund failure notification saved as delayed");

            // Assert: owner notification was also delivered in real time
            assertTrue(ownerLatch.await(2000, TimeUnit.MILLISECONDS), "Owner notification timeout");
            assertNotNull(ownerNotification.get());
            assertEquals(NotifyType.GENERAL_POPUP, ownerNotification.get().getType());
            assertTrue(ownerNotification.get().getPayload().getMessage().contains("Company"),
                    "Owner should receive company closure notification in real time: "
                            + ownerNotification.get().getPayload().getMessage());

        } finally {
            buyerReg.remove();
            ownerReg.remove();
        }
    }

    @Test
    void GivenNotActivePaymentSystemAndBuyerOffline_WhenCloseCompany_ThenCompanyClosedAndFailureNotificationSavedAsDelayed() {
        // Arrange: Create order using nonAdminToken to cleanly separate buyer from owner
        int orderId = createCompletedOrderThroughPurchaseFlow(nonAdminToken, eventId, 1);
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble())).thenReturn(false);

        Event event = eventRepo.findById(eventId);
        String buyerIdentifier = event.findOrderById(orderId).getUserIdentifier();

        // Buyer is offline/logged out while the admin closes the company.
        // Do not register the buyer in Broadcaster, so the refund failure notification should be saved as delayed.
        userService.logout(nonAdminToken);

        CountDownLatch ownerLatch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> ownerNotification = new AtomicReference<>();
        String ownerIdentifier = auth.getUserEmail(adminToken).getValue();

        Registration ownerReg = Broadcaster.registerUser(ownerIdentifier, dto -> {
            ownerNotification.set(dto);
            ownerLatch.countDown();
        });

        try {
            // Act: Admin requests to close the company
            Response<Boolean> response = adminService.closeCompanyByAdmin(adminToken, companyId);

            // Assert: database state and response
            assertTrue(response.getValue());
            assertEquals("Company closed successfully", response.getMessage());
            assertFalse(companyRepo.findById(companyId).isActive());
            assertFalse(eventRepo.findById(eventId).isActive());

            assertEquals(OrderStatus.REFUND_REQUIRED,
                    eventRepo.findById(eventId).findOrderById(orderId).getStatus());

            // Assert: buyer refund-failure notification was saved as delayed
            Member buyer = userRepo.findUserByEmail(buyerIdentifier);
            List<NotifyDTO> delayedNotifications = buyer.getDelayedNotifications();

            assertTrue(delayedNotifications.stream()
                            .anyMatch(n ->
                                    n.getType() == NotifyType.GENERAL_POPUP
                                            && n.getPayload() != null
                                            && n.getPayload().getMessage().contains("Refund failed")
                                            && n.getPayload().getMessage().contains("because of closing the company")
                                            && n.getPayload().getMessage().contains("please contact support")
                            ),
                    "Buyer should have a delayed refund failure notification");

            // Assert: owner notification was still delivered in real time
            try {
                assertTrue(ownerLatch.await(2000, TimeUnit.MILLISECONDS), "Owner notification timeout");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            assertNotNull(ownerNotification.get());
            assertTrue(ownerNotification.get().getPayload().getMessage().contains("Company"));

        } finally {
            ownerReg.remove();
        }
    }

    @Test
    void GivenConcurrentRequests_WhenCloseCompany_ThenActionPerformedOnceAndNotificationsSentOnce() throws Exception {
        // Arrange: Create a completed order using a regular user (nonAdminToken) to cleanly separate buyer from owner
        int orderId = createCompletedOrderThroughPurchaseFlow(nonAdminToken, eventId, 1);

        // Arrange: Mock the external payment system to simulate a successful refund process
        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble())).thenReturn(true);

        // Arrange: Retrieve the correct string identifier for the company owner (Admin in this setup)
        String ownerIdentifier = auth.getUserIdentifier(adminToken).getValue();

        // Arrange: Use an AtomicInteger to safely count exact notification occurrences across background threads
        AtomicInteger notificationCount = new AtomicInteger(0);
        Registration ownerReg = Broadcaster.registerUser(ownerIdentifier, dto -> {
            notificationCount.incrementAndGet();
        });

        // Arrange: Prepare a real multithreading environment with 2 concurrent threads
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Arrange: Define the exact task both threads will try to execute simultaneously
        Callable<Response<Boolean>> closureTask = () -> adminService.closeCompanyByAdmin(adminToken, companyId);

        try {
            // Act: Fire two requests at the exact same time to simulate real-world concurrency
            Future<Response<Boolean>> future1 = executor.submit(closureTask);
            Future<Response<Boolean>> future2 = executor.submit(closureTask);

            // Wait for both threads to finish and collect their responses
            Response<Boolean> response1 = future1.get();
            Response<Boolean> response2 = future2.get();

            // Assert:
            boolean success1 = Boolean.TRUE.equals(response1.getValue());
            boolean success2 = Boolean.TRUE.equals(response2.getValue());

            // Assert: Exactly one thread must succeed (XOR operator). The other must fail cleanly.
            assertTrue(success1 ^ success2, "Exactly one concurrent request should succeed, and the other must fail");

            // Assert: Ensure the company is actually closed in the database
            assertFalse(companyRepo.findById(companyId).isActive(), "Company should be inactive after closure");

            // Wait briefly to allow background notification threads to finish processing
            Thread.sleep(1000);

            // Assert: Verify the notification was sent exactly once despite the concurrent assault
            assertEquals(1, notificationCount.get(), "Notification should be sent exactly once despite concurrent attempts");

        } finally {
            // Cleanup: Shut down the thread pool and remove the listener to prevent memory leaks
            executor.shutdown();
            ownerReg.remove();
        }
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

    private int createCompletedOrderThroughPurchaseFlow(String buyerToken, int eventId, int ticketCount) {
        Response<EnterPurchaseDTO> enterResponse =
                activeOrderService.enterEventPurchase(buyerToken, companyId, eventId,null);

        assertNotNull(enterResponse.getValue(), "enterEventPurchase failed: " + enterResponse.getMessage());

        Response<Integer> selectResponse =
                activeOrderService.userSelectTickets(
                        buyerToken,
                        eventId,
                        new HashMap<>(),
                        Map.of("floor", ticketCount)
                );

        assertNotNull(selectResponse.getValue(), "userSelectTickets failed: " + selectResponse.getMessage());

        int activeOrderId = selectResponse.getValue();

        Mockito.when(paymentSystem.pay(Mockito.anyDouble(), Mockito.any(PaymentDetailsDTO.class)))
                .thenReturn("payment-" + activeOrderId);

        TicketSupplyResultDTO supplyResult = Mockito.mock(TicketSupplyResultDTO.class);
        Mockito.when(supplyResult.isSuccess()).thenReturn(true);

        Mockito.when(ticketSupply.issue(Mockito.any(TicketSupplyRequestDTO.class)))
                .thenReturn(supplyResult);

        Response<CheckoutPriceDTO> checkoutPriceResponse =
                activeOrderService.prepareCheckout(buyerToken, activeOrderId);

        assertNotNull(
                checkoutPriceResponse.getValue(),
                "Checkout price should be prepared before payment: "
                        + checkoutPriceResponse.getMessage()
        );

        PaymentDetailsDTO paymentDetails =
                new PaymentDetailsDTO("1234", "12/30", "123", "111", 1, null);

        Response<Integer> checkoutResponse =
                activeOrderService.checkoutAndPayment(buyerToken, activeOrderId, paymentDetails);

        assertNotNull(checkoutResponse.getValue(), "checkoutAndPayment failed: " + checkoutResponse.getMessage());

        return checkoutResponse.getValue();
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

    @Test
    void GivenAdminAndOrdersExist_WhenGetGlobalOrdersByBuyers_ThenReturnFilteredOrders() {
        // Arrange
        Event event = eventRepo.findById(eventId);
        Order order1 = new Order(
                1,
                "buyer1@bgu.ac.il",
                eventId,
                "Test Event",
                "2026-01-01T20:00",
                "TEL_AVIV",
                List.of(
                        new PurchasedTicketDTO(1, "floor", "STANDING", null, null, 50.0),
                        new PurchasedTicketDTO(2, "floor", "STANDING", null, null, 50.0)
                ),
                List.of(1, 2),
                100.0,
                "pay123"
        );

        Order order2 = new Order(
                2,
                "buyer2@bgu.ac.il",
                eventId,
                "Test Event",
                "2026-01-01T20:00",
                "TEL_AVIV",
                List.of(
                        new PurchasedTicketDTO(3, "floor", "STANDING", null, null, 50.0),
                        new PurchasedTicketDTO(4, "floor", "STANDING", null, null, 50.0)
                ),
                List.of(3, 4),
                100.0,
                "pay456"
        );
        event.getOrders().add(order1);
        event.getOrders().add(order2);
        eventRepo.store(event);
        Response<List<AdminPurchaseHistoryDTO>> response = adminService.getGlobalOrders(
                adminToken,
                List.of("buyer1@bgu.ac.il"),
                null,
                null
        );

        // Assert
        assertNotNull(response.getValue(), "Response value should not be null");
        assertEquals(1, response.getValue().size(), "Should filter and return exactly 1 order");

        AdminPurchaseHistoryDTO fetchedOrder = response.getValue().get(0);
        assertEquals("buyer1@bgu.ac.il", fetchedOrder.getUserIdentifier(), "Should match the requested buyer");

        assertEquals(List.of(1, 2), fetchedOrder.getPurchasedTickets(), "Should return the list of ticket IDs directly");
        assertEquals("Retrieved history orders successfully for filter", response.getMessage());
    }

    @Test
    void GivenAdminAndOrdersExist_WhenGetGlobalOrdersByCompaniesAndEvents_ThenReturnFilteredOrders() {
        // Arrange
        Event event = eventRepo.findById(eventId);
        Order order1 = new Order(
                3,
                "company_buyer@bgu.ac.il",
                eventId,
                "Test Event",
                "2026-01-01T20:00",
                "TEL_AVIV",
                List.of(
                        new PurchasedTicketDTO(
                                5,
                                "floor",
                                "STANDING",
                                null,
                                null,
                                75.0
                        ),
                        new PurchasedTicketDTO(
                                6,
                                "floor",
                                "STANDING",
                                null,
                                null,
                                75.0
                        )
                ),
                List.of(5, 6),
                150.0,
                "pay789"
        );
        event.getOrders().add(order1);
        eventRepo.store(event);

        // Act:
        Response<List<AdminPurchaseHistoryDTO>> response = adminService.getGlobalOrders(
                adminToken,
                null,
                List.of(eventId),
                List.of(companyId)
        );

        // Assert
        assertNotNull(response.getValue(), "Response value should not be null");
        assertFalse(response.getValue().isEmpty(), "Should return orders for the given company/event");
        assertTrue(response.getValue().stream().anyMatch(o -> o.getOrderId() == 3), "Must contain the newly added order");
    }

    @Test
    void GivenNonAdminToken_WhenGetGlobalOrders_ThenUnauthorized() {
        // Act
        Response<List<AdminPurchaseHistoryDTO>> response = adminService.getGlobalOrders(
                nonAdminToken,
                List.of("buyer1@bgu.ac.il"),
                null,
                null
        );

        // Assert
        assertNull(response.getValue(), "Unauthorized user should not receive any data");
        assertTrue(response.getMessage().contains("Unauthorized"), "Should return Unauthorized error");
    }

    @Test
    void GivenLoggedOutAdmin_WhenGetGlobalOrders_ThenUnauthorized() {
        // Arrange
        userService.logout(adminToken);

        // Act
        Response<List<AdminPurchaseHistoryDTO>> response = adminService.getGlobalOrders(                adminToken,
                List.of("buyer1@bgu.ac.il"),
                null,
                null
        );

        // Assert
        assertNull(response.getValue(), "Logged out user should not receive any data");
        assertTrue(response.getMessage().contains("Unauthorized"), "Should return Unauthorized error");
    }
    @Test
    void GivenAdminWithConflictingFilters_WhenGetGlobalOrders_ThenInvalidFilterError() {
        // Act
        Response<List<AdminPurchaseHistoryDTO>> response = adminService.getGlobalOrders(                adminToken,
                List.of("buyer1@bgu.ac.il"),
                null,
                List.of(companyId)
        );

        // Assert
        assertNull(response.getValue(), "Should not return data for conflicting filters");
        assertEquals("Cannot filter both users and companies", response.getMessage());
    }

    @Test
    void GivenAdminAndNoMatchingOrders_WhenGetGlobalOrders_ThenReturnEmptyList() {
        // Act
        Response<List<AdminPurchaseHistoryDTO>> response = adminService.getGlobalOrders(                adminToken,
                List.of("nonexistent@bgu.ac.il"),
                null,
                null
        );

        // Assert
        assertNotNull(response.getValue(), "Should return an empty list, not null");
        assertTrue(response.getValue().isEmpty(), "The list must be empty");
        assertEquals("No history orders found", response.getMessage());
    }
    //Admin closes company while fetching global history
    @Test
    void GivenConcurrentCompanyClosure_WhenGetGlobalOrders_ThenSystemRemainsStable() throws Exception {
        // Arrange
        Event event = eventRepo.findById(eventId);
        event.getOrders().add(
                new Order(
                        999,
                        "race_buyer@bgu.ac.il",
                        eventId,
                        "Test Event",
                        "2026-01-01T20:00",
                        "TEL_AVIV",
                        List.of(
                                new PurchasedTicketDTO(
                                        1,
                                        "floor",
                                        "STANDING",
                                        null,
                                        null,
                                        50.0
                                ),
                                new PurchasedTicketDTO(
                                        2,
                                        "floor",
                                        "STANDING",
                                        null,
                                        null,
                                        50.0
                                )
                        ),
                        List.of(1, 2),
                        100.0,
                        "pay123"
                )
        );
        eventRepo.store(event);

        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble())).thenReturn(true);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> closeFuture = pool.submit(() -> {
            start.await();
            return adminService.closeCompanyByAdmin(adminToken, companyId);
        });

        Future<Response<List<AdminPurchaseHistoryDTO>>> reportFuture = pool.submit(() -> {            start.await();
            return adminService.getGlobalOrders(adminToken, null, null, List.of(companyId));
        });

        // Act
        start.countDown();

        Response<Boolean> closeRes = closeFuture.get();
        Response<List<AdminPurchaseHistoryDTO>> reportRes = reportFuture.get();        pool.shutdown();

        // Assert
        assertTrue(closeRes.getValue(), "Company closure should succeed");
        assertNotNull(reportRes.getValue(), "Global orders fetch should survive the race condition without crashing");

        assertFalse(reportRes.getValue().isEmpty(), "Historical orders should be retrieved regardless of closure status");

        assertFalse(companyRepo.findById(companyId).isActive(), "Company must be closed at the end");
    }
    @Test
    void GivenAdminAndOrdersExist_WhenGetAllPurchasers_ThenReturnUniquePurchasersList() {
        String buyer1 = "unique_buyer@bgu.ac.il";
        String buyer2 = "another_buyer@bgu.ac.il";

        userService.registerUser(null, new UserDTO(buyer1, "B1", "User", PASSWORD, 1, 1, 1990, "City", "050-111-2222"));
        userService.registerUser(null, new UserDTO(buyer2, "B2", "User", PASSWORD, 1, 1, 1990, "City", "050-333-4444"));

        String token1 = userService.login(buyer1, PASSWORD).getValue();
        String token2 = userService.login(buyer2, PASSWORD).getValue();

        createCompletedOrderThroughPurchaseFlow(token1, eventId, 1);
        createCompletedOrderThroughPurchaseFlow(token2, eventId, 1);
        createCompletedOrderThroughPurchaseFlow(token1, eventId, 1);

        // Act
        Response<List<String>> response = adminService.getAllPurchasers(adminToken);

        // Assert
        assertNotNull(response.getValue(), "Response should not be null");
        assertEquals(2, response.getValue().size(), "Should return exactly 2 unique purchasers, ignoring the duplicate");
        assertTrue(response.getValue().contains(buyer1));
        assertTrue(response.getValue().contains(buyer2));
        assertEquals("Retrieved purchasers successfully", response.getMessage());
    }

    @Test
    void GivenAdminAndNoOrders_WhenGetAllPurchasers_ThenReturnEmptyList() {
        // Act
        Response<List<String>> response = adminService.getAllPurchasers(adminToken);
        // Assert
        assertNotNull(response.getValue(), "Response should not be null");
        assertTrue(response.getValue().isEmpty(), "Purchasers list should be empty");
        assertEquals("No purchasers found", response.getMessage());
    }

    @Test
    void GivenNonAdminToken_WhenGetAllPurchasers_ThenUnauthorized() {
        // Act
        Response<List<String>> response = adminService.getAllPurchasers(nonAdminToken);

        // Assert
        assertNull(response.getValue(), "Unauthorized user should not receive data");
        assertTrue(response.getMessage().contains("Unauthorized"), "Should return Unauthorized error");
    }

    @Test
    void GivenLoggedOutAdmin_WhenGetAllPurchasers_ThenUnauthorized() {
        // Arrange
        userService.logout(adminToken);
        // Act
        Response<List<String>> response = adminService.getAllPurchasers(adminToken);

        // Assert
        assertNull(response.getValue(), "Logged out admin should not receive data");
        assertTrue(response.getMessage().contains("Unauthorized"), "Should return Unauthorized error");
    }

    @Test
    void GivenConcurrentCompanyClosure_WhenGetAllPurchasers_ThenSystemRemainsStable() throws Exception {
        // Arrange
        String raceBuyer = "race_purchaser@bgu.ac.il";
        userService.registerUser(null, new UserDTO(raceBuyer, "Race", "Buyer", PASSWORD, 1, 1, 1990, "City", "050-999-9999"));
        String raceToken = userService.login(raceBuyer, PASSWORD).getValue();

        createCompletedOrderThroughPurchaseFlow(raceToken, eventId, 1);

        Mockito.when(paymentSystem.refund(Mockito.anyString(), Mockito.anyDouble())).thenReturn(true);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Boolean>> closeFuture = pool.submit(() -> {
            start.await();
            return adminService.closeCompanyByAdmin(adminToken, companyId);
        });
        Future<Response<List<String>>> fetchFuture = pool.submit(() -> {
            start.await();
            return adminService.getAllPurchasers(adminToken);
        });

        // Act
        start.countDown();
        Response<Boolean> closeRes = closeFuture.get();
        Response<List<String>> fetchRes = fetchFuture.get();
        pool.shutdown();

        // Assert
        assertTrue(closeRes.getValue(), "Company closure should succeed");
        assertNotNull(fetchRes.getValue(), "Fetch should survive the race condition without crashing");
        assertTrue(fetchRes.getValue().contains(raceBuyer), "The purchaser must be in the list regardless of closure");
    }

    @Test
    void GivenSystemAdminAndExistingUser_WhenSuspendUserPermanently_ThenUserSuspendedSuccessfully() {

        // Act
        Response<Boolean> response = adminService.SuspendUser(adminToken, userIdNotSuspened);

        // Assert
        assertTrue(response.getValue());
        assertTrue(response.getMessage().contains("Suspension succeeded"));

        assertTrue(suspensionRepo.haveActiveSuspension(userIdNotSuspened));
        assertTrue(userRepo.findById(userIdNotSuspened).isSuspended());
    }

    @Test
    void GivenLoggedOutAdmin_WhenSuspendUserPermanently_ThenUserIsNotAdminErrorReturned() {
        // Arrange
        userService.logout(adminToken);

        // Act
        Response<Boolean> response = adminService.SuspendUser(adminToken, userIdNotSuspened);

        // Assert
        assertFalse(response.getValue());
        assertEquals("SuspendUser failed : user is not admin", response.getMessage());

        Member member = userRepo.findById(userIdNotSuspened);
        assertFalse(member.isSuspended());

        assertFalse(suspensionRepo.haveActiveSuspension(userIdNotSuspened));
    }

    @Test
    void GivenNonAdminUser_WhenSuspendUserPermanently_ThenPermissionDeniedErrorReturned() {

        // Act
        Response<Boolean> response = adminService.SuspendUser(nonAdminToken, userIdNotSuspened);

        // Assert
        assertFalse(response.getValue());
        assertEquals("SuspendUser failed : user is not admin", response.getMessage());

        Member member = userRepo.findById(userIdNotSuspened);
        assertFalse(member.isSuspended());

        assertFalse(suspensionRepo.haveActiveSuspension(userIdNotSuspened));
    }

    @Test
    void GivenNonExistingUser_WhenSuspendUserPermanently_ThenUserNotFoundErrorReturned() {
        // Arrange
        int nonExistingUserId = -1;

        // Act
        Response<Boolean> response = adminService.SuspendUser(adminToken, nonExistingUserId);

        // Assert
        assertFalse(response.getValue());
        assertEquals("User not found", response.getMessage());

    }

    @Test
    void GivenAlreadySuspendedUser_WhenSuspendUserPermanently_ThenUserAlreadySuspendedErrorReturned() {
        // Arrange

        Response<Boolean> firstResponse = adminService.SuspendUser(adminToken, userIdNotSuspened);

        int suspendedUserId = userIdNotSuspened;
        assertTrue(firstResponse.getValue());

        // Act
        Response<Boolean> secondResponse = adminService.SuspendUser(adminToken, suspendedUserId);

        // Assert
        assertFalse(secondResponse.getValue());
        assertEquals("SuspendUser failed : user is already suspended", secondResponse.getMessage());

        Member member = userRepo.findById(suspendedUserId);
        assertTrue(member.isSuspended());

        assertTrue(suspensionRepo.haveActiveSuspension(suspendedUserId));
    }

    @Test
    void GivenSystemAdminAndExistingUser_WhenSuspendUserTemporarily_ThenUserSuspendedSuccessfully() {
        // Arrange
        int suspensionDuration = 7;

        // Act
        Response<Boolean> response = adminService.SuspendUser(adminToken, userIdNotSuspened, suspensionDuration);

        // Assert
        assertTrue(response.getValue());
        assertTrue(response.getMessage().contains("Suspension succeeded"));

        assertTrue(userRepo.findById(userIdNotSuspened).isSuspended());
        assertTrue(suspensionRepo.haveActiveSuspension(userIdNotSuspened));
    }

    @Test
    void GivenLoggedOutAdmin_WhenSuspendUserTemporarily_ThenUserIsNotAdminErrorReturned() {
        // Arrange
        int suspensionDuration = 7;
        userService.logout(adminToken);

        // Act
        Response<Boolean> response = adminService.SuspendUser(adminToken, userIdNotSuspened, suspensionDuration);

        // Assert
        assertFalse(response.getValue());
        assertEquals("SuspendUser failed : user is not admin", response.getMessage());

        Member member = userRepo.findById(userIdNotSuspened);
        assertFalse(member.isSuspended());

        assertFalse(suspensionRepo.haveActiveSuspension(userIdNotSuspened));
    }

    @Test
    void GivenNonAdminUser_WhenSuspendUserTemporarily_ThenPermissionDeniedErrorReturned() {
        // Arrange
        int suspensionDuration = 7;

        // Act
        Response<Boolean> response = adminService.SuspendUser(nonAdminToken, userIdNotSuspened, suspensionDuration);

        // Assert
        assertFalse(response.getValue());
        assertEquals("SuspendUser failed : user is not admin", response.getMessage());

        Member member = userRepo.findById(userIdNotSuspened);
        assertFalse(member.isSuspended());

        assertFalse(suspensionRepo.haveActiveSuspension(userIdNotSuspened));
    }

    @Test
    void GivenNonExistingUser_WhenSuspendUserTemporarily_ThenUserNotFoundErrorReturned() {
        // Arrange
        int nonExistingUserId = -1;
        int suspensionDuration = 7;

        // Act
        Response<Boolean> response = adminService.SuspendUser(adminToken, nonExistingUserId, suspensionDuration);

        // Assert
        assertFalse(response.getValue());
        assertEquals("User not found", response.getMessage());
    }

    @Test
    void GivenAlreadySuspendedUser_WhenSuspendUserTemporarily_ThenUserAlreadySuspendedErrorReturned() {
        // Arrange
        int suspensionDuration = 7;

        Response<Boolean> firstResponse = adminService.SuspendUser(adminToken, userIdNotSuspened, suspensionDuration);

        int suspendedUserId = userIdNotSuspened;
        assertTrue(firstResponse.getValue());

        // Act
        Response<Boolean> secondResponse = adminService.SuspendUser(adminToken, suspendedUserId, suspensionDuration);

        // Assert
        assertFalse(secondResponse.getValue());
        assertEquals("SuspendUser failed : user is already suspended", secondResponse.getMessage());

        Member member = userRepo.findById(suspendedUserId);
        assertTrue(member.isSuspended());

        assertTrue(suspensionRepo.haveActiveSuspension(userIdNotSuspened));
    }

    @Test
    void GivenInvalidEndDate_WhenSuspendUserTemporarily_ThenInvalidSuspensionPeriodErrorReturned() {
        // Arrange
        int invalidSuspensionDuration = -1;

        // Act
        Response<Boolean> response = adminService.SuspendUser(adminToken, userIdNotSuspened, invalidSuspensionDuration);

        // Assert
        assertFalse(response.getValue());
        assertEquals("SuspendUser failed : duration must be greater than 0", response.getMessage());

        Member member = userRepo.findById(userIdNotSuspened);
        assertFalse(member.isSuspended());

        assertFalse(suspensionRepo.haveActiveSuspension(userIdNotSuspened));
    }

    // Race Condition
    @Test
    void GivenAdminSuspendsUserAndUserPerformsWriteActionSimultaneously_ThenSuspendedUserHasNoWriteAccess() throws InterruptedException {
        // Arrange
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(2);
        int suspenedUserId = userIdNotSuspened;
        String suspendedUserToken = userNotSusToken;

        List<Response<Boolean>> responses = Collections.synchronizedList(new ArrayList<>());

        // Thread 1 - Admin suspends the user
        executor.submit(() -> {
            try {
                startGun.await();
                responses.add(adminService.SuspendUser(adminToken, suspenedUserId));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finishLine.countDown();
            }
        });

        // Thread 2 - User attempts to perform a write action
        executor.submit(() -> {
            try {
                startGun.await();
                createCompletedOrderThroughPurchaseFlow(suspendedUserToken,eventId,2);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finishLine.countDown();
            }
        });

        // Act
        startGun.countDown();
        finishLine.await();

        // Assert
        Member member = userRepo.findById(suspenedUserId);

        assertTrue(member.isSuspended(), "User should be suspended");
        assertTrue(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should not have write access");

        executor.shutdown();
    }

    @Test
    void GivenSystemAdminAndTemporarilySuspendedUser_WhenCancelUserSuspension_ThenSuspensionCancelledSuccessfully() {
        // Arrange
        int suspensionDuration = 7;

        Response<Boolean> suspendResponse =
                adminService.SuspendUser(adminToken, userIdNotSuspened, suspensionDuration);

        assertTrue(suspendResponse.getValue());
        assertTrue(userRepo.findById(userIdNotSuspened).isSuspended());
        assertTrue(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should not have write access");

        // Act
        Response<Boolean> response = adminService.UnsuspendUser(adminToken, userIdNotSuspened);

        // Assert
        assertTrue(response.getValue());
        assertTrue(response.getMessage().contains("UnsuspendUser succeeded"));

        Member member = userRepo.findById(userIdNotSuspened);
        assertFalse(member.isSuspended());

        assertFalse(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should have write access");

    }

    @Test
    void GivenSystemAdminAndPermanentlySuspendedUser_WhenCancelUserSuspension_ThenSuspensionCancelledSuccessfully() {
        // Arrange
        Response<Boolean> suspendResponse = adminService.SuspendUser(adminToken, userIdNotSuspened);

        assertTrue(suspendResponse.getValue());
        assertTrue(userRepo.findById(userIdNotSuspened).isSuspended());
        assertTrue(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should not have write access");

        // Act
        Response<Boolean> response = adminService.UnsuspendUser(adminToken, userIdNotSuspened);

        // Assert
        assertTrue(response.getValue());
        assertTrue(response.getMessage().contains("UnsuspendUser succeeded"));

        Member member = userRepo.findById(userIdNotSuspened);
        assertFalse(member.isSuspended());

        assertFalse(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should have write access");

    }

    @Test
    void GivenLoggedOutAdmin_WhenCancelUserSuspension_ThenUserIsNotLoggedInErrorReturned() {
        // Arrange
        Response<Boolean> suspendResponse =
                adminService.SuspendUser(adminToken, userIdNotSuspened);

        assertTrue(suspendResponse.getValue());
        assertTrue(userRepo.findById(userIdNotSuspened).isSuspended());
        assertTrue(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should not have write access");


        userService.logout(adminToken);

        // Act
        Response<Boolean> response = adminService.UnsuspendUser(adminToken, userIdNotSuspened);

        // Assert
        assertFalse(response.getValue());
        assertEquals("UnsuspendUser failed : user is not admin", response.getMessage());

        Member member = userRepo.findById(userIdNotSuspened);
        assertTrue(member.isSuspended());

        assertTrue(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should not have write access");

    }

    @Test
    void GivenNonAdminUser_WhenCancelUserSuspension_ThenPermissionDeniedErrorReturned() {
        // Arrange
        Response<Boolean> suspendResponse =
                adminService.SuspendUser(adminToken, userIdNotSuspened);

        assertTrue(suspendResponse.getValue());
        assertTrue(userRepo.findById(userIdNotSuspened).isSuspended());
        assertTrue(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should not have write access");


        // Act
        Response<Boolean> response = adminService.UnsuspendUser(nonAdminToken, userIdNotSuspened);

        // Assert
        assertFalse(response.getValue());
        assertTrue(response.getMessage().contains("UnsuspendUser failed"));

        Member member = userRepo.findById(userIdNotSuspened);
        assertTrue(member.isSuspended());

        assertTrue(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should not have write access");

    }

    @Test
    void GivenNonExistingUser_WhenCancelUserSuspension_ThenUserNotFoundErrorReturned() {
        // Arrange
        int nonExistingUserId = -1;

        // Act
        Response<Boolean> response = adminService.UnsuspendUser(adminToken, nonExistingUserId);

        // Assert
        assertFalse(response.getValue());
        assertEquals("User not found", response.getMessage());
    }

    @Test
    void GivenActiveUser_WhenCancelUserSuspension_ThenUserIsNotSuspendedErrorReturned() {
        // Arrange
        assertFalse(userRepo.findById(userIdNotSuspened).isSuspended());
        assertFalse(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should have write access");


        // Act
        Response<Boolean> response = adminService.UnsuspendUser(adminToken, userIdNotSuspened);

        // Assert
        assertFalse(response.getValue());
        assertEquals("UnsuspendUser failed : user is not suspended", response.getMessage());

        Member member = userRepo.findById(userIdNotSuspened);
        assertFalse(member.isSuspended());

        assertFalse(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should have write access");

    }

    // Race Condition
    @Test
    void GivenAdminUnsuspendsUserAndUserPerformsWriteActionSimultaneously_ThenUserHasWriteAccess() throws InterruptedException {
        // Arrange
        int suspendedUserId = userIdNotSuspened;
        String suspendedUserToken = userNotSusToken;

        Response<Boolean> suspendResponse = adminService.SuspendUser(adminToken, suspendedUserId);
        assertTrue(suspendResponse.getValue());
        assertTrue(userRepo.findById(suspendedUserId).isSuspended());
        assertTrue(suspensionRepo.haveActiveSuspension(suspendedUserId), "Suspended user should not have write access");


        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(2);

        List<Response<Boolean>> responses = Collections.synchronizedList(new ArrayList<>());
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // Thread 1 - Admin unsuspends the user
        executor.submit(() -> {
            try {
                startGun.await();

                Response<Boolean> response =
                        adminService.UnsuspendUser(adminToken, suspendedUserId);

                responses.add(response);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                finishLine.countDown();
            }
        });

        // Thread 2 - User attempts to perform a write action
        executor.submit(() -> {
            try {
                startGun.await();

                createCompletedOrderThroughPurchaseFlow(suspendedUserToken, eventId, 2);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                finishLine.countDown();
            }
        });

        // Act
        startGun.countDown();
        finishLine.await();

        // Assert
        assertTrue(exceptions.isEmpty(), "No unexpected exception should be thrown during concurrent execution");

        Member member = userRepo.findById(suspendedUserId);

        assertFalse(member.isSuspended(), "User should not be suspended after unsuspend");
        assertFalse(suspensionRepo.haveActiveSuspension(suspendedUserId), "User should not have an active suspension");

        long successfulUnsuspensions = responses.stream()
                .filter(Response::getValue)
                .count();

        assertEquals(1, successfulUnsuspensions, "The unsuspend operation should succeed");

        executor.shutdown();
    }

    @Test
    void GivenSystemAdminAndExistingSuspensions_WhenViewUserSuspensions_ThenSuspensionsReturnedSuccessfully() {
        // Arrange
        Response<Boolean> firstSuspendResponse =
                adminService.SuspendUser(adminToken, userIdNotSuspened);

        assertTrue(firstSuspendResponse.getValue());

        // Act
        Response<List<SuspensionDTO>> response = adminService.getAllUsersSuspensions(adminToken);

        // Assert
        assertNotNull(response);
        assertTrue(response.getValue() != null);
        assertFalse(response.getValue().isEmpty());

        List<SuspensionDTO> suspensions = response.getValue();

        assertTrue(
                suspensions.stream()
                        .anyMatch(suspension -> suspension.getUserId() == userIdNotSuspened),
                "Suspensions list should contain the suspended user"
        );

        assertTrue(response.getMessage().contains("succeeded"));
    }

    @Test
    void GivenSystemAdminAndNoSuspensions_WhenViewUserSuspensions_ThenEmptyListReturned() {
        // Act
        Response<List<SuspensionDTO>> response =
                adminService.getAllUsersSuspensions(adminToken);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getValue());
        assertTrue(response.getValue().isEmpty());

        assertTrue(response.getMessage().contains("succeeded"));
    }

    @Test
    void GivenLoggedOutAdmin_WhenViewUserSuspensions_ThenUserIsNotLoggedInErrorReturned() {
        // Arrange
        adminService.SuspendUser(adminToken, userIdNotSuspened);
        userService.logout(adminToken);

        // Act
        Response<List<SuspensionDTO>> response = adminService.getAllUsersSuspensions(adminToken);

        // Assert
        assertNull(response.getValue());
        assertEquals("getAllUsersSuspensions failed : user is not admin", response.getMessage());
    }

    @Test
    void GivenNonAdminUser_WhenViewUserSuspensions_ThenPermissionDeniedErrorReturned() {
        // Arrange
        adminService.SuspendUser(adminToken, userIdNotSuspened);

        // Act
        Response<List<SuspensionDTO>> response = adminService.getAllUsersSuspensions(nonAdminToken);

        // Assert
        assertNull(response.getValue());
        assertEquals("getAllUsersSuspensions failed : user is not admin", response.getMessage());
    }

    @Test
    void GivenSystemAdminAndTemporarySuspension_WhenViewUserSuspensions_ThenTemporarySuspensionDetailsReturned() {
        // Arrange
        int suspensionDuration = 7;

        Response<Boolean> suspendResponse =
                adminService.SuspendUser(adminToken, userIdNotSuspened, suspensionDuration);

        assertTrue(suspendResponse.getValue());

        // Act
        Response<List<SuspensionDTO>> response = adminService.getAllUsersSuspensions(adminToken);

        // Assert
        assertNotNull(response.getValue());
        assertFalse(response.getValue().isEmpty());

        SuspensionDTO suspension = response.getValue().stream()
                .filter(s -> s.getUserId() == userIdNotSuspened)
                .findFirst()
                .orElseThrow();

        assertEquals(userIdNotSuspened, suspension.getUserId());
        assertNotNull(suspension.getEndDate());
        assertNotNull(suspension.getStartDate());

        assertTrue(suspension.getEndDate().isAfter(LocalDateTime.now()), "Temporary suspension end time should be in the future");
    }
    @Test
    void GivenSystemAdminAndCancelledSuspension_WhenViewUserSuspensions_ThenCancelledSuspensionDetailsReturned() {
        // Arrange
        Response<Boolean> suspendResponse =
                adminService.SuspendUser(adminToken, userIdNotSuspened);

        assertTrue(suspendResponse.getValue());
        assertTrue(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should not have write access");


        Response<Boolean> unsuspendResponse =
                adminService.UnsuspendUser(adminToken, userIdNotSuspened);

        assertTrue(unsuspendResponse.getValue());
        assertFalse(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should have write access");


        // Act
        Response<List<SuspensionDTO>> response = adminService.getAllUsersSuspensions(adminToken);

        // Assert
        assertNotNull(response.getValue());
        assertFalse(response.getValue().isEmpty());

        SuspensionDTO suspension = response.getValue().stream()
                .filter(s -> s.getUserId() == userIdNotSuspened)
                .findFirst()
                .orElseThrow();

        assertEquals(userIdNotSuspened, suspension.getUserId());

        Member member = userRepo.findById(userIdNotSuspened);
        assertFalse(member.isSuspended());

        assertFalse(suspensionRepo.haveActiveSuspension(userIdNotSuspened), "Suspended user should not have write access");

    }

    @Test
    void GivenRealExpiredToken_WhenSetMaxCapacity_ThenTokenExpiredNotificationSent() throws InterruptedException {
        // Arrange: Register a dedicated admin so the user exists in DB
        String email = "expired_admin_hermetic@bgu.ac.il";
        userService.registerUser(null, new UserDTO(
                email, "Expired", "Admin", "pass", 1, 1, 1990, "Israel", "050-111-2233"
        ));

        // Arrange: Use the TokenService to generate a real JWT that has ALREADY EXPIRED.
        TokenService testTokenService = new TokenService();
        String expiredToken = testTokenService.generateExpiredTokenForTest(email);

        // Arrange: Set up the WebSocket listener for this specific token's tab
        CountDownLatch tabLatch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> receivedNotification = new AtomicReference<>();

        Registration tabReg = Broadcaster.registerTab(expiredToken, dto -> {
            receivedNotification.set(dto);
            tabLatch.countDown();
        });

        try {
            // Act: Call an admin function with the expired token
            Response<Boolean> response = adminService.setMaxCapacity(expiredToken, 500);

            // Assert: Must fail gracefully
            assertTrue(response.isError());

            // Assert: Verify the TOKEN_EXPIRED notification was delivered in real time
            assertTrue(tabLatch.await(2000, TimeUnit.MILLISECONDS), "Notification timeout - Tab listener did not catch the event");
            assertNotNull(receivedNotification.get());
            assertEquals(NotifyType.TOKEN_EXPIRED, receivedNotification.get().getType());

        } finally {
            tabReg.remove();
        }
    }

    @Test
    void GivenLoggedOutAdmin_WhenCloseCompanyByAdmin_ThenTokenExpiredNotificationSent() throws InterruptedException {
        // Arrange: Create a dedicated admin for this test
        String email = "logout_close_admin@bgu.ac.il";
        userService.registerUser(null, new UserDTO(
                email, "Logout", "Admin", "pass", 1, 1, 1990, "City", "050-000-0000"
        ));
        String testAdminToken = userService.login(email, "pass").getValue();

        // Act: Log the user out (invalidating the token in Auth)
        userService.logout(testAdminToken);

        // Arrange: Set up the WebSocket listener for this specific token's tab
        CountDownLatch tabLatch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> receivedNotification = new AtomicReference<>();

        Registration tabReg = Broadcaster.registerTab(testAdminToken, dto -> {
            receivedNotification.set(dto);
            tabLatch.countDown();
        });

        try {
            // Act: Attempt to close the company
            Response<Boolean> response = adminService.closeCompanyByAdmin(testAdminToken, companyId);

            // Assert: Unauthorized due to invalid token
            assertTrue(response.isError());

            // Assert: Verify the TOKEN_EXPIRED notification was delivered in real time
            assertTrue(tabLatch.await(2000, TimeUnit.MILLISECONDS), "Notification timeout - Tab listener did not catch the event");
            assertNotNull(receivedNotification.get());
            assertEquals(NotifyType.TOKEN_EXPIRED, receivedNotification.get().getType());

        } finally {
            tabReg.remove();
        }
    }

    @Test
    void GivenLoggedOutAdmin_WhenSuspendUser_ThenTokenExpiredNotificationSent() throws InterruptedException {
        // Arrange: Create a dedicated admin for this test
        String email = "logout_suspend_admin@bgu.ac.il";
        userService.registerUser(null, new UserDTO(
                email, "Logout", "Admin2", "pass", 1, 1, 1990, "City", "050-000-0000"
        ));
        String testAdminToken = userService.login(email, "pass").getValue();

        // Act: Log the user out
        userService.logout(testAdminToken);

        // Arrange: Set up the WebSocket listener for this specific token's tab
        CountDownLatch tabLatch = new CountDownLatch(1);
        AtomicReference<NotifyDTO> receivedNotification = new AtomicReference<>();

        Registration tabReg = Broadcaster.registerTab(testAdminToken, dto -> {
            receivedNotification.set(dto);
            tabLatch.countDown();
        });

        try {
            // Act: Attempt to suspend a user
            Response<Boolean> response = adminService.SuspendUser(testAdminToken, userIdNotSuspened);

            // Assert: Request should fail
            assertTrue(response.isError());

            // Assert: Verify the TOKEN_EXPIRED notification was delivered in real time
            assertTrue(tabLatch.await(2000, TimeUnit.MILLISECONDS), "Notification timeout - Tab listener did not catch the event");
            assertNotNull(receivedNotification.get());
            assertEquals(NotifyType.TOKEN_EXPIRED, receivedNotification.get().getType());

        } finally {
            tabReg.remove();
        }
    }
    @Test
    void GivenAdminAndNoFiltersProvided_WhenGetGlobalOrders_ThenReturnAllOrders() {
        // Arrange
        Event event = eventRepo.findById(eventId);
        event.getOrders().add(new Order(1, "buyer1@bgu.ac.il", eventId, "Test Event", "2026-01-01T20:00", "CENTER", List.of(), List.of(1), 50.0, "pay1"));
        event.getOrders().add(new Order(2, "buyer2@bgu.ac.il", eventId, "Test Event", "2026-01-01T20:00", "CENTER", List.of(), List.of(2), 50.0, "pay2"));
        eventRepo.store(event);

        // Act
        Response<List<AdminPurchaseHistoryDTO>> response = adminService.getGlobalOrders(
                adminToken, null, null, null
        );

        // Assert
        assertNotNull(response.getValue());
        assertEquals(2, response.getValue().size(), "Should return all system orders when no filters are provided");
        assertEquals("Retrieved history orders successfully for filter", response.getMessage());
    }
}
