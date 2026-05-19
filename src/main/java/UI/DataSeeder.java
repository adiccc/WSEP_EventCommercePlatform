package UI;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import application.*;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.dto.UserDTO;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Runs once at startup and seeds the in-memory repos with demo data:
 *   - 3 registered users (each becomes founder of their companies)
 *   - 10 production companies
 *   - 20 events (2 per company, varied category / area)
 *
 * Remove or disable this class before connecting a real database.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = Logger.getLogger(DataSeeder.class.getName());

    private final UserService userService;
    private final CompanyService companyService;
    private final EventCompanyManageService eventService;

    public DataSeeder(UserService userService,
                      CompanyService companyService,
                      EventCompanyManageService eventService) {
        this.userService = userService;
        this.companyService = companyService;
        this.eventService = eventService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== DataSeeder: seeding demo data ===");

        // ── 1. Register users ─────────────────────────────────────────────────
        String guest = guestToken();

        registerUser(guest, "alice@demo.com",  "Alice",   "Cohen",   "Alice123!", "123 Main St",  "050-123-4567");
        registerUser(guest, "bob@demo.com",    "Bob",     "Levi",    "Bob1234!",  "456 Elm Ave",  "052-234-5678");
        registerUser(guest, "charlie@demo.com","Charlie", "Mizrahi", "Charlie1!", "789 Oak Rd",   "054-345-6789");

        // ── 2. Login as each user and create companies ─────────────────────────
        String aliceToken   = login("alice@demo.com",   "Alice123!");
        String bobToken     = login("bob@demo.com",     "Bob1234!");
        String charlieToken = login("charlie@demo.com", "Charlie1!");

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

        // ── 3. Create events (2 per company) ──────────────────────────────────
        LocalDateTime base = LocalDateTime.now();

        // Company 1 — SoundWave Events
        createEvent(aliceToken, 1, "Rock Under the Stars",   base.plusMonths(1),  CategoryEvent.LIVEMUSIC,  GeographicalArea.CENTER);
        createEvent(aliceToken, 1, "Electronic Night Vol.3", base.plusMonths(2),  CategoryEvent.FESTIVAL,   GeographicalArea.CENTER);

        // Company 2 — Stadium Live
        createEvent(aliceToken, 2, "Champions Finals",       base.plusDays(20),   CategoryEvent.SPORTS,     GeographicalArea.JERUSALEM);
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

        log.info("=== DataSeeder: done — 3 users, 10 companies, 20 events ===");
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

    private void createEvent(String token, int companyId, String name,
                             LocalDateTime date, CategoryEvent category, GeographicalArea area) {
        LocalDateTime saleStart = date.minusWeeks(3);
        var r = eventService.createEvent(token, companyId, date, name, saleStart, false, area, category);
        if (r.getValue() == null) {
            log.warning("DataSeeder: event creation failed [" + name + "] — " + r.getMessage());
            return;
        }
        int eventId = r.getValue();
        log.info("DataSeeder: created event [" + name + "] id=" + eventId);

        // Add a simple standing-zone map so the event becomes active
        activateEvent(token, eventId, name);
    }

    /**
     * Creates a minimal seating map for the event.
     * Without a map, events stay inactive and won't appear in search results.
     */
    private void activateEvent(String token, int eventId, String eventName) {

        // ── Basic layout elements ───────────────────────────────
        ElementPositionDTO stage = new ElementPositionDTO(0, 0);
        ElementPositionDTO entry = new ElementPositionDTO(1, 0);

        // ── Standing zone (high capacity) ───────────────────────
        StandingZoneDTO standingZone = new StandingZoneDTO(
                300,
                "General Standing",
                80.0,
                new ElementPositionDTO(0, 1)
        );

        // ── VIP seating zone (small, expensive) ────────────────
        SeatingZoneDTO vipZone = new SeatingZoneDTO(
                4,   // rows
                6,   // cols
                "VIP",
                300.0,
                new ElementPositionDTO(2, 2)
        );

        // ── Regular seating zone (bigger, cheaper) ─────────────
        SeatingZoneDTO regularZone = new SeatingZoneDTO(
                8,   // rows
                10,  // cols
                "Regular",
                150.0,
                new ElementPositionDTO(10, 2)
        );

        // ── Balcony zone (medium, mid price) ───────────────────
        SeatingZoneDTO balconyZone = new SeatingZoneDTO(
                5,
                8,
                "Balcony",
                180.0,
                new ElementPositionDTO(20, 2)
        );

        var r = eventService.DefineVenueAndSeatingMap(
                token,
                eventId,
                stage,
                List.of(entry),
                List.of(standingZone),
                List.of(vipZone, regularZone, balconyZone)
        );

        if (r.getValue() == null || !r.getValue()) {
            log.warning("DataSeeder: map creation failed for [" + eventName + "] — " + r.getMessage());
        } else {
            log.info("DataSeeder: activated event with FULL MAP [" + eventName + "]");
        }
    }
}
