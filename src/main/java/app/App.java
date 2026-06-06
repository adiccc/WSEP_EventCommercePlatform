package app;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "app",
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
