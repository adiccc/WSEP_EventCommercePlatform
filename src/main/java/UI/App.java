package UI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * scanBasePackages tells Spring to scan:
 *   - UI             → Vaadin views, presenters, layouts
 *   - application    → @Service classes (UserService, EventService, ...)
 *   - infrastructure → @Repository and @Component classes (UserRepo, Auth, PaymentSystemProxy, ...)
 *
 * Vaadin 24 auto-configures itself via spring-boot-starter — no @EnableVaadin needed.
 */
@SpringBootApplication(scanBasePackages = {"UI", "application", "infrastructure"})
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
