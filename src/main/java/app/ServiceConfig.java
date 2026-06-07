package app;

import app.config.SystemProperties;
import domain.webQueue.WebQueue;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.logging.Logger;

@Configuration
public class ServiceConfig {

    private static final Logger logger = Logger.getLogger(ServiceConfig.class.getName());

    @Autowired
    private SystemProperties systemProperties;

    // ── Queue ─────────────────────────────────────────────────────────────────
    @PostConstruct
    public void initWebQueue() {
        int capacity = systemProperties.getMaxConcurrentUsers();
        WebQueue.getInstance(capacity);
        logger.info("WebQueue initialized with capacity: " + capacity);
    }
}
