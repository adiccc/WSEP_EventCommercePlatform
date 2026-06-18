package app;

import app.config.SystemProperties;
import domain.activeOrder.ActiveOrder;
import domain.webQueue.WebQueue;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.logging.Logger;

@Configuration
public class ServiceConfig {

    private static final Logger logger = Logger.getLogger(ServiceConfig.class.getName());

    @Autowired
    private SystemProperties systemProperties;

    @Value("${active-order.selecting-timeout-minutes:5}")
    private int selectingTimeoutMinutes;

    @Value("${active-order.checkout-timeout-minutes:10}")
    private int checkoutTimeoutMinutes;

    @Value("${active-order.warning-before-expiry-minutes:1}")
    private int warningBeforeExpiryMinutes;

    @PostConstruct
    public void init() {
        int capacity = systemProperties.getMaxConcurrentUsers();
        WebQueue.getInstance(capacity);
        logger.info("WebQueue initialized with capacity: " + capacity);

        ActiveOrder.configure(selectingTimeoutMinutes, checkoutTimeoutMinutes, warningBeforeExpiryMinutes);
    }
}
