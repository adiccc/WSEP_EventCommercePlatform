package UI;

import DTO.DiscountDTO;
import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import application.*;
import domain.event.IEventRepo;
import domain.event.Event;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dataType.PermissionType;
import domain.dto.UserDTO;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import domain.lottery.ILotteryRepo;
import domain.lottery.Lottery;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Runs once at startup and seeds the in-memory repos with demo data:
 *   - 5 registered users
 *   - 10 production companies
 *   - 20 events (2 per company, each with a map so they are active)
 *   - dave is Owner of company 1, eve is Manager of company 1
 *
 * Remove or disable this class before connecting a real database.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = Logger.getLogger(DataSeeder.class.getName());
    private int seededEventCounter = 0;
    private final ILotteryRepo lotteryRepo;
    private final UserService userService;
    private final CompanyService companyService;
    private final EventCompanyManageService eventService;
    private final LotteryService lotteryService;
    private final IAuth auth;
    private final IEventRepo eventRepo;

    public DataSeeder(UserService userService,
                      CompanyService companyService,
                      EventCompanyManageService eventService,
                      LotteryService lotteryService,
                      IAuth auth,
                      ILotteryRepo lotteryRepo,
                      IEventRepo eventRepo) {
        this.userService = userService;
        this.companyService = companyService;
        this.eventService = eventService;
        this.lotteryService = lotteryService;
        this.auth = auth;
        this.lotteryRepo = lotteryRepo;
        this.eventRepo = eventRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== DataSeeder: seeding demo data ===");

        // ── 1. Register users ─────────────────────────────────────────────────
        String guest = guestToken();

        registerUser(
                guest,
                "systemadmin@demo.com",
                "System",
                "Admin",
                "Admin123!",
                "1 Admin St",
                "050-000-0000"
        );
        registerUser(guest, "alice@demo.com",  "Alice",   "Cohen",   "Alice123!", "123 Main St", "050-123-4567");
        registerUser(guest, "bob@demo.com",    "Bob",     "Levi",    "Bob1234!",  "456 Elm Ave", "052-234-5678");
        registerUser(guest, "charlie@demo.com","Charlie", "Mizrahi", "Charlie1!", "789 Oak Rd",  "054-345-6789");
        registerUser(guest, "dave@demo.com",   "Dave",    "Ben-David","Dave123!",  "10 Pine St",  "053-456-7890");
        registerUser(guest, "eve@demo.com",    "Eve",     "Shapiro", "Eve1234!",  "20 Cedar Ave","058-567-8901");

        // ── 2. Login as each user ─────────────────────────────────────────────
        String adminToken = login("systemadmin@demo.com", "Admin123!");
        String aliceToken   = login("alice@demo.com",   "Alice123!");
        String bobToken     = login("bob@demo.com",     "Bob1234!");
        String charlieToken = login("charlie@demo.com", "Charlie1!");
        String daveToken    = login("dave@demo.com",    "Dave123!");
        String eveToken     = login("eve@demo.com",     "Eve1234!");

        // ── 3. Create companies ───────────────────────────────────────────────

        // Alice owns companies 1–4
        createCompany(aliceToken,   1, "SoundWave Events",    "soundwave@events.com",  "050-111-0001", "IL-1234-001");
        createCompany(aliceToken,   2, "Stadium Live",        "info@stadiumlive.com",  "050-111-0002", "IL-1234-002");
        createCompany(aliceToken,   3, "Festival Nation",     "hello@festnation.com",  "050-111-0003", "IL-1234-003");
        createCompany(aliceToken,   4, "Comedy Club Pro",     "comedy@clubpro.com",    "050-111-0004", "IL-1234-004");

        // Bob owns companies 5–7
        createCompany(bobToken,     5, "Arena Productions",   "arena@prod.com",        "052-222-0001", "IL-5678-001");
        createCompany(bobToken,     6, "NightOwl Concerts",   "night@owlconcerts.com", "052-222-0002", "IL-5678-002");
        createCompany(bobToken,     7, "Sport Events IL",     "info@sportevents.il",   "052-222-0003", "IL-5678-003");

        // Charlie owns companies 8–10
        createCompany(charlieToken, 8, "Tech Conferences",    "tech@conferences.il",   "054-333-0001", "IL-9012-001");
        createCompany(charlieToken, 9, "Theater Arts Group",  "arts@theatergroup.com", "054-333-0002", "IL-9012-002");
        createCompany(charlieToken,10, "Open Air Fest",       "open@airfest.com",      "054-333-0003", "IL-9012-003");

        // ── 4. Appoint dave as Owner of company 1, eve as Manager ─────────────
        int daveId = userService.getUserId(daveToken).getValue();
        int eveId  = userService.getUserId(eveToken).getValue();

        appointOwner(aliceToken, daveToken, 1, daveId);
        appointManager(aliceToken, eveToken, 1, eveId,
                Set.of(PermissionType.MANAGE_EVENTS_INVENTORY,
                       PermissionType.VIEW_ORDERS_HISTORY,
                       PermissionType.CREATE_EVENT));

        // ── 5. Create events (2 per company, each activated via map) ──────────
        LocalDateTime base = LocalDateTime.now();

        // Company 1 — SoundWave Events
        createEvent(aliceToken, 1, "Rock Under the Stars",   base.plusMonths(1),  CategoryEvent.LIVEMUSIC,  GeographicalArea.CENTER);
        createEvent(aliceToken, 1, "Electronic Night Vol.3", base.plusMonths(2),  CategoryEvent.FESTIVAL,   GeographicalArea.CENTER);
        createScheduledLotteryDemoEvent(aliceToken, base);

        // Company 2 — Stadium Live
        createEvent(aliceToken, 2, "Champions Finals", base.plusDays(20), CategoryEvent.SPORTS, GeographicalArea.JERUSALEM, true, true, List.of(userService.getUserId(aliceToken).getValue(), userService.getUserId(bobToken).getValue()));
        createEvent(aliceToken, 2, "All-Star Weekend",       base.plusMonths(3),  CategoryEvent.SPORTS,     GeographicalArea.JERUSALEM);

        // Company 3 — Festival Nation
        createEvent(aliceToken, 3, "Summer Groove Festival", base.plusMonths(2),  CategoryEvent.FESTIVAL,   GeographicalArea.NORTH);
        createEvent(aliceToken, 3, "Jazz in the Park",       base.plusMonths(4),  CategoryEvent.LIVEMUSIC,  GeographicalArea.NORTH);

        // Company 4 — Comedy Club Pro
        createEvent(aliceToken, 4, "Stand-Up Gala Night",    base.plusDays(30),   CategoryEvent.OTHER,      GeographicalArea.CENTER);
        createEvent(aliceToken, 4, "Improv Open Mic",        base.plusDays(45),   CategoryEvent.OTHER,      GeographicalArea.CENTER);

        // Company 5 — Arena Productions
        createEvent(bobToken,   5, "Pop Mega Concert",       base.plusMonths(1),  CategoryEvent.LIVEMUSIC,  GeographicalArea.CENTER);
        createEvent(bobToken,   5, "DJ Battle Arena",        base.plusMonths(3),  CategoryEvent.FESTIVAL,   GeographicalArea.CENTER);

        // Company 6 — NightOwl Concerts
        createEvent(bobToken,   6, "Midnight Jazz Sessions", base.plusDays(25),   CategoryEvent.LIVEMUSIC,  GeographicalArea.NORTH);
        createEvent(bobToken,   6, "Blues & Soul Night",     base.plusMonths(2),  CategoryEvent.LIVEMUSIC,  GeographicalArea.NORTH);

        // Company 7 — Sport Events IL
        createEvent(bobToken,   7, "National Marathon 2026", base.plusMonths(2),  CategoryEvent.SPORTS,     GeographicalArea.SOUTH);
        createEvent(bobToken,   7, "Basketball Playoffs",    base.plusDays(40),   CategoryEvent.SPORTS,     GeographicalArea.SOUTH);

        // Company 8 — Tech Conferences
        createEvent(charlieToken, 8, "DevCon Israel 2026",   base.plusMonths(3),  CategoryEvent.CONFERENCE, GeographicalArea.CENTER);
        createEvent(charlieToken, 8, "AI & Data Summit",     base.plusMonths(5),  CategoryEvent.CONFERENCE, GeographicalArea.CENTER);

        // Company 9 — Theater Arts Group
        createEvent(charlieToken, 9, "Hamlet — Hebrew Premiere", base.plusDays(35), CategoryEvent.THEATER, GeographicalArea.JERUSALEM);
        createEvent(charlieToken, 9, "The Lion King Musical",    base.plusMonths(2), CategoryEvent.THEATER, GeographicalArea.JERUSALEM);

        // Company 10 — Open Air Fest
        createEvent(charlieToken,10, "Sunset Open Air",      base.plusMonths(1),  CategoryEvent.FESTIVAL,   GeographicalArea.SOUTH);
        createEvent(charlieToken,10, "Food & Music Weekend", base.plusMonths(3),  CategoryEvent.FESTIVAL,   GeographicalArea.SOUTH);

        log.info("=== DataSeeder: done — 5 users, 10 companies, 20 events ===");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String guestToken() {
        var r = userService.continueAsGuest();
        if (r.getValue() == null) {
            log.severe("DataSeeder: could not get guest token — " + r.getMessage());
            return "";
        }
        return r.getValue();
    }

    private void registerUser(String guestToken, String email, String firstName,
                              String lastName, String password, String address, String phone) {
        UserDTO dto = new UserDTO(email, firstName, lastName, password,
                1, 1, 1995, address, phone);
        var r = userService.registerUser(guestToken, dto);
        if (r.getValue() == null || !r.getValue()) {
            log.warning("DataSeeder: register failed for " + email + " — " + r.getMessage());
        } else {
            log.info("DataSeeder: registered " + email);
        }
    }

    private String login(String email, String password) {
        var r = userService.login(email, password);
        if (r.getValue() == null) {
            log.severe("DataSeeder: login failed for " + email + " — " + r.getMessage());
            return "";
        }
        log.info("DataSeeder: logged in as " + email);
        return r.getValue();
    }

    private void createCompany(String token, int companyId, String name,
                               String email, String phone, String bankAccount) {
        var r = companyService.createProductionCompany(token, companyId, name, email, phone, bankAccount);
        if (r.getValue() == null) {
            log.warning("DataSeeder: company creation failed [" + name + "] — " + r.getMessage());
        } else {
            log.info("DataSeeder: created company [" + name + "]");
        }
    }

    private void appointOwner(String ownerToken, String appointeeToken,
                              int companyId, int appointeeId) {
        var req = companyService.requestAppointOwner(ownerToken, companyId, appointeeId);
        if (req.getValue() == null || !req.getValue()) {
            log.warning("DataSeeder: appoint owner request failed — " + req.getMessage());
            return;
        }
        var res = companyService.respondToOwnerAppointment(appointeeToken, companyId, true);
        if (res.getValue() == null || !res.getValue()) {
            log.warning("DataSeeder: appoint owner response failed — " + res.getMessage());
        } else {
            log.info("DataSeeder: appointed user " + appointeeId + " as Owner of company " + companyId);
        }
    }

    private void appointManager(String ownerToken, String appointeeToken,
                                int companyId, int appointeeId,
                                Set<PermissionType> permissions) {
        var req = companyService.requestAppointManager(ownerToken, companyId, appointeeId, permissions);
        if (req.getValue() == null || !req.getValue()) {
            log.warning("DataSeeder: appoint manager request failed — " + req.getMessage());
            return;
        }
        var res = companyService.respondToManagerAppointment(appointeeToken, companyId, true);
        if (res.getValue() == null || !res.getValue()) {
            log.warning("DataSeeder: appoint manager response failed — " + res.getMessage());
        } else {
            log.info("DataSeeder: appointed user " + appointeeId + " as Manager of company " + companyId);
        }
    }

    private void createEvent(String token, int companyId, String name, LocalDateTime date, CategoryEvent category, GeographicalArea area) {
        createEvent(token, companyId, name, date, category, area, false, false, List.of());
    }

    private void createEvent(String token,
                             int companyId,
                             String name,
                             LocalDateTime date,
                             CategoryEvent category,
                             GeographicalArea area,
                             boolean hasLottery,
                             boolean forceSaleStarted,
                             List<Integer> demoLotteryWinnerUserIds) {

        LocalDateTime saleStart = forceSaleStarted
                ? LocalDateTime.now().minusMinutes(55)
                : getDemoSaleStart(date);

        var r = eventService.createEvent(
                token,
                companyId,
                date,
                name,
                saleStart,
                hasLottery,
                area,
                category
        );

        if (r.getValue() == null) {
            log.warning("DataSeeder: event creation failed [" + name + "] — " + r.getMessage());
            return;
        }
        int eventId = r.getValue();
        log.info("DataSeeder: created event [" + name + "] id=" + eventId);
        if ("Rock Under the Stars".equals(name)) {
            addDemoCoupon(token, eventId, "ROCK50");
        }
        activateEvent(token, eventId, name);

        if (hasLottery) {
            seedDemoLottery(eventId, name, demoLotteryWinnerUserIds);
        }
    }

    private void createScheduledLotteryDemoEvent(
            String token,
            LocalDateTime base
    ) {

        LocalDateTime eventDate = base.plusWeeks(1);

        LocalDateTime registerWindow = base.plusMinutes(2);

        LocalDateTime saleStartDate = base.plusMinutes(3);

        var response = eventService.createEvent(
                token,
                1,
                eventDate,
                "LOTTERY",
                saleStartDate,
                true,
                GeographicalArea.CENTER,
                CategoryEvent.FESTIVAL
        );

        if (response.getValue() == null) {
            log.warning(
                    "DataSeeder: scheduled lottery demo event creation failed — "
                            + response.getMessage()
            );
            return;
        }

        int eventId = response.getValue();

        activateEvent(token, eventId, "LOTTERY");

        var lotteryResponse = lotteryService.createLottery(
                token,
                eventId,
                1,
                registerWindow,
                24
        );

        if (lotteryResponse.getValue() == null
                || !lotteryResponse.getValue()) {

            log.warning(
                    "DataSeeder: scheduled lottery creation failed for LOTTERY — "
                            + lotteryResponse.getMessage()
            );
            return;
        }

        log.info(
                "DataSeeder: created scheduled lottery demo event [LOTTERY] id="
                        + eventId
        );

        log.info(
                "DataSeeder: LOTTERY registration closes at "
                        + registerWindow
        );

        log.info(
                "DataSeeder: LOTTERY sales open at "
                        + saleStartDate
        );

        log.info(
                "DataSeeder: LOTTERY event date is "
                        + eventDate
        );
    }

    private void seedDemoLottery(int eventId,
                                 String eventName,
                                 List<Integer> winnerUserIds) {

        if (winnerUserIds == null || winnerUserIds.isEmpty()) {
            log.warning("DataSeeder: lottery requested for [" + eventName + "] but no demo winners supplied");
            return;
        }

        int capacity = winnerUserIds.size();

        LocalDateTime registerWindow = LocalDateTime.now().minusMinutes(1);

        long expirationTimeHours = 1;

        Lottery lottery = new Lottery(
                eventId,
                capacity,
                registerWindow,
                expirationTimeHours
        );

        for (Integer userId : winnerUserIds) {
            lottery.registerUserToLottery(userId);
        }

        Map<Integer, String> winners = lottery.drawWinners();

        lotteryRepo.store(lottery);

        Event event = eventRepo.findById(eventId);
        event.setActive(true);
        eventRepo.store(event);

        log.info("DataSeeder: seeded lottery for [" + eventName + "] eventId=" + eventId);
        log.info("DataSeeder: activated lottery event [" + eventName + "] eventId=" + eventId);
        for (Map.Entry<Integer, String> entry : winners.entrySet()) {
            log.info(
                    "DataSeeder: lottery winner for [" + eventName + "] userId="
                            + entry.getKey()
                            + " code="
                            + entry.getValue()
            );
        }
    }

    private LocalDateTime getDemoSaleStart(LocalDateTime eventDate) {
        seededEventCounter++;

        if (seededEventCounter % 3 == 0) {
            return LocalDateTime.now().plusDays(2);
        }

        return LocalDateTime.now().minusDays(1);
    }
    private void addDemoCoupon(String token, int eventId, String couponCode) {
        DiscountDTO coupon = new DiscountDTO(
                couponCode,
                50.0,
                LocalDate.now().plusMonths(1)
        );

        var r = eventService.addDiscountToEvent(token, eventId, coupon);

        if (r.getValue() == null || !r.getValue()) {
            log.warning("DataSeeder: coupon creation failed for event " + eventId + " — " + r.getMessage());
        } else {
            log.info("DataSeeder: added demo coupon [" + couponCode + "] to event " + eventId);
        }
    }

    /**
     * Creates a minimal seating map so the event becomes active.
     */
    private void activateEvent(String token, int eventId, String eventName) {

        // ── STAGE (top center) ─────────────────────────────
        ElementPositionDTO stage = new ElementPositionDTO(400, 20);

        // ── ENTRIES (left + right) ─────────────────────────
        List<ElementPositionDTO> entries = List.of(
                new ElementPositionDTO(50, 100),
                new ElementPositionDTO(750, 100)
        );

        // ── STANDING ZONE (bottom area) ───────────────────
        StandingZoneDTO standingZone = new StandingZoneDTO(
                40,
                "General Standing",
                80.0,
                new ElementPositionDTO(70, 450)
        );

        // ── VIP (close to stage center) ────────────────────
        SeatingZoneDTO vipZone = new SeatingZoneDTO(
                3,
                10,
                "VIP",
                300.0,
                new ElementPositionDTO(270, 150)
        );

        // ── REGULAR (middle area) ──────────────────────────
        SeatingZoneDTO regularZone = new SeatingZoneDTO(
                8,
                10,
                "Regular",
                150.0,
                new ElementPositionDTO(270, 400)
        );

        // ── BALCONY (back area) ────────────────────────────
        SeatingZoneDTO balconyZone = new SeatingZoneDTO(
                5,
                3,
                "Balcony",
                180.0,
                new ElementPositionDTO(700, 300)
        );

        var r = eventService.DefineVenueAndSeatingMap(
                token,
                eventId,
                stage,
                entries,
                List.of(standingZone),
                List.of(vipZone, regularZone, balconyZone)
        );

        if (r.getValue() == null || !r.getValue()) {
            log.warning("DataSeeder: map creation failed for [" + eventName + "] — " + r.getMessage());
        } else {
            log.info("DataSeeder: activated event with VISUAL MAP [" + eventName + "]");
        }
    }
}
