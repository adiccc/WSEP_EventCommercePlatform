package app;

import app.config.SystemProperties;
import application.WebQueue;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.logging.Logger;

@Configuration
public class ServiceConfig {

    private static final Logger logger = Logger.getLogger(ServiceConfig.class.getName());

    @Autowired
    private SystemProperties systemProperties;

    @PostConstruct
    public void init() {
        int capacity = systemProperties.getMaxConcurrentUsers();
        WebQueue.getInstance(capacity);
        logger.info("WebQueue initialized with capacity: " + capacity);
    }
}
