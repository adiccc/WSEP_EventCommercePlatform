package application;

import DTO.NotifyDTO;
import DTO.NotifyType;
import Log.LoggerSetup;
import com.vaadin.flow.shared.Registration;
import domain.Suspension.ISuspensionRepo;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dto.LotteryDTO;
import domain.dto.UserDTO;
import domain.event.Event;
import domain.lottery.Lottery;
import domain.user.IUserRepo;
import domain.user.Member;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import Exception.OptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

        @BeforeEach
        void setUp() {
                LoggerSetup.setup();
                userRepo = new UserRepo();
                passwordEncoder = new PasswordEncoderUtil();
                tokenService = new TokenService();
                auth = new Auth(tokenService);
                userService = new UserService(tokenService, auth, userRepo, passwordEncoder, notifier);
                suspensionRepo = new SuspensionRepoImpl();

                companyRepo = new CompanyRepoImpl();
                eventRepo = new EventRepoImpl();
                lotteryRepo = new LotteryRepoImpl();

                IPaymentSystem paymentSystem = Mockito.mock(IPaymentSystem.class);
                notifier = new VaadinNotifier();

                userService = new UserService(tokenService, auth, userRepo, passwordEncoder, notifier);
                CompanyService companyService = new CompanyService(auth, companyRepo, userRepo, suspensionRepo,
                                notifier);
                eventCompanyManageService = new EventCompanyManageService(companyRepo, eventRepo, auth, paymentSystem,
                                suspensionRepo, notifier, userRepo);

                lotteryService = new LotteryService(lotteryRepo, eventRepo, auth, companyRepo, suspensionRepo,
                                notifier, userRepo);

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

                        assertFalse(user1.getDelayedNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                        && n.getPayload().getMessage().contains("Your code is: ")),
                                        "Online user 1 should not have the lottery winner notification saved as delayed");

                        assertFalse(user2.getDelayedNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                        && n.getPayload().getMessage().contains("Your code is: ")),
                                        "Online user 2 should not have the lottery winner notification saved as delayed");

                        assertFalse(user3.getDelayedNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                        && n.getPayload().getMessage().contains("Your code is: ")),
                                        "Online user 3 should not have the lottery winner notification saved as delayed");
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

                assertTrue(user1.getDelayedNotifications().stream()
                                .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "User 1 should have a delayed lottery winner notification");

                assertTrue(user2.getDelayedNotifications().stream()
                                .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "User 2 should have a delayed lottery winner notification");

                assertTrue(user3.getDelayedNotifications().stream()
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

                                assertFalse(onlineUser.getDelayedNotifications().stream()
                                                .anyMatch(n -> n.getPayload() != null
                                                                && n.getPayload().getMessage()
                                                                                .contains("Your code is: ")),
                                                "Online winner should not have the code saved as delayed");

                                assertFalse(offlineUser.getDelayedNotifications().stream()
                                                .anyMatch(n -> n.getPayload() != null
                                                                && n.getPayload().getMessage()
                                                                                .contains("Your code is: ")),
                                                "Offline non-winner should not receive a delayed code");
                        }

                        if (offlineUserWon) {
                                Thread.sleep(1000);

                                assertTrue(offlineUser.getDelayedNotifications().stream()
                                                .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                                && n.getPayload() != null
                                                                && n.getPayload().getMessage()
                                                                                .contains("Your code is: ")),
                                                "Offline winner should have a delayed lottery code notification");

                                assertNull(onlineNotification.get(),
                                                "Online non-winner should not receive a lottery code notification");

                                assertFalse(onlineUser.getDelayedNotifications().stream()
                                                .anyMatch(n -> n.getPayload() != null
                                                                && n.getPayload().getMessage()
                                                                                .contains("Your code is: ")),
                                                "Online non-winner should not have delayed lottery code");
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

                CountDownLatch notificationLatch = new CountDownLatch(2);
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

                        assertEquals(2, updatedLottery.getNotifiedWinners().size(),
                                        "Both winners should eventually be marked as notified");

                        assertTrue(notificationLatch.await(3, TimeUnit.SECONDS),
                                        "Expected winner notifications were not received in time");

                        assertTrue(notificationCount.get() >= 2,
                                        "At least the 2 winners should receive live notifications");

                        assertTrue(receivedNotifications.stream()
                                        .allMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                        && n.getPayload() != null
                                                        && n.getPayload().getMessage() != null
                                                        && n.getPayload().getMessage().contains("Your code is: ")
                                                        && n.getPayload().getMessage()
                                                                        .contains("event " + lottery.getId())),
                                        "All received notifications should be lottery code notifications for this lottery");

                        List<Member> users = List.of(
                                        userRepo.findUserByEmail(id1),
                                        userRepo.findUserByEmail(id2),
                                        userRepo.findUserByEmail(id3),
                                        userRepo.findUserByEmail(id4));

                        for (Member user : users) {
                                assertFalse(user.getDelayedNotifications().stream()
                                                .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                                && n.getPayload() != null
                                                                && n.getPayload().getMessage() != null
                                                                && n.getPayload().getMessage()
                                                                                .contains("Your code is: ")
                                                                && n.getPayload().getMessage()
                                                                                .contains("event " + lottery.getId())),
                                                "Online users should not have lottery winner notifications saved as delayed");
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

                UserService userService = new UserService(tokenService, auth, userRepo, passwordEncoder, notifier);
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
        void GivenLotteryAlreadyDrawnButWinnersNotNotified_WhenDrawLotteryRunsAgain_ThenMissingNotificationsAreSent() {
                // Arrange
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
                lotteryService.createLottery(validToken, eventId, 10, lotteryDate_X, 24L);

                lotteryService.registerUserToLottery(validToken2, eventId);

                String userIdentifier = auth.getUserIdentifier(validToken2).getValue();

                userService.logout(validToken2);

                Lottery lottery = lotteryRepo.findById(eventId);

                // Simulate a state where winners were already drawn and stored,
                // but notifications were not sent/marked yet.
                lottery.drawWinners();
                lotteryRepo.store(lottery);

                Lottery alreadyDrawnLottery = lotteryRepo.findById(eventId);

                assertEquals(1, alreadyDrawnLottery.getWinners().size(),
                                "Lottery should already have winners before retrying draw");

                assertTrue(alreadyDrawnLottery.getNotifiedWinners().isEmpty(),
                                "Winner should not be marked as notified yet");

                // Act
                lotteryService.drawLottery(eventId);

                // Assert
                Member winner = userRepo.findUserByEmail(userIdentifier);

                assertTrue(winner.getDelayedNotifications().stream()
                                .anyMatch(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: ")),
                                "Missing winner notification should be saved as delayed");

                Lottery updatedLottery = lotteryRepo.findById(eventId);

                assertEquals(1, updatedLottery.getNotifiedWinners().size(),
                                "Winner should be marked as notified after notification is saved");
        }

        @Test
        void GivenOfflineWinnerAlreadyNotified_WhenDrawLotteryRunsAgain_ThenNoDuplicateDelayedNotificationIsCreated() {
                // Arrange
                LocalDateTime lotteryDate_X = LocalDateTime.now().plusDays(7);
                lotteryService.createLottery(validToken, eventId, 10, lotteryDate_X, 24L);

                lotteryService.registerUserToLottery(validToken2, eventId);

                String userIdentifier = auth.getUserIdentifier(validToken2).getValue();

                userService.logout(validToken2);

                // First draw
                lotteryService.drawLottery(eventId);

                Member winnerAfterFirstDraw = userRepo.findUserByEmail(userIdentifier);

                long notificationsAfterFirstDraw = winnerAfterFirstDraw.getDelayedNotifications().stream()
                                .filter(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: "))
                                .count();

                assertEquals(1, notificationsAfterFirstDraw,
                                "Winner should have exactly one delayed lottery notification after first draw");

                // Act: Run draw again
                lotteryService.drawLottery(eventId);

                // Assert
                Member winnerAfterSecondDraw = userRepo.findUserByEmail(userIdentifier);

                long notificationsAfterSecondDraw = winnerAfterSecondDraw.getDelayedNotifications().stream()
                                .filter(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                && n.getPayload() != null
                                                && n.getPayload().getMessage().contains("Your code is: "))
                                .count();

                assertEquals(1, notificationsAfterSecondDraw,
                                "Running draw again should not create duplicate delayed lottery notifications");

                Lottery updatedLottery = lotteryRepo.findById(eventId);

                assertEquals(1, updatedLottery.getNotifiedWinners().size(),
                                "Winner should remain marked as notified");
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

                        future1.get();
                        future2.get();

                        assertTrue(notificationLatch.await(3, TimeUnit.SECONDS),
                                        "Expected winner notifications were not received in time");

                        Lottery updatedLottery = lotteryRepo.findById(lottery.getId());

                        assertEquals(2, updatedLottery.getWinners().size(),
                                        "Lottery should have exactly 2 winners regardless of concurrent draw calls");

                        assertEquals(2, updatedLottery.getNotifiedWinners().size(),
                                        "Both winners should eventually be marked as notified");

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

                        assertFalse(user1.getDelayedNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                        && n.getPayload().getMessage() != null
                                                        && n.getPayload().getMessage().contains("Your code is: ")
                                                        && n.getPayload().getMessage().contains("event " + eventRepo
                                                                        .findById(lottery.getId()).getName())),
                                        "Online user 1 should not have lottery winner notification saved as delayed");

                        assertFalse(user2.getDelayedNotifications().stream()
                                        .anyMatch(n -> n.getPayload() != null
                                                        && n.getPayload().getMessage() != null
                                                        && n.getPayload().getMessage().contains("Your code is: ")
                                                        && n.getPayload().getMessage().contains("event " + eventRepo
                                                                        .findById(lottery.getId()).getName())),
                                        "Online user 2 should not have lottery winner notification saved as delayed");

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

                        future1.get();
                        future2.get();

                        Lottery updatedLottery = lotteryRepo.findById(lottery.getId());

                        assertEquals(2, updatedLottery.getWinners().size(),
                                        "Lottery should have exactly 2 winners regardless of concurrent draw calls");

                        assertEquals(2, updatedLottery.getNotifiedWinners().size(),
                                        "Both winners should be marked as notified");

                        for (Integer winnerId : updatedLottery.getWinners()) {
                                Member winner = userRepo.findById(winnerId);

                                long delayedLotteryNotifications = winner.getDelayedNotifications().stream()
                                                .filter(n -> n.getType() == NotifyType.GENERAL_POPUP
                                                                && n.getPayload() != null
                                                                && n.getPayload().getMessage() != null
                                                                && n.getPayload().getMessage()
                                                                                .contains("Your code is: ")
                                                                && n.getPayload().getMessage().contains("event "
                                                                                + eventRepo.findById(lottery.getId())
                                                                                                .getName()))
                                                .count();

                                assertEquals(1, delayedLotteryNotifications,
                                                "Each offline winner should have exactly one delayed lottery notification");
                        }

                        Member user1 = userRepo.findUserByEmail(id1);
                        Member user2 = userRepo.findUserByEmail(id2);

                        long totalDelayedLotteryNotifications = user1.getDelayedNotifications().stream()
                                        .filter(n -> n.getPayload() != null
                                                        && n.getPayload().getMessage() != null
                                                        && n.getPayload().getMessage().contains("Your code is: ")
                                                        && n.getPayload().getMessage().contains("event " + eventRepo
                                                                        .findById(lottery.getId()).getName()))
                                        .count()
                                        +
                                        user2.getDelayedNotifications().stream()
                                                        .filter(n -> n.getPayload() != null
                                                                        && n.getPayload().getMessage() != null
                                                                        && n.getPayload().getMessage()
                                                                                        .contains("Your code is: ")
                                                                        && n.getPayload().getMessage().contains(
                                                                                        "event " + eventRepo.findById(
                                                                                                        lottery.getId())
                                                                                                        .getName()))
                                                        .count();

                        assertEquals(2, totalDelayedLotteryNotifications,
                                        "There should be exactly one delayed lottery notification per winner");

                } finally {
                        executor.shutdown();
                }
        }
}