package UI;

import application.ActiveOrderService;
import application.AdminService;
import application.CompanyService;
import application.EventCompanyManageService;
import application.EventService;
import application.LotteryService;
import application.TokenService;
import application.UserService;
import infrastructure.AccessValidator;
import infrastructure.Auth;
import infrastructure.PasswordEncoderUtil;
import infrastructure.PaymentSystemProxy;
import infrastructure.TicketSupplyProxy;
import infrastructure.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = App.class)
public class SpringContextIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ActiveOrderService activeOrderService;

    @Autowired
    private EventService eventService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserService userService;

    @Autowired
    private LotteryService lotteryService;

    @Autowired
    private EventCompanyManageService eventCompanyManageService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Auth auth;

    @Autowired
    private AccessValidator accessValidator;

    @Autowired
    private PaymentSystemProxy paymentSystemProxy;

    @Autowired
    private TicketSupplyProxy ticketSupplyProxy;

    @Autowired
    private PasswordEncoderUtil passwordEncoderUtil;

    @Autowired
    private UserRepo userRepo;

    @Test
    void contextLoads() {
        assertNotNull(context);
    }

    @Test
    void criticalBeansAreWired() {
        assertNotNull(activeOrderService);
        assertNotNull(eventService);
        assertNotNull(companyService);
        assertNotNull(adminService);
        assertNotNull(userService);
        assertNotNull(lotteryService);
        assertNotNull(eventCompanyManageService);
        assertNotNull(tokenService);
        assertNotNull(auth);
        assertNotNull(accessValidator);
        assertNotNull(paymentSystemProxy);
        assertNotNull(ticketSupplyProxy);
        assertNotNull(passwordEncoderUtil);
        assertNotNull(userRepo);
    }

    @Test
    void activeOrderCapacityValueResolves() {
        assertEquals(20, activeOrderService.getCapacity());
    }
}
