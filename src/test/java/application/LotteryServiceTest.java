package application;

import DTO.NotifyDTO;
import DTO.NotifyType;
import Log.LoggerSetup;
import com.vaadin.flow.shared.Registration;
import domain.Suspension.ISuspensionRepo;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dto.UserDTO;
import domain.lottery.AccessCodeGenerator;
import domain.lottery.Lottery;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.user.NotificationStatus;
import domain.user.UserNotification;
import infrastructure.Auth;
import infrastructure.Broadcaster;
import infrastructure.PasswordEncoderUtil;
import infrastructure.VaadinNotifier;
import infrastructure.inMemory.*;
import infrastructure.inMemory.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.function.BooleanSupplier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.mockito.Mockito;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LotteryServiceTest {

        private final int companyId = 111;
        private String creatorId1;
        private String creatorId2;

        private TokenService tokenService;
        private EventRepoImpl eventRepo;
        private LotteryRepoImpl lotteryRepo;
        private CompanyRepoImpl companyRepo;
        private LotteryService lotteryService;

        private LocalDateTime saleStartDate_Y;
        private Integer eventId;

        private String validToken;
        private String validToken2;
        private String validToken3;
        private IAuth auth;
        private ISuspensionRepo suspensionRepo;
        private IUserRepo userRepo;
        private IPasswordEncoder passwordEncoder;
        private String invalidToken;
        private String notPermission;
        private EventCompanyManageService eventCompanyManageService;
        private INotifier notifier;
        private UserService userService;
        private TransactionTemplate transactionTemplate;

        @BeforeEach
        void setUp() {
                AccessCodeGenerator.configure(
                        "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                        6
                );
                LoggerSetup.setup();
                userRepo = new UserRepo();
                transactionTemplate = mock(TransactionTemplate.class);

                when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                        TransactionCallback<?> callback = invocation.getArgument(0);
                        return callback.doInTransaction(new org.springframework.transaction.support.SimpleTransactionStatus());
                });
                passwordEncoder = new PasswordEncoderUtil();
                tokenService = new TokenService();
                auth = new Auth(tokenService);
                userService = new UserService(tokenService, auth, userRepo, passwordEncoder, notifier,transactionTemplate);
                suspensionRepo = new SuspensionRepoImpl();

                companyRepo = new CompanyRepoImpl();
                eventRepo = new EventRepoImpl();
                lotteryRepo = new LotteryRepoImpl();

                IPaymentSystem paymentSystem = Mockito.mock(IPaymentSystem.class);
                notifier = new VaadinNotifier();

                userService = new UserService(tokenService, auth, userRepo, passwordEncoder, notifier,transactionTemplate);
                CompanyService companyService = new CompanyService(auth, companyRepo, userRepo, suspensionRepo,
                                notifier,transactionTemplate);
                eventCompanyManageService = new EventCompanyManageService(companyRepo, eventRepo, auth, paymentSystem,
                                suspensionRepo, notifier, userRepo,transactionTemplate);



                lotteryService = new LotteryService(lotteryRepo, eventRepo, auth, companyRepo, suspensionRepo,
                                notifier, userRepo,transactionTemplate);

                // user with permission
                UserDTO user1DTO = new UserDTO(
                                "user1@test.com",
                                "test1",
                                "first",
                                "test1",
                                15,
                                4,
                                2002,
                                "Omer",
                                "050-427-3201");

                userService.registerUser(null, user1DTO);
                validToken = userService.login("user1@test.com", "test1").getValue();
                creatorId1 = validToken;

                // user without permission
                UserDTO user2DTO = new UserDTO(
                                "user2@test.com",
                                "test2",
                                "first",
                                "test2",
                                15,
                                4,
                                2002,
                                "Omer",
                                "050-427-3202");

                userService.registerUser(null, user2DTO);
                notPermission = userService.login("user2@test.com", "test2").getValue();
                creatorId2 = notPermission;

                // logged out / invalid user
                invalidToken = null;

                companyService.createProductionCompany(
                                validToken,
                                companyId,
                                "Test Company",
                                "test@test.com",
                                "0500000000",
                                "bank-1");

                saleStartDate_Y = LocalDateTime.now().plusDays(14);

                Response<Integer> eventResponse = eventCompanyManageService.createEvent(
                                validToken,
                                companyId,
                                LocalDateTime.now().plusDays(30),
                                "Lottery Event",
                                saleStartDate_Y,
                                true,
                                GeographicalArea.CENTER,
                                CategoryEvent.FESTIVAL);

                eventId = eventResponse.getValue();

                // more valid users

                // user with permission
                UserDTO userDTO2 = new UserDTO(
                                "user11@test.com",
                                "test1",
                                "first",
                                "test1",
                                15,
                                4,
                                2002,
                                "Omer",
                                "050-427-3201");

                userService.registerUser(null, userDTO2);
                validToken2 = userService.login("user11@test.com", "test1").getValue();

                UserDTO userDTO3 = new UserDTO(
                                "user12@test.com",
                                "test1",
                                "first",
                                "test1",
                                15,
                                4,
                                2002,
                                "Omer",
                                "050-427-3201");

                userService.registerUser(null, userDTO3);
                validToken3 = userService.login("user12@test.com", "test1").getValue();

        }

        private void waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
                long deadline = System.currentTimeMillis() + timeoutMillis;

                while (System.currentTimeMillis() < deadline) {
                        if (condition.getAsBoolean()) {
                                return;
                        }
                        Thread.sleep(50);
                }

                fail("Condition was not met within " + timeoutMillis + " ms");
        }

        private long countLotteryNotifications(Member member) {
                return member.getPendingNotifications().stream()
                        .filter(n -> n.getType() == NotifyType.GENERAL_POPUP
                                && n.getPayload() != null
                                && n.getPayload().getMessage() != null
                                && n.getPayload().getMessage().contains("Your code is: "))
                        .count();
        }

        private boolean isLoserUserNotification(UserNotification notification) {
                return notification.getType() == NotifyType.GENERAL_POPUP
                        && notification.getPayload() != null
                        && notification.getPayload().getMessage() != null
                        && notification.getPayload().getMessage().contains("not selected");
        }

        private boolean isLoserNotification(NotifyDTO notification) {
                return notification != null
                        && notification.getType() == NotifyType.GENERAL_POPUP
                        && notification.getPayload() != null
                        && notification.getPayload().getMessage() != null
                        && notification.getPayload().getMessage().contains("not selected");
        }

        private boolean isWinnerNotification(NotifyDTO notification) {
                return notification != null
                        && notification.getType() == NotifyType.GENERAL_POPUP
                        && notification.getPayload() != null
                        && notification.getPayload().getMessage() != null
                        && notification.getPayload().getMessage().contains("Your code is: ");
        }

        private boolean isWinnerUserNotification(domain.user.UserNotification notification) {
                return notification.getType() == NotifyType.GENERAL_POPUP
                        && notification.getPayload() != null
                        && notification.getPayload().getMessage() != null
                        && notification.getPayload().getMessage().contains("Your code is: ");
        }



        @AfterEach
        void tearDown() {
                if (lotteryService != null) {
                        lotteryService.shutdown();
                }
        }

        // ==========================================
        // Tests for createLottery Use Case
        // ==========================================

        @Test
        void GivenValidInputs_WhenCreateLottery_ThenLotteryIsSuccessfullyCreated() {
                // Arrange: Date 'X' - Next week (before sales open)
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

                // Act
                Response<Boolean> response = lotteryService.createLottery(
                                validToken, eventId, 50, lotteryDate_X, (long) 24.0);

                // Assert
                assertTrue(response.getValue());
                assertEquals("Lottery created successfully", response.getMessage());
                assertEquals(1, lotteryRepo.getAll().size(), "Lottery should be saved in the repository");
        }

        @Test
        void GivenInvalidOrMissingToken_WhenCreateLottery_ThenUserNotLoggedInErrorIsReturned() {
                // Arrange
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

                // Act
                Response<Boolean> response = lotteryService.createLottery(
                                invalidToken, eventId, 50, lotteryDate_X, (long) 24.0);

                // Assert
                assertFalse(response.getValue());
                assertEquals("Invalid token", response.getMessage());
        }

        @Test
        void GivenNonExistingEventId_WhenCreateLottery_ThenEventNotFoundErrorIsReturned() {
                // Arrange
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

                // Act
                Response<Boolean> response = lotteryService.createLottery(
                                validToken, -1, 50, lotteryDate_X, (long) 24.0);

                // Assert
                assertFalse(response.getValue());
                assertEquals("event not found", response.getMessage());
        }

        @Test
        void GivenEventNotSupportingLottery_WhenCreateLottery_ThenLotteryNotSupportedErrorIsReturned() {
                // Arrange: Create an event that DOES NOT support lottery
                Integer eventId = eventCompanyManageService.createEvent(validToken, companyId,
                                LocalDateTime.now().plusDays(30), "test-event-no-lottery", saleStartDate_Y, false,
                                GeographicalArea.CENTER, CategoryEvent.FESTIVAL).getValue();
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

                // Act
                Response<Boolean> response = lotteryService.createLottery(
                                validToken, eventId, 50, lotteryDate_X, (long) 24.0);

                // Assert
                assertFalse(response.getValue());
                assertEquals("This event does not support lottery", response.getMessage());
        }

        @Test
        void GivenLotteryDateInThePast_WhenCreateLottery_ThenInvalidDateErrorIsReturned() {
                // Arrange: Date 'Z' - A week ago
                LocalDateTime pastDate_Z = LocalDateTime.now().minusDays(7);

                // Act
                Response<Boolean> response = lotteryService.createLottery(
                                validToken, eventId, 50, pastDate_Z, (long) 24.0);

                // Assert
                assertFalse(response.getValue());
                assertEquals("Register window must be in the future", response.getMessage());
        }

        @Test
        void GivenLotteryDateAfterSalesOpen_WhenCreateLottery_ThenInvalidDateErrorIsReturned() {
                // Arrange: Date 'T' - After sales open date (Y + 5 days)
                LocalDateTime lateDate_T = saleStartDate_Y.plusDays(5);

                // Act
                Response<Boolean> response = lotteryService.createLottery(
                                validToken, eventId, 50, lateDate_T, (long) 24.0);

                // Assert
                assertFalse(response.getValue());
                assertEquals("Register window must be before sale start date", response.getMessage());
        }

        @Test
        void GivenFewerRegistrationsThanCapacity_WhenDrawLotteryIsTriggered_ThenAllRegisteredWin()
                        throws InterruptedException {
                // Arrange: Create lottery with capacity 10
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
                lotteryService.createLottery(validToken, eventId, 10, lotteryDate_X, (long) 24.0);

                Lottery lottery = lotteryRepo.getAll().get(0);
                lotteryService.registerUserToLottery(validToken, eventId);
                lotteryService.registerUserToLottery(notPermission, eventId);
                lotteryService.registerUserToLottery(validToken2, eventId);

                // Arrange: Extract string identifiers for WebSocket listeners
                String id1 = auth.getUserIdentifier(validToken).getValue();
                String id2 = auth.getUserIdentifier(notPermission).getValue();
                String id3 = auth.getUserIdentifier(validToken2).getValue();

                // Arrange: Setup latches and references to catch the notifications
                CountDownLatch latch1 = new CountDownLatch(1);
                CountDownLatch latch2 = new CountDownLatch(1);
                CountDownLatch latch3 = new CountDownLatch(1);

                AtomicReference<NotifyDTO> notif1 = new AtomicReference<>();
                AtomicReference<NotifyDTO> notif2 = new AtomicReference<>();
                AtomicReference<NotifyDTO> notif3 = new AtomicReference<>();

                Registration reg1 = Broadcaster.registerUser(id1, dto -> {
                        notif1.set(dto);
                        latch1.countDown();
                });
                Registration reg2 = Broadcaster.registerUser(id2, dto -> {
                        notif2.set(dto);
                        latch2.countDown();
                });
                Registration reg3 = Broadcaster.registerUser(id3, dto -> {
                        notif3.set(dto);
                        latch3.countDown();
                });

                try {
                        // Act: Manually trigger the draw (simulating the scheduler)
                        lotteryService.drawLottery(lottery.getId());

                        // Assert: Verify DB state
                        Lottery updatedLottery = lotteryRepo.findById(lottery.getId());
                        assertEquals(3, updatedLottery.getWinners().size(), "All 3 registered users should win");

                        // Assert: Verify all users received notifications within timeout
                        assertTrue(latch1.await(2000, TimeUnit.MILLISECONDS), "User 1 did not receive notification");
                        assertTrue(latch2.await(2000, TimeUnit.MILLISECONDS), "User 2 did not receive notification");
                        assertTrue(latch3.await(2000, TimeUnit.MILLISECONDS), "User 3 did not receive notification");

                        // Assert: Verify notification content contains the access code
                        assertTrue(notif1.get().getPayload().getMessage().contains("Your code is: "));
                        assertTrue(notif2.get().getPayload().getMessage().contains("Your code is: "));
                        assertTrue(notif3.get().getPayload().getMessage().contains("Your code is: "));

                        Member user1 = userRepo.findUserByEmail(id1);
                        Member user2 = userRepo.findUserByEmail(id2);
                        Member user3 = userRepo.findUserByEmail(id3);

                        assertTrue(user1.getPendingNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")
                                                && n.getStatus() == NotificationStatus.DELIVERED),
                                "Online user 1 should have the lottery winner notification saved and marked as DELIVERED");

                        assertTrue(user2.getPendingNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")
                                                && n.getStatus() == NotificationStatus.DELIVERED),
                                "Online user 2 should have the lottery winner notification saved and marked as DELIVERED");

                        assertTrue(user3.getPendingNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")
                                                && n.getStatus() == NotificationStatus.DELIVERED),
                                "Online user 3 should have the lottery winner notification saved and marked as DELIVERED");
                } finally {
                        // Cleanup
                        reg1.remove();
                        reg2.remove();
                        reg3.remove();
                }
        }

        @Test
        void GivenFewerRegistrationsThanCapacityAndUsersOffline_WhenDrawLotteryIsTriggered_ThenAllRegisteredWinAndNotificationsSavedAsDelayed() {
                // Arrange: Create lottery with capacity 10
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
                lotteryService.createLottery(validToken, eventId, 10, lotteryDate_X, (long) 24.0);

                Lottery lottery = lotteryRepo.getAll().get(0);

                lotteryService.registerUserToLottery(validToken, eventId);
                lotteryService.registerUserToLottery(notPermission, eventId);
                lotteryService.registerUserToLottery(validToken2, eventId);

                // Extract identifiers before logout, because tokens may become invalid after
                // logout
                String id1 = auth.getUserIdentifier(validToken).getValue();
                String id2 = auth.getUserIdentifier(notPermission).getValue();
                String id3 = auth.getUserIdentifier(validToken2).getValue();

                // Users registered to the lottery and then logged out before the draw.
                // They are not registered in Broadcaster, so winner notifications should be
                // saved as delayed.
                userService.logout(validToken);
                userService.logout(notPermission);
                userService.logout(validToken2);

                // Act: Manually trigger the draw
                lotteryService.drawLottery(lottery.getId());

                // Assert: Verify DB state
                Lottery updatedLottery = lotteryRepo.findById(lottery.getId());
                assertEquals(3, updatedLottery.getWinners().size(),
                                "All 3 registered users should win");

                Member user1 = userRepo.findUserByEmail(id1);
                Member user2 = userRepo.findUserByEmail(id2);
                Member user3 = userRepo.findUserByEmail(id3);

                assertTrue(user1.getPendingNotifications().stream()
                                .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "User 1 should have a delayed lottery winner notification");

                assertTrue(user2.getPendingNotifications().stream()
                                .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "User 2 should have a delayed lottery winner notification");

                assertTrue(user3.getPendingNotifications().stream()
                                .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "User 3 should have a delayed lottery winner notification");
        }

        @Test
        void GivenOneOnlineUserAndOneOfflineUser_WhenLotteryWithCapacityOneIsDrawn_ThenOnlyWinnerReceivesCodeThroughCorrectChannel()
                        throws InterruptedException {
                // Arrange
                LocalDateTime lotteryDate = LocalDateTime.now().plusDays(7);
                lotteryService.createLottery(validToken, eventId, 1, lotteryDate, 24L);

                Lottery lottery = lotteryRepo.getAll().get(0);

                lotteryService.registerUserToLottery(validToken, eventId);
                lotteryService.registerUserToLottery(validToken2, eventId);

                String onlineUserId = auth.getUserIdentifier(validToken).getValue();
                String offlineUserId = auth.getUserIdentifier(validToken2).getValue();

                CountDownLatch onlineLatch = new CountDownLatch(1);
                AtomicReference<NotifyDTO> onlineNotification = new AtomicReference<>();

                Registration onlineRegistration = Broadcaster.registerUser(onlineUserId, dto -> {
                        onlineNotification.set(dto);
                        onlineLatch.countDown();
                });

                userService.logout(validToken2);

                try {
                        // Act
                        lotteryService.drawLottery(lottery.getId());

                        // Assert
                        Lottery updatedLottery = lotteryRepo.findById(lottery.getId());
                        assertEquals(1, updatedLottery.getWinners().size(),
                                        "Exactly one user should win the lottery");

                        Member onlineUser = userRepo.findUserByEmail(onlineUserId);
                        Member offlineUser = userRepo.findUserByEmail(offlineUserId);

                        int onlineUserNumericId = userRepo.findUserByEmail(onlineUserId).getUserId();
                        int offlineUserNumericId = userRepo.findUserByEmail(offlineUserId).getUserId();

                        boolean onlineUserWon = updatedLottery.getWinners().contains(onlineUserNumericId);
                        boolean offlineUserWon = updatedLottery.getWinners().contains(offlineUserNumericId);

                        assertTrue(onlineUserWon || offlineUserWon,
                                        "The winner should be one of the registered users");

                        if (onlineUserWon) {
                                assertTrue(onlineLatch.await(2000, TimeUnit.MILLISECONDS),
                                                "Online winner should receive a live notification");

                                assertNotNull(onlineNotification.get(),
                                                "Online winner notification should exist");

                                assertTrue(onlineNotification.get().getPayload().getMessage()
                                                .contains("Your code is: "),
                                                "Online winner notification should contain lottery code");

                                assertTrue(onlineUser.getPendingNotifications().stream()
                                                .anyMatch(n -> n.getPayload() != null
                                                        && n.getPayload().getMessage()
                                                        .contains("Your code is: ")
                                                        && n.getStatus() == NotificationStatus.DELIVERED),
                                        "Online winner should have the code saved and marked as DELIVERED");

                                assertFalse(offlineUser.getPendingNotifications().stream()
                                                .anyMatch(n -> n.getPayload() != null
                                                                && n.getPayload().getMessage()
                                                                                .contains("Your code is: ")),
                                                "Offline non-winner should not receive a delayed code");
                                assertTrue(offlineUser.getPendingNotifications().stream()
                                                .anyMatch(n -> isLoserUserNotification(n)
                                                        && n.getStatus() == NotificationStatus.PENDING),
                                        "Offline loser should have a PENDING loser notification");
                        }

                        if (offlineUserWon) {
                                Thread.sleep(1000);

                                assertTrue(offlineUser.getPendingNotifications().stream()
                                                .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                                && n.getPayload() != null
                                                                && n.getPayload().getMessage()
                                                                                .contains("Your code is: ")
                                        && n.getStatus() == NotificationStatus.PENDING),
                                "Offline winner should have a PENDING lottery code notification");
                                assertTrue(onlineLatch.await(2000, TimeUnit.MILLISECONDS),
                                        "Online loser should receive a live loser notification");

                                assertNotNull(onlineNotification.get());

                                assertTrue(isLoserNotification(onlineNotification.get()),
                                        "Online non-winner should receive a loser notification");

                                assertFalse(isWinnerNotification(onlineNotification.get()),
                                        "Online non-winner should not receive a lottery code notification");

                                assertTrue(onlineUser.getPendingNotifications().stream()
                                                .anyMatch(n -> isLoserUserNotification(n)
                                                        && n.getStatus() == NotificationStatus.DELIVERED),
                                        "Online loser notification should be saved and marked as DELIVERED");
                        }
                } finally {
                        onlineRegistration.remove();
                }
        }

        @Test
        void GivenMoreRegistrationsThanCapacity_WhenDrawLotteryIsTriggered_ThenWinnersAreSelectedUpToCapacityAndNotificationsMatchLottery()
                        throws InterruptedException {
                // Arrange: Create a lottery with a capacity of 2
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
                lotteryService.createLottery(validToken, eventId, 2, lotteryDate_X, 24L);

                lotteryService.registerUserToLottery(validToken, eventId);
                lotteryService.registerUserToLottery(notPermission, eventId);
                lotteryService.registerUserToLottery(validToken2, eventId);
                lotteryService.registerUserToLottery(validToken3, eventId);

                Lottery lottery = lotteryRepo.getAll().get(0);

                String id1 = auth.getUserIdentifier(validToken).getValue();
                String id2 = auth.getUserIdentifier(notPermission).getValue();
                String id3 = auth.getUserIdentifier(validToken2).getValue();
                String id4 = auth.getUserIdentifier(validToken3).getValue();

                CountDownLatch notificationLatch = new CountDownLatch(4);
                AtomicInteger notificationCount = new AtomicInteger(0);
                List<NotifyDTO> receivedNotifications = new CopyOnWriteArrayList<>();

                Registration reg1 = Broadcaster.registerUser(id1, dto -> {
                        receivedNotifications.add(dto);
                        notificationCount.incrementAndGet();
                        notificationLatch.countDown();
                });
                Registration reg2 = Broadcaster.registerUser(id2, dto -> {
                        receivedNotifications.add(dto);
                        notificationCount.incrementAndGet();
                        notificationLatch.countDown();
                });
                Registration reg3 = Broadcaster.registerUser(id3, dto -> {
                        receivedNotifications.add(dto);
                        notificationCount.incrementAndGet();
                        notificationLatch.countDown();
                });
                Registration reg4 = Broadcaster.registerUser(id4, dto -> {
                        receivedNotifications.add(dto);
                        notificationCount.incrementAndGet();
                        notificationLatch.countDown();
                });

                try {
                        // Act
                        lotteryService.drawLottery(lottery.getId());

                        // Assert: Verify DB state
                        Lottery updatedLottery = lotteryRepo.findById(lottery.getId());

                        assertEquals(2, updatedLottery.getWinners().size(),
                                        "Should only have exactly 2 winners");


                        assertTrue(notificationLatch.await(3, TimeUnit.SECONDS),
                                        "Expected winner notifications were not received in time");

                        assertTrue(notificationCount.get() >= 2,
                                        "At least the 2 winners should receive live notifications");

                        long winnerLiveNotifications = receivedNotifications.stream()
                                .filter(this::isWinnerNotification)
                                .count();

                        long loserLiveNotifications = receivedNotifications.stream()
                                .filter(this::isLoserNotification)
                                .count();

                        assertEquals(2, winnerLiveNotifications,
                                "Exactly 2 users should receive winner code notifications");

                        assertEquals(2, loserLiveNotifications,
                                "Exactly 2 users should receive loser notifications");

                        List<Member> users = List.of(
                                        userRepo.findUserByEmail(id1),
                                        userRepo.findUserByEmail(id2),
                                        userRepo.findUserByEmail(id3),
                                        userRepo.findUserByEmail(id4));

                        for (Member user : users) {
                                boolean isWinner = updatedLottery.getWinners().contains(user.getUserId());

                                if (isWinner) {
                                        assertTrue(user.getPendingNotifications().stream()
                                                        .anyMatch(n -> isWinnerUserNotification(n)
                                                                && n.getStatus() == NotificationStatus.DELIVERED),
                                                "Online winner should have winner notification marked as DELIVERED");

                                        assertEquals(0, user.getPendingNotifications().stream()
                                                        .filter(this::isLoserUserNotification)
                                                        .count(),
                                                "Winner should not have loser notification");

                                } else {
                                        assertTrue(user.getPendingNotifications().stream()
                                                        .anyMatch(n -> isLoserUserNotification(n)
                                                                && n.getStatus() == NotificationStatus.DELIVERED),
                                                "Online loser should have loser notification marked as DELIVERED");

                                        assertEquals(0, user.getPendingNotifications().stream()
                                                        .filter(this::isWinnerUserNotification)
                                                        .count(),
                                                "Loser should not have winner code notification");
                                }
                        }
                } finally {
                        reg1.remove();
                        reg2.remove();
                        reg3.remove();
                        reg4.remove();
                }
        }

        @Test
        void GivenNonExistentLotteryId_WhenDrawLotteryIsTriggered_ThenItFailsGracefullyWithoutCrashing() {
                // Act & Assert
                // We call the service with a fake ID. If it crashes the test fails.
                // It should just log the error internally as per our implementation.
                assertDoesNotThrow(() -> lotteryService.drawLottery(-1),
                                "The service should catch the exception and log it, not crash the system.");
        }

        @Test
        void GivenLoggedInMemberAndLotteryEvent_WhenRegisterUserToLottery_ThenUserRegistered() {
                // Arrange
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

                lotteryService.createLottery(validToken, eventId, 5, lotteryDate_X, 24L);

                int userId = userService.getUserId(validToken).getValue();

                // Act
                Response<Boolean> response = lotteryService.registerUserToLottery(validToken, eventId);

                // Assert
                assertTrue(response.getValue());
                assertEquals("User registered to lottery successfully", response.getMessage());

                Lottery lottery = lotteryRepo.findById(eventId);
                assertTrue(lottery.getRegistered().contains(userId));
        }

        @Test
        void GivenNonExistingEvent_WhenRegisterUserToLottery_ThenEventNotFound() {
                // Arrange
                int nonExistingEventId = -1;

                // Act
                Response<Boolean> response = lotteryService.registerUserToLottery(validToken, nonExistingEventId);

                // Assert
                assertFalse(response.getValue());
                assertEquals("Event not found", response.getMessage());
        }

        @Test
        void GivenRegularEvent_WhenRegisterUserToLottery_ThenLotteryNotSupported() {
                // Arrange
                Integer noLotteryEventId = eventCompanyManageService.createEvent(
                                validToken,
                                companyId,
                                LocalDateTime.now().plusDays(30),
                                "No Lottery Event",
                                saleStartDate_Y,
                                false,
                                GeographicalArea.CENTER,
                                CategoryEvent.FESTIVAL).getValue();

                // Act
                Response<Boolean> response = lotteryService.registerUserToLottery(validToken, noLotteryEventId);

                // Assert
                assertFalse(response.getValue());
                assertEquals("This event does not support lottery", response.getMessage());
        }

        @Test
        void GivenLoggedOutUser_WhenRegisterUserToLottery_ThenErrorReturned() {
                // Arrange
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
                UserDTO user = new UserDTO("test@test.com", "first", "last", "pass", 1, 1, 2000, "city",
                                "050-123-4567");
                userService.registerUser(null, user);
                String token = userService.login("test@test.com", "pass").getValue();
                lotteryService.createLottery(validToken, eventId, 5, lotteryDate_X, 24L);
                userService.logout(token);
                // Act
                Response<Boolean> response = lotteryService.registerUserToLottery(token, eventId);

                // Assert
                assertFalse(response.getValue());
                assertEquals("Invalid token", response.getMessage());
        }

        @Test
        void GivenAlreadyRegisteredMember_WhenRegisterUserToLottery_ThenDuplicateRegistrationError() {
                // Arrange
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
                lotteryService.createLottery(validToken, eventId, 5, lotteryDate_X, 24L);

                // first registration
                Response<Boolean> first = lotteryService.registerUserToLottery(validToken, eventId);

                assertTrue(first.getValue());

                // Act – second registration trial
                Response<Boolean> second = lotteryService.registerUserToLottery(validToken, eventId);

                // Assert
                assertFalse(second.getValue());
                assertEquals("User is already registered to this lottery", second.getMessage());
        }

        @Test
        void GivenExpiredLotteryRegistration_WhenRegisterUserToLottery_ThenRegistrationExpiredError() {
                // Arrange
                LocalDateTime pastWindow = LocalDateTime.now().minusDays(1);

                Lottery expiredLottery = new Lottery(eventId, 5, pastWindow, 24L);
                lotteryRepo.store(expiredLottery);

                // Act
                Response<Boolean> response = lotteryService.registerUserToLottery(validToken, eventId);

                // Assert
                assertFalse(response.getValue());
                assertEquals("Lottery registration period has expired", response.getMessage());
        }

        @Test
        void GivenEventWithLotteryPolicyButNoLottery_WhenRegisterUserToLottery_ThenLotteryNotFound() {
                // Arrange

                // Act
                Response<Boolean> response = lotteryService.registerUserToLottery(validToken, eventId);

                // Assert
                assertFalse(response.getValue());
                assertEquals("Lottery not found", response.getMessage());
        }

        @Test
        void GivenTenDifferentUsersRegisterConcurrently_WhenRegisterUserToLottery_ThenAllUsersRegistered()
                        throws Exception {
                // Arrange
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);

                Response<Boolean> createLotteryResponse = lotteryService.createLottery(validToken, eventId, 3,
                                lotteryDate_X, 24L);

                assertTrue(Boolean.TRUE.equals(createLotteryResponse.getValue()),
                                "Lottery creation failed: " + createLotteryResponse.getMessage());

                int usersCount = 10;

                UserService userService = new UserService(tokenService, auth, userRepo, passwordEncoder, notifier,transactionTemplate);
                List<String> tokens = new ArrayList<>();

                for (int i = 0; i < usersCount; i++) {
                        String email = "lottery_concurrent_user_" + i + "@test.com";

                        Response<Boolean> registerResponse = userService.registerUser(null, new UserDTO(
                                        email,
                                        "first" + i,
                                        "last" + i,
                                        "pass",
                                        1,
                                        1,
                                        2000,
                                        "city",
                                        "050-123-45" + String.format("%02d", i)));

                        assertTrue(Boolean.TRUE.equals(registerResponse.getValue()),
                                        "User registration failed: " + registerResponse.getMessage());

                        String token = userService.login(email, "pass").getValue();

                        assertNotNull(token, "Login failed for user " + i);

                        tokens.add(token);
                }

                ExecutorService executor = Executors.newFixedThreadPool(usersCount);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Response<Boolean>>> futures = new ArrayList<>();

                // Act
                for (String token : tokens) {
                        futures.add(executor.submit(() -> {
                                start.await();
                                return lotteryService.registerUserToLottery(token, eventId);
                        }));
                }

                start.countDown();

                int successfulRegistrations = 0;

                for (Future<Response<Boolean>> future : futures) {
                        Response<Boolean> response = future.get();

                        if (Boolean.TRUE.equals(response.getValue())) {
                                successfulRegistrations++;
                        } else {
                                fail("Registration failed unexpectedly: " + response.getMessage());
                        }
                }

                executor.shutdown();

                // Assert
                Lottery lottery = lotteryRepo.findById(eventId);

                assertEquals(usersCount, successfulRegistrations);
                assertEquals(usersCount, lottery.getRegistered().size());
        }

        @Test
        void GivenRealExpiredToken_WhenCreateLottery_ThenTokenExpiredNotificationSent() throws InterruptedException {
                // Arrange
                String email = "expired_create_lottery@test.com";
                userService.registerUser(null, new UserDTO(
                                email, "Expired", "User", "pass", 1, 1, 1990, "Israel", "050-111-2233"));

                // Arrange: Generate a real token that has ALREADY EXPIRED
                TokenService testTokenService = new TokenService();
                String expiredToken = testTokenService.generateExpiredTokenForTest(email);

                CountDownLatch tabLatch = new CountDownLatch(1);
                AtomicReference<NotifyDTO> receivedNotification = new AtomicReference<>();

                Registration tabReg = Broadcaster.registerTab(expiredToken, dto -> {
                        receivedNotification.set(dto);
                        tabLatch.countDown();
                });

                try {
                        // Act
                        Response<Boolean> response = lotteryService.createLottery(
                                        expiredToken, eventId, 50, LocalDateTime.now().plusDays(7), 24L);

                        assertFalse(response.getValue());
                        assertEquals("Invalid token", response.getMessage());

                        // Assert: Verify the TOKEN_EXPIRED notification was delivered in real time
                        assertTrue(tabLatch.await(2000, TimeUnit.MILLISECONDS),
                                        "Notification timeout - Tab listener did not catch the event");
                        assertNotNull(receivedNotification.get());
                        assertEquals(NotifyType.TOKEN_EXPIRED, receivedNotification.get().getType());

                } finally {
                        tabReg.remove();
                }
        }
        @Test
        void GivenLotteryAlreadyDrawn_WhenDrawLotteryRunsAgain_ThenNoNotificationsAreCreated() {
                LocalDateTime lotteryDate = LocalDateTime.now().plusDays(7);
                lotteryService.createLottery(validToken, eventId, 10, lotteryDate, 24L);

                lotteryService.registerUserToLottery(validToken2, eventId);

                String userIdentifier = auth.getUserIdentifier(validToken2).getValue();
                userService.logout(validToken2);

                Lottery lottery = lotteryRepo.findById(eventId);

                lottery.drawWinners();
                lotteryRepo.store(lottery);

                Member userBefore = userRepo.findUserByEmail(userIdentifier);
                long beforeCount = countLotteryNotifications(userBefore);

                lotteryService.drawLottery(eventId);

                Member userAfter = userRepo.findUserByEmail(userIdentifier);
                long afterCount = countLotteryNotifications(userAfter);

                assertEquals(beforeCount, afterCount,
                        "If lottery already has winners, drawLottery should skip and not create notifications");
        }

        @Test
        void GivenConcurrentDrawCallsAndOnlineUsers_WhenExecuted_ThenDrawnOnceAndOnlyLotteryNotificationsForSameLotteryAreSent()
                        throws Exception {
                // Arrange
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
                lotteryService.createLottery(validToken, eventId, 10, lotteryDate_X, 24L);

                Lottery lottery = lotteryRepo.getAll().get(0);

                lotteryService.registerUserToLottery(validToken, eventId);
                lotteryService.registerUserToLottery(notPermission, eventId);

                String id1 = auth.getUserIdentifier(validToken).getValue();
                String id2 = auth.getUserIdentifier(notPermission).getValue();

                List<NotifyDTO> receivedNotifications = new CopyOnWriteArrayList<>();
                CountDownLatch notificationLatch = new CountDownLatch(2);

                Registration reg1 = Broadcaster.registerUser(id1, dto -> {
                        receivedNotifications.add(dto);
                        notificationLatch.countDown();
                });

                Registration reg2 = Broadcaster.registerUser(id2, dto -> {
                        receivedNotifications.add(dto);
                        notificationLatch.countDown();
                });

                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch startGate = new CountDownLatch(1);

                try {
                        Callable<Void> concurrentTask = () -> {
                                startGate.await();
                                lotteryService.drawLottery(lottery.getId());
                                return null;
                        };

                        Future<?> future1 = executor.submit(concurrentTask);
                        Future<?> future2 = executor.submit(concurrentTask);

                        startGate.countDown();

                        future1.get(5, TimeUnit.SECONDS);
                        future2.get(5, TimeUnit.SECONDS);

                        assertTrue(notificationLatch.await(3, TimeUnit.SECONDS),
                                        "Expected winner notifications were not received in time");

                        Lottery updatedLottery = lotteryRepo.findById(lottery.getId());

                        assertEquals(2, updatedLottery.getWinners().size(),
                                        "Lottery should have exactly 2 winners regardless of concurrent draw calls");

                        assertTrue(receivedNotifications.size() >= 2,
                                        "Each online winner should receive at least one lottery notification");

                        assertTrue(receivedNotifications.stream()
                                        .allMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                        && n.getPayload() != null
                                                        && n.getPayload().getMessage() != null
                                                        && n.getPayload().getMessage().contains("Your code is: ")
                                                        && n.getPayload().getMessage().contains("event " + eventRepo
                                                                        .findById(lottery.getId()).getName())),
                                        "All live notifications should be lottery code notifications for the same lottery");

                        Member user1 = userRepo.findUserByEmail(id1);
                        Member user2 = userRepo.findUserByEmail(id2);

                        assertTrue(user1.getPendingNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                        && n.getPayload().getMessage() != null
                                                        && n.getPayload().getMessage().contains("Your code is: ")
                                                        && n.getPayload().getMessage().contains("event " + eventRepo
                                                                        .findById(lottery.getId()).getName())
                                                        && n.getStatus() == NotificationStatus.DELIVERED),
                                "Online user 1 should have lottery winner notification saved and marked as DELIVERED");

                        assertTrue(user2.getPendingNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                        && n.getPayload().getMessage() != null
                                                        && n.getPayload().getMessage().contains("Your code is: ")
                                                        && n.getPayload().getMessage().contains("event " + eventRepo
                                                                        .findById(lottery.getId()).getName())
                                                && n.getStatus() == NotificationStatus.DELIVERED),
                                "Online user 2 should have lottery winner notification saved and marked as DELIVERED");
                } finally {
                        executor.shutdown();
                        reg1.remove();
                        reg2.remove();
                }
        }

        @Test
        void GivenConcurrentDrawCallsAndOfflineUsers_WhenExecuted_ThenEachWinnerHasExactlyOneDelayedNotification()
                        throws Exception {
                // Arrange
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
                lotteryService.createLottery(validToken, eventId, 10, lotteryDate_X, 24L);

                Lottery lottery = lotteryRepo.getAll().get(0);

                lotteryService.registerUserToLottery(validToken, eventId);
                lotteryService.registerUserToLottery(notPermission, eventId);

                String id1 = auth.getUserIdentifier(validToken).getValue();
                String id2 = auth.getUserIdentifier(notPermission).getValue();

                userService.logout(validToken);
                userService.logout(notPermission);

                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch startGate = new CountDownLatch(1);

                try {
                        Callable<Void> concurrentTask = () -> {
                                startGate.await();
                                lotteryService.drawLottery(lottery.getId());
                                return null;
                        };

                        Future<?> future1 = executor.submit(concurrentTask);
                        Future<?> future2 = executor.submit(concurrentTask);

                        startGate.countDown();

                        future1.get(5, TimeUnit.SECONDS);
                        future2.get(5, TimeUnit.SECONDS);

                        Lottery updatedLottery = lotteryRepo.findById(lottery.getId());

                        assertEquals(2, updatedLottery.getWinners().size(),
                                        "Lottery should have exactly 2 winners regardless of concurrent draw calls");


                        for (Integer winnerId : updatedLottery.getWinners()) {
                                Member winner = userRepo.findById(winnerId);

                                long delayedLotteryNotifications = winner.getPendingNotifications().stream()
                                                .filter(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                                && n.getPayload() != null
                                                                && n.getPayload().getMessage() != null
                                                                && n.getPayload().getMessage()
                                                                                .contains("Your code is: ")
                                                                && n.getPayload().getMessage().contains("event "
                                                                                + eventRepo.findById(lottery.getId())
                                                                                                .getName())
                                                                && n.getStatus() == NotificationStatus.PENDING)
                                                .count();

                                assertEquals(1, delayedLotteryNotifications,
                                        "Each offline winner should have exactly one PENDING lottery notification");                        }

                        Member user1 = userRepo.findUserByEmail(id1);
                        Member user2 = userRepo.findUserByEmail(id2);

                        long totalDelayedLotteryNotifications = user1.getPendingNotifications().stream()
                                        .filter(n -> n.getPayload() != null
                                                        && n.getPayload().getMessage() != null
                                                        && n.getPayload().getMessage().contains("Your code is: ")
                                                        && n.getPayload().getMessage().contains("event " + eventRepo
                                                                        .findById(lottery.getId()).getName())
                                                        && n.getStatus() == NotificationStatus.PENDING)
                                        .count()
                                        +
                                        user2.getPendingNotifications().stream()
                                                        .filter(n -> n.getPayload() != null
                                                                        && n.getPayload().getMessage() != null
                                                                        && n.getPayload().getMessage()
                                                                                        .contains("Your code is: ")
                                                                        && n.getPayload().getMessage().contains(
                                                                                        "event " + eventRepo.findById(
                                                                                                        lottery.getId())
                                                                                                        .getName())
                                                                        && n.getStatus() == NotificationStatus.PENDING)
                                                        .count();

                        assertEquals(2, totalDelayedLotteryNotifications,
                                "There should be exactly one PENDING lottery notification per winner");
                } finally {
                        executor.shutdown();
                }
        }

        @Test
        void GivenPendingLotteryWithPastRegisterWindow_WhenRescheduleOnStartup_ThenLotteryIsDrawn()
                throws InterruptedException {
                // Arrange
                int userId = userService.getUserId(validToken2).getValue();

                Lottery lottery = new Lottery(
                        eventId,
                        1,
                        LocalDateTime.now().minusSeconds(1),
                        24L
                );

                lottery.registerUserToLottery(userId);
                lotteryRepo.store(lottery);

                // Act
                lotteryService.reschedulePendingLotteriesOnStartup();

                // Assert
                waitUntil(() -> !lotteryRepo.findById(eventId).getWinners().isEmpty(), 2000);

                Lottery updatedLottery = lotteryRepo.findById(eventId);

                assertEquals(1, updatedLottery.getWinners().size(),
                        "Pending lottery should be drawn after startup reschedule");
        }

        @Test
        void GivenDrawnLottery_WhenRescheduleOnStartup_ThenLotteryIsNotProcessedAgain()
                throws InterruptedException {
                int userId = userService.getUserId(validToken2).getValue();
                String userIdentifier = auth.getUserIdentifier(validToken2).getValue();

                userService.logout(validToken2);

                Lottery lottery = new Lottery(
                        eventId,
                        1,
                        LocalDateTime.now().minusSeconds(1),
                        24L
                );

                lottery.registerUserToLottery(userId);
                lottery.drawWinners();
                lotteryRepo.store(lottery);

                Member userBefore = userRepo.findUserByEmail(userIdentifier);
                long beforeCount = countLotteryNotifications(userBefore);

                lotteryService.reschedulePendingLotteriesOnStartup();

                Thread.sleep(500);

                Member userAfter = userRepo.findUserByEmail(userIdentifier);
                long afterCount = countLotteryNotifications(userAfter);

                assertEquals(beforeCount, afterCount,
                        "Drawn lottery should not be processed again on startup");
        }


        @Test
        void WhenShutdownScheduler_ThenDoesNotThrow() {
                assertDoesNotThrow(() -> lotteryService.shutdown());
        }
        //TODO::CHECK THIS TEST
        @Test
        void GivenFewerRegistrationsThanCapacityAndUsersOffline_WhenDrawLotteryIsTriggered_WithMockNotifier_ThenAllRegisteredWinAndNotificationsSavedAsDelayed() {
                // Arrange: Create mock notifier for this test
                INotifier notifierMock = Mockito.mock(INotifier.class);

                /*
                 * All users are considered offline:
                 * notifyUser returns false, so winner notifications should be saved as delayed.
                 */
                Mockito.when(notifierMock.notifyUser(
                        Mockito.anyString(),
                        Mockito.any(NotifyDTO.class)
                )).thenReturn(false);

                TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

                when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                        TransactionCallback<?> callback = invocation.getArgument(0);
                        return callback.doInTransaction(null);
                });

                LotteryService lotteryServiceWithMockNotifier =
                        new LotteryService(
                                lotteryRepo,
                                eventRepo,
                                auth,
                                companyRepo,
                                suspensionRepo,
                                notifierMock,
                                userRepo,
                                transactionTemplate
                        );

                try {
                        // Arrange: Create lottery with capacity 10
                        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
                        lotteryServiceWithMockNotifier.createLottery(validToken, eventId, 10, lotteryDate_X, 24L);

                        Lottery lottery = lotteryRepo.getAll().get(0);

                        lotteryServiceWithMockNotifier.registerUserToLottery(validToken, eventId);
                        lotteryServiceWithMockNotifier.registerUserToLottery(notPermission, eventId);
                        lotteryServiceWithMockNotifier.registerUserToLottery(validToken2, eventId);

                        // Extract identifiers before logout, because tokens may become invalid after logout
                        String id1 = auth.getUserIdentifier(validToken).getValue();
                        String id2 = auth.getUserIdentifier(notPermission).getValue();
                        String id3 = auth.getUserIdentifier(validToken2).getValue();

                        // Keep the same business scenario: users registered and then logged out before the draw
                        userService.logout(validToken);
                        userService.logout(notPermission);
                        userService.logout(validToken2);

                        // Act: Manually trigger the draw
                        lotteryServiceWithMockNotifier.drawLottery(lottery.getId());

                        // Assert: Verify DB state
                        Lottery updatedLottery = lotteryRepo.findById(lottery.getId());

                        assertEquals(3, updatedLottery.getWinners().size(),
                                "All 3 registered users should win");


                        Member user1 = userRepo.findUserByEmail(id1);
                        Member user2 = userRepo.findUserByEmail(id2);
                        Member user3 = userRepo.findUserByEmail(id3);

                        // Assert: each offline winner has delayed lottery notification
                        assertTrue(user1.getPendingNotifications().stream()
                                        .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "User 1 should have a delayed lottery winner notification");

                        assertTrue(user2.getPendingNotifications().stream()
                                        .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "User 2 should have a delayed lottery winner notification");

                        assertTrue(user3.getPendingNotifications().stream()
                                        .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "User 3 should have a delayed lottery winner notification");

                        // Assert: service tried to send real-time notifications through the mock notifier
                        Mockito.verify(notifierMock, Mockito.atLeastOnce()).notifyUser(
                                Mockito.eq(id1),
                                Mockito.any(NotifyDTO.class)
                        );

                        Mockito.verify(notifierMock, Mockito.atLeastOnce()).notifyUser(
                                Mockito.eq(id2),
                                Mockito.any(NotifyDTO.class)
                        );

                        Mockito.verify(notifierMock, Mockito.atLeastOnce()).notifyUser(
                                Mockito.eq(id3),
                                Mockito.any(NotifyDTO.class)
                        );
                        assertEquals(1, user1.getPendingNotifications().stream()
                                .filter(n -> n.getPayload() != null
                                        && n.getPayload().getMessage().contains("Your code is: ")
                                        && n.getStatus() == NotificationStatus.PENDING)
                                .count());

                        assertEquals(1, user2.getPendingNotifications().stream()
                                .filter(n -> n.getPayload() != null
                                        && n.getPayload().getMessage().contains("Your code is: ")
                                        && n.getStatus() == NotificationStatus.PENDING)
                                .count());

                        assertEquals(1, user3.getPendingNotifications().stream()
                                .filter(n -> n.getPayload() != null
                                        && n.getPayload().getMessage().contains("Your code is: ")
                                        && n.getStatus() == NotificationStatus.PENDING)
                                .count());

                } finally {
                        lotteryServiceWithMockNotifier.shutdown();
                }
        }

        @Test
        void GivenFewerRegistrationsThanCapacity_WhenDrawLotteryIsTriggered_WithMockNotifier_ThenAllRegisteredWinAndNotificationsSentRealtime() {
                // Arrange: Create mock notifier for this test
                INotifier notifierMock = Mockito.mock(INotifier.class);

                /*
                 * All users are considered online:
                 * notifyUser returns true, so winner notifications should be delivered in real time
                 * and should NOT be saved as delayed.
                 */
                Mockito.when(notifierMock.notifyUser(
                        Mockito.anyString(),
                        Mockito.any(NotifyDTO.class)
                )).thenReturn(true);

                TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

                when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                        TransactionCallback<?> callback = invocation.getArgument(0);
                        return callback.doInTransaction(null);
                });

                LotteryService lotteryServiceWithMockNotifier =
                        new LotteryService(
                                lotteryRepo,
                                eventRepo,
                                auth,
                                companyRepo,
                                suspensionRepo,
                                notifierMock,
                                userRepo,
                                transactionTemplate
                        );

                try {
                        // Arrange: Create lottery with capacity 10
                        LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
                        lotteryServiceWithMockNotifier.createLottery(validToken, eventId, 10, lotteryDate_X, 24L);

                        Lottery lottery = lotteryRepo.getAll().get(0);

                        lotteryServiceWithMockNotifier.registerUserToLottery(validToken, eventId);
                        lotteryServiceWithMockNotifier.registerUserToLottery(notPermission, eventId);
                        lotteryServiceWithMockNotifier.registerUserToLottery(validToken2, eventId);

                        // Extract identifiers
                        String id1 = auth.getUserIdentifier(validToken).getValue();
                        String id2 = auth.getUserIdentifier(notPermission).getValue();
                        String id3 = auth.getUserIdentifier(validToken2).getValue();

                        // Act: Manually trigger the draw
                        lotteryServiceWithMockNotifier.drawLottery(lottery.getId());

                        // Assert: Verify DB state
                        Lottery updatedLottery = lotteryRepo.findById(lottery.getId());

                        assertEquals(3, updatedLottery.getWinners().size(),
                                "All 3 registered users should win");

                        // Assert: verify realtime notifications were sent through notifierMock
                        org.mockito.ArgumentCaptor<NotifyDTO> user1Captor =
                                org.mockito.ArgumentCaptor.forClass(NotifyDTO.class);

                        Mockito.verify(notifierMock, Mockito.atLeastOnce()).notifyUser(
                                Mockito.eq(id1),
                                user1Captor.capture()
                        );

                        assertTrue(user1Captor.getAllValues().stream()
                                        .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "User 1 should receive lottery winner notification in real time");

                        org.mockito.ArgumentCaptor<NotifyDTO> user2Captor =
                                org.mockito.ArgumentCaptor.forClass(NotifyDTO.class);

                        Mockito.verify(notifierMock, Mockito.atLeastOnce()).notifyUser(
                                Mockito.eq(id2),
                                user2Captor.capture()
                        );

                        assertTrue(user2Captor.getAllValues().stream()
                                        .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "User 2 should receive lottery winner notification in real time");

                        org.mockito.ArgumentCaptor<NotifyDTO> user3Captor =
                                org.mockito.ArgumentCaptor.forClass(NotifyDTO.class);

                        Mockito.verify(notifierMock, Mockito.atLeastOnce()).notifyUser(
                                Mockito.eq(id3),
                                user3Captor.capture()
                        );

                        assertTrue(user3Captor.getAllValues().stream()
                                        .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "User 3 should receive lottery winner notification in real time");

                        // Assert: online users should not have delayed notifications
                        Member user1 = userRepo.findUserByEmail(id1);
                        Member user2 = userRepo.findUserByEmail(id2);
                        Member user3 = userRepo.findUserByEmail(id3);

                        assertTrue(user1.getPendingNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")
                                                && n.getStatus() == NotificationStatus.DELIVERED),
                                "Online user 1 should not have the lottery winner notification saved as delayed");

                        assertTrue(user2.getPendingNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")
                                                && n.getStatus() == NotificationStatus.DELIVERED),
                                "Online user 2 should have the lottery winner notification saved and marked as DELIVERED");

                        assertTrue(user3.getPendingNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")
                                                && n.getStatus() == NotificationStatus.DELIVERED),
                                "Online user 3 should have the lottery winner notification saved and marked as DELIVERED");
                } finally {
                        lotteryServiceWithMockNotifier.shutdown();
                }
        }

        @Test
        void GivenNotifierFailsForLiveDelivery_WhenDrawLottery_ThenNotificationRemainsPending() {
                INotifier notifierMock = Mockito.mock(INotifier.class);

                Mockito.when(notifierMock.notifyUser(
                        Mockito.anyString(),
                        Mockito.any(NotifyDTO.class)
                )).thenReturn(false);

                LotteryService service =
                        new LotteryService(
                                lotteryRepo,
                                eventRepo,
                                auth,
                                companyRepo,
                                suspensionRepo,
                                notifierMock,
                                userRepo,
                                transactionTemplate
                        );

                try {
                        LocalDateTime lotteryDate = LocalDateTime.now().plusDays(7);
                        service.createLottery(validToken, eventId, 1, lotteryDate, 24L);

                        service.registerUserToLottery(validToken2, eventId);

                        String userIdentifier = auth.getUserIdentifier(validToken2).getValue();

                        service.drawLottery(eventId);

                        Lottery lottery = lotteryRepo.findById(eventId);
                        assertEquals(1, lottery.getWinners().size());

                        Member winner = userRepo.findUserByEmail(userIdentifier);

                        assertTrue(winner.getPendingNotifications().stream()
                                .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                        && n.getPayload() != null
                                        && n.getPayload().getMessage().contains("Your code is: ")
                                        && n.getStatus() == NotificationStatus.PENDING));

                        Mockito.verify(notifierMock, Mockito.atLeastOnce())
                                .notifyUser(Mockito.eq(userIdentifier), Mockito.any(NotifyDTO.class));

                } finally {
                        service.shutdown();
                }
        }

        @Test
        void GivenMoreRegistrationsThanCapacityAndUsersOffline_WhenDrawLottery_ThenWinnersGetCodeAndLosersGetPendingLossNotification() {
                LocalDateTime lotteryDate = LocalDateTime.now().plusDays(7);
                lotteryService.createLottery(validToken, eventId, 1, lotteryDate, 24L);

                lotteryService.registerUserToLottery(validToken, eventId);
                lotteryService.registerUserToLottery(validToken2, eventId);
                lotteryService.registerUserToLottery(validToken3, eventId);

                String id1 = auth.getUserIdentifier(validToken).getValue();
                String id2 = auth.getUserIdentifier(validToken2).getValue();
                String id3 = auth.getUserIdentifier(validToken3).getValue();

                userService.logout(validToken);
                userService.logout(validToken2);
                userService.logout(validToken3);

                lotteryService.drawLottery(eventId);

                Lottery updatedLottery = lotteryRepo.findById(eventId);

                assertEquals(1, updatedLottery.getWinners().size());

                List<Member> users = List.of(
                        userRepo.findUserByEmail(id1),
                        userRepo.findUserByEmail(id2),
                        userRepo.findUserByEmail(id3)
                );

                for (Member user : users) {
                        boolean isWinner = updatedLottery.getWinners().contains(user.getUserId());

                        if (isWinner) {
                                assertTrue(user.getPendingNotifications().stream()
                                                .anyMatch(n -> isWinnerUserNotification(n)
                                                        && n.getStatus() == NotificationStatus.PENDING),
                                        "Offline winner should have PENDING winner code notification");

                                assertEquals(0, user.getPendingNotifications().stream()
                                                .filter(this::isLoserUserNotification)
                                                .count(),
                                        "Winner should not receive loser notification");
                        } else {
                                assertTrue(user.getPendingNotifications().stream()
                                                .anyMatch(n -> isLoserUserNotification(n)
                                                        && n.getStatus() == NotificationStatus.PENDING),
                                        "Offline loser should have PENDING loser notification");

                                assertEquals(0, user.getPendingNotifications().stream()
                                                .filter(this::isWinnerUserNotification)
                                                .count(),
                                        "Loser should not receive winner code notification");
                        }
                }
        }

        @Test
        void GivenMoreRegistrationsThanCapacity_WhenDrawLottery_WithMockNotifier_ThenNotifierCalledForWinnersAndLosers() {
                INotifier notifierMock = Mockito.mock(INotifier.class);

                Mockito.when(notifierMock.notifyUser(
                        Mockito.anyString(),
                        Mockito.any(NotifyDTO.class)
                )).thenReturn(true);

                LotteryService service =
                        new LotteryService(
                                lotteryRepo,
                                eventRepo,
                                auth,
                                companyRepo,
                                suspensionRepo,
                                notifierMock,
                                userRepo,
                                transactionTemplate
                        );

                try {
                        LocalDateTime lotteryDate = LocalDateTime.now().plusDays(7);
                        service.createLottery(validToken, eventId, 1, lotteryDate, 24L);

                        service.registerUserToLottery(validToken, eventId);
                        service.registerUserToLottery(validToken2, eventId);
                        service.registerUserToLottery(validToken3, eventId);

                        String id1 = auth.getUserIdentifier(validToken).getValue();
                        String id2 = auth.getUserIdentifier(validToken2).getValue();
                        String id3 = auth.getUserIdentifier(validToken3).getValue();

                        service.drawLottery(eventId);

                        Mockito.verify(notifierMock, Mockito.atLeastOnce())
                                .notifyUser(Mockito.eq(id1), Mockito.any(NotifyDTO.class));

                        Mockito.verify(notifierMock, Mockito.atLeastOnce())
                                .notifyUser(Mockito.eq(id2), Mockito.any(NotifyDTO.class));

                        Mockito.verify(notifierMock, Mockito.atLeastOnce())
                                .notifyUser(Mockito.eq(id3), Mockito.any(NotifyDTO.class));

                        Lottery updatedLottery = lotteryRepo.findById(eventId);
                        assertEquals(1, updatedLottery.getWinners().size());

                        List<Member> users = List.of(
                                userRepo.findUserByEmail(id1),
                                userRepo.findUserByEmail(id2),
                                userRepo.findUserByEmail(id3)
                        );

                        long winnerNotifications = users.stream()
                                .flatMap(u -> u.getPendingNotifications().stream())
                                .filter(this::isWinnerUserNotification)
                                .count();

                        long loserNotifications = users.stream()
                                .flatMap(u -> u.getPendingNotifications().stream())
                                .filter(this::isLoserUserNotification)
                                .count();

                        assertEquals(1, winnerNotifications);
                        assertEquals(2, loserNotifications);

                } finally {
                        service.shutdown();
                }
        }
}