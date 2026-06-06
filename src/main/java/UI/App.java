package UI;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

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
@SpringBootApplication(scanBasePackages = {
        "UI",
        "application",
        "infrastructure",
        "domain"
})
@EnableJpaRepositories(basePackages = {
        "infrastructure.JPA"
})
@EntityScan(basePackages = {
        "domain"
})
@Push
public class App implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}