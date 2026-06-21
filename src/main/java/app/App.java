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
                for (int i = 0; i < args.length; i++) {
                        if (args[i].startsWith("--config=")) {
                                String path = args[i].substring("--config=".length());
                                if (!path.startsWith("classpath:") && !path.startsWith("file:")) {
                                        path = "file:" + path;
                                }
                                args[i] = "--spring.config.additional-location=" + path;
                                break;
                        }
                }
                boolean emptyDb = Arrays.stream(args).anyMatch("--db=empty"::equals);
                if (emptyDb) {
                        args = Arrays.copyOf(args, args.length + 1);
                        args[args.length - 1] = "--spring.jpa.hibernate.ddl-auto=create";
                }
                SpringApplication.run(App.class, args);
        }
}