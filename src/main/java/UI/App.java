package UI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * scanBasePackages = "UI" tells Spring to scan:
 *   - UI.Views      → all @Route views
 *   - UI.Components → shared Vaadin components
 *   - UI            → ServiceConfig (@Configuration)
 *
 * The existing service/domain/infrastructure classes are plain Java
 * (no Spring annotations) and are wired manually in ServiceConfig,
 * so they don't need to be in the scan.
 *
 * Vaadin 24 auto-configures itself via spring-boot-starter — no @EnableVaadin needed.
 */
@SpringBootApplication(scanBasePackages = {"UI"})
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
