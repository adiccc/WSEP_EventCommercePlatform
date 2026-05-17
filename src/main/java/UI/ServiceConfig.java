package UI;

import application.*;
import domain.Suspension.ISuspensionRepo;
import domain.activeOrder.IActiveOrderRepo;
import domain.company.ICompanyRepo;
import domain.event.IEventRepo;
import domain.lottery.ILotteryRepo;
import domain.webQueue.WebQueue;
import domain.user.IUserRepo;
import infrastructure.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Constructs all plain-Java application services and exposes them as
 * Spring beans so Vaadin views can receive them via constructor injection.
 *
 * Add a new @Bean here whenever a new service is needed in the UI.
 */
@Configuration
public class ServiceConfig {

    // ── Repos ────────────────────────────────────────────────────────────────

    @Bean
    public WebQueue webQueue() {
        return WebQueue.getInstance(100);
    }

    @Bean
    public IUserRepo userRepo() {
        return new UserRepo();
    }

    @Bean
    public ICompanyRepo companyRepo() {
        return new CompanyRepoImpl();
    }

    @Bean
    public IEventRepo eventRepo() {
        return new EventRepoImpl();
    }

    @Bean
    public ILotteryRepo lotteryRepo() {
        return new LotteryRepoImpl();
    }

    @Bean
    public IActiveOrderRepo activeOrderRepo() {
        return new ActiveOrderRepoImpl();
    }

    // ── Infrastructure ───────────────────────────────────────────────────────

    @Bean
    public IPasswordEncoder passwordEncoder() {
        return new PasswordEncoderUtil();
    }

    @Bean
    public TokenService tokenService() {
        return new TokenService();
    }

    @Bean
    public IAuth auth() {
        return new Auth(tokenService(), userRepo(), passwordEncoder());
    }

    @Bean
    public ISuspensionRepo suspensionRepo() {
        return new SuspensionRepoImpl();
    }

    @Bean
    public IAccessValidator accessValidator() {
        return new AccessValidator(suspensionRepo());
    }

    @Bean
    public IPaymentSystem paymentSystem() {
        return new PaymentSystemProxy();
    }

    @Bean
    public ITicketSupply ticketSupply() {
        return new TicketSupplyProxy();
    }

    // ── Application Services ─────────────────────────────────────────────────

    @Bean
    public UserService userService() {
        return new UserService(tokenService(), auth(), userRepo(), passwordEncoder());
    }

    @Bean
    public EventService eventService() {
        return new EventService(auth(), eventRepo());
    }

    @Bean
    public CompanyService companyService() {
        return new CompanyService(auth() ,companyRepo(), userRepo(), accessValidator());
    }

    @Bean
    public ActiveOrderService activeOrderService() {
        return new ActiveOrderService(auth(), activeOrderRepo(), eventRepo(),companyRepo(), lotteryRepo(), paymentSystem(), ticketSupply(), accessValidator(),20);
    }

    @Bean
    public LotteryService lotteryService() {
        return new LotteryService(lotteryRepo(), eventRepo(), auth(), companyRepo(), accessValidator());
    }

    @Bean
    public AdminService adminService() {
        return new AdminService(auth() ,userRepo(), companyRepo(), eventRepo() ,paymentSystem(), suspensionRepo());
    }

    @Bean
    public EventCompanyManageService eventCompanyManageService() {
        return new EventCompanyManageService(companyRepo(), eventRepo() , auth(), paymentSystem(), accessValidator());
    }
}
