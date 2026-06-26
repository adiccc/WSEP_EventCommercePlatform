package app;

import Log.LoggerSetup;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.spring.annotation.EnableVaadin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Logger;

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

        private static final Logger logger = Logger.getLogger(App.class.getName());

        public static void main(String[] args) {
                LoggerSetup.setup();
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
                try {
                        SpringApplication.run(App.class, args);
                } catch (Exception e) {
                        if (isDatabaseConnectionProblem(e)) {
                                logger.severe("Application failed to start: could not connect to the database, "
                                        + "so it cannot start. Make sure the database is running and reachable, and that "
                                        + "the connection settings (URL, host, port, username and password) are correct.");
                        } else {
                                logger.severe("Application failed to start: Spring Boot failed to load, "
                                        + "so the application cannot start.");
                        }
                        System.exit(1);
                }
        }

        /** Walks the cause chain to see if the startup failure was caused by a database connection error. */
        private static boolean isDatabaseConnectionProblem(Throwable t) {
                for (Throwable cause = t; cause != null; cause = cause.getCause()) {
                        if (cause instanceof SQLException) {
                                return true;
                        }
                        // When the DB is unreachable at boot, the SQLException is logged separately and the
                        // exception that actually propagates is a Hibernate "Unable to determine Dialect" error.
                        String message = cause.getMessage();
                        if (message != null && message.contains("Unable to determine Dialect")) {
                                return true;
                        }
                }
                return false;
        }
}