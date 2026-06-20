package app;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.spring.annotation.EnableVaadin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Arrays;

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
@EnableVaadin({"UI"})
@Push
public class App implements AppShellConfigurator {

        public static void main(String[] args) {
                boolean emptyDb = Arrays.stream(args).anyMatch("--db=empty"::equals);
                if (emptyDb) {
                        args = Arrays.copyOf(args, args.length + 1);
                        args[args.length - 1] = "--spring.jpa.hibernate.ddl-auto=create";
                }
                SpringApplication.run(App.class, args);
        }
}